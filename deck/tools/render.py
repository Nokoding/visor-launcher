"""Render an HTML file to PNG with Playwright Chromium. Usage: render.py in.html out.png W H [query]"""
import sys
from pathlib import Path
from playwright.sync_api import sync_playwright

src, out, w, h = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
query = sys.argv[5] if len(sys.argv) > 5 else ""
with sync_playwright() as p:
    b = p.chromium.launch()
    pg = b.new_page(viewport={"width": w, "height": h}, device_scale_factor=1)
    pg.goto(Path(src).resolve().as_uri() + query)
    pg.wait_for_timeout(300)
    pg.evaluate("document.fonts.ready.then(() => document.fonts.size)")  # bundled TTFs (2.1 mocks)
    pg.wait_for_timeout(200)
    pg.screenshot(path=out, full_page=False, omit_background=False)
    b.close()
print("wrote", out)
