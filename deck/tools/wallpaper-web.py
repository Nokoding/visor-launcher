#!/usr/bin/env python3
"""Local upload page for Visor wallpapers. Run it through wallpaper-web.sh.

Listens on 127.0.0.1 only. The page sends one file at a time with a PUT (no multipart). The file name is
cleaned and checked here; a path from the page is never used. Limits match Visor: .mp4 40 MB, .zip 49 MB,
.gif .png .jpg .jpeg .webp 8 MB. A single file is wrapped into a small pack with a wallpaper.json, a zip
is pushed as it is, then Visor is told to reload (deck_cmd reload_wallpapers).
"""
import http.server
import json
import os
import re
import secrets
import shutil
import subprocess
import sys
import tempfile

PKG = "com.nokoding.deck"
DEST = "/sdcard/Android/data/%s/files/wallpapers" % PKG
MB = 1024 * 1024
LIMITS = {".mp4": 40 * MB, ".zip": 49 * MB, ".gif": 8 * MB, ".png": 8 * MB, ".jpg": 8 * MB, ".jpeg": 8 * MB, ".webp": 8 * MB}
NAME_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._ -]{0,59}$")

TARGET = sys.argv[1]
PORT = int(sys.argv[2])
TOKEN = secrets.token_urlsafe(12)
BUSY = False

PAGE = """<!doctype html><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
<title>Visor wallpaper</title>
<style>body{font:18px sans-serif;margin:16px;background:#101018;color:#eee}button,input{font-size:18px;margin:8px 0;display:block}
progress{width:100%%}#msg{white-space:pre-wrap;margin-top:12px}small{color:#aaa}</style>
<h2>Send a wallpaper to the TV</h2>
<p><small>.mp4 up to 40 MB (1280x720, 30 fps, 6 Mbit/s), .gif .png .jpg .webp up to 8 MB, or a pack .zip.<br>
The TV is %(target)s.</small></p>
<input type=file id=f accept=".mp4,.zip,.gif,.png,.jpg,.jpeg,.webp">
<button id=b>SEND</button><progress id=p value=0 max=1 hidden></progress><div id=msg></div>
<script>
var T=%(token)s;
document.getElementById('b').onclick=function(){
  var f=document.getElementById('f').files[0], m=document.getElementById('msg'), p=document.getElementById('p');
  if(!f){m.textContent='Pick a file first.';return;}
  var x=new XMLHttpRequest();
  x.open('PUT','/upload?t='+encodeURIComponent(T)+'&name='+encodeURIComponent(f.name));
  x.upload.onprogress=function(e){if(e.lengthComputable){p.hidden=false;p.value=e.loaded/e.total;}};
  x.onload=function(){m.textContent=x.responseText;};
  x.onerror=function(){m.textContent='The page lost the connection to the script.';};
  m.textContent='Sending...';
  x.send(f);
};
</script>"""


def adb(*args, timeout=600):
    return subprocess.run(["adb", "-s", TARGET] + list(args), capture_output=True, text=True, timeout=timeout)


def clean_name(raw):
    """The base name only, never a path. Returns (name, error)."""
    base = os.path.basename((raw or "").replace("\\", "/"))
    if not NAME_RE.match(base):
        return None, "The file name must be letters, digits, space, dot, dash or underscore (up to 60)."
    ext = os.path.splitext(base)[1].lower()
    if ext not in LIMITS:
        return None, "Only .mp4 .zip .gif .png .jpg .jpeg .webp files are accepted."
    return base, None


def pack_json(base, ext):
    stem = os.path.splitext(base)[0][:40]
    kind = {".mp4": "video", ".gif": "animated"}.get(ext, "image")
    return json.dumps({"format": 1, "name": stem, "type": kind, "file": base})


def push(path, base):
    """Pushes the file (zip as it is, anything else inside a small pack folder) and asks Visor to reload."""
    ext = os.path.splitext(base)[1].lower()
    r = adb("connect", TARGET)
    if adb("get-state").returncode != 0:
        return "The TV is not reachable. Check Wireless debugging is on and the port is current."
    if adb("shell", "pm", "path", PKG).returncode != 0:
        return "Visor is not installed on the TV."
    adb("shell", "mkdir", "-p", DEST)
    if ext == ".zip":
        res = adb("push", path, DEST + "/" + base)
    else:
        stem = os.path.splitext(base)[0]
        tmp = tempfile.mkdtemp(prefix="deckwp")
        try:
            pack = os.path.join(tmp, stem)
            os.mkdir(pack)
            shutil.copy(path, os.path.join(pack, base))
            with open(os.path.join(pack, "wallpaper.json"), "w") as j:
                j.write(pack_json(base, ext))
            res = adb("push", pack, DEST + "/")
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    if res.returncode != 0:
        return "adb push failed: " + (res.stderr or res.stdout).strip()[:300]
    adb("shell", "am", "start", "-n", PKG + "/.HomeActivity", "--es", "deck_cmd", "reload_wallpapers")
    return None


class Handler(http.server.BaseHTTPRequestHandler):
    server_version = "DeckWallpaper"

    def log_message(self, fmt, *args):
        sys.stderr.write("  " + (fmt % args) + "\n")

    def _send(self, code, body, ctype="text/plain; charset=utf-8"):
        data = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def _ok_client(self):
        return self.client_address[0] in ("127.0.0.1", "::1")

    def _query(self):
        out = {}
        if "?" in self.path:
            from urllib.parse import parse_qs
            for k, v in parse_qs(self.path.split("?", 1)[1]).items():
                out[k] = v[0]
        return out

    def do_GET(self):
        if not self._ok_client():
            return self._send(403, "Only this phone may use this page.")
        if self.path.split("?")[0] != "/" or self._query().get("t") != TOKEN:
            return self._send(404, "Open the address the script printed, with the ?t= part.")
        self._send(200, PAGE % {"target": TARGET, "token": json.dumps(TOKEN)}, "text/html; charset=utf-8")

    def do_PUT(self):
        global BUSY
        if not self._ok_client():
            return self._send(403, "Only this phone may use this page.")
        q = self._query()
        if self.path.split("?")[0] != "/upload" or q.get("t") != TOKEN:
            return self._send(403, "Wrong or missing token.")
        base, err = clean_name(q.get("name"))
        if err:
            return self._send(400, err)
        ext = os.path.splitext(base)[1].lower()
        try:
            n = int(self.headers.get("Content-Length", ""))
        except ValueError:
            return self._send(411, "The page did not say how big the file is.")
        if n <= 0:
            return self._send(400, "The file is empty.")
        if n > LIMITS[ext]:
            return self._send(413, "%s is over %d MB, Visor would refuse it." % (base, LIMITS[ext] // MB))
        if BUSY:
            return self._send(409, "Another file is being sent. Wait for it.")
        BUSY = True
        tmp = tempfile.mkdtemp(prefix="deckup")
        try:
            path = os.path.join(tmp, base)
            left = n
            with open(path, "wb") as f:
                while left > 0:
                    chunk = self.rfile.read(min(65536, left))
                    if not chunk:
                        return self._send(400, "The upload stopped half way.")
                    f.write(chunk)
                    left -= len(chunk)
            why = push(path, base)
            if why:
                return self._send(502, why)
            msg = "Sent %s. Visor reloaded its wallpapers." % base
            if ext == ".mp4":
                msg += "\nA video wallpaper is experimental. Pick it in Setup > LOOK > WALLPAPER. WALLPAPER STATUS says if Visor refused it."
            else:
                msg += "\nPick it in Setup > LOOK > WALLPAPER."
            self._send(200, msg)
        except Exception as e:  # never leave the page hanging
            self._send(500, "Something failed: %s" % e)
        finally:
            BUSY = False
            shutil.rmtree(tmp, ignore_errors=True)


def main():
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print("Open this in your phone's browser:")
    print("  http://127.0.0.1:%d/?t=%s" % (PORT, TOKEN))
    print("Sending to the TV at %s. Press Ctrl+C here to stop." % TARGET)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        print("\nstopped")


if __name__ == "__main__":
    main()
