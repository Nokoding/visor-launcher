#!/usr/bin/env python3
"""Writes the Visor Launcher logo files into docs/brand: the mark (visor slit with lock-on brackets) and the
wordmark (mark + VISOR + LAUNCHER, text turned into paths with fontTools so no font is needed to show it).
Original artwork. Usage: python3 deck/tools/make-logo.py   (needs: pip install fonttools)"""
import os
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

HERE = os.path.dirname(os.path.abspath(__file__))
FONTS = os.path.join(HERE, '..', 'assets', 'fonts')
OUT = os.path.join(HERE, '..', '..', 'docs', 'brand')
A, B, TEXT, BG = '#FF2E97', '#A855F7', '#F4EEFF', '#07040D'

MARK_BODY = '''
  <defs>
    <linearGradient id="g" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="%(A)s"/><stop offset="1" stop-color="%(B)s"/></linearGradient>
  </defs>
  <g stroke="%(A)s" stroke-width="14" stroke-linecap="square" fill="none">
    <path d="M56 136V56H136"/><path d="M456 136V56H376"/><path d="M56 376V456H136"/><path d="M456 376V456H376"/>
  </g>
  <g stroke="%(B)s" stroke-width="8" stroke-linecap="square" fill="none">
    <path d="M256 56V84"/><path d="M256 456V428"/><path d="M56 256H84"/><path d="M456 256H428"/>
  </g>
  <path d="M88 236L146 188H366L424 236L404 306L340 340H276L256 318L236 340H172L108 306Z" fill="url(#g)"/>
  <path d="M128 240L168 214H344L384 240L372 280L330 300H290L256 282L222 300H182L140 280Z" fill="%(BG)s"/>
  <path d="M152 252H360" stroke="%(TEXT)s" stroke-width="6" stroke-linecap="round" opacity="0.85"/>
''' % dict(A=A, B=B, TEXT=TEXT, BG=BG)

def mark_svg(bg=True):
    rect = '<rect width="512" height="512" rx="72" fill="%s"/>' % BG if bg else ''
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512" role="img" aria-label="Visor Launcher logo">'
            '<title>Visor Launcher</title>' + rect + MARK_BODY + '</svg>\n')

def text_path(font_file, text, size, spacing, x, y, fill):
    f = TTFont(os.path.join(FONTS, font_file))
    gs = f.getGlyphSet(); cmap = f.getBestCmap(); upm = f['head'].unitsPerEm
    sc = size / upm
    parts = []; cx = x
    for ch in text:
        g = cmap.get(ord(ch))
        if g is None:
            cx += size * 0.5; continue
        pen = SVGPathPen(gs)
        gs[g].draw(TransformPen(pen, (sc, 0, 0, -sc, cx, y)))
        d = pen.getCommands()
        if d: parts.append('<path d="%s"/>' % d)
        cx += gs[g].width * sc + spacing
    return '<g fill="%s">%s</g>' % (fill, ''.join(parts)), cx

def wordmark_svg():
    vis, x1 = text_path('orbitron-bold.ttf', 'VISOR', 150, 10, 560, 250, TEXT)
    lau, x2 = text_path('rajdhani-semibold-latin.ttf', 'LAUNCHER', 64, 26, 566, 330, A)
    w = int(max(x1, x2) + 80)
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d 512" width="%d" height="512" role="img" aria-label="Visor Launcher">'
            '<title>Visor Launcher</title><rect width="%d" height="512" rx="72" fill="%s"/>'
            '<g transform="translate(0,0)">%s</g>%s%s</svg>\n') % (w, w, w, BG, MARK_BODY, vis, lau)

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    open(os.path.join(OUT, 'visor-mark.svg'), 'w').write(mark_svg(True))
    open(os.path.join(OUT, 'visor-mark-transparent.svg'), 'w').write(mark_svg(False))
    open(os.path.join(OUT, 'visor-wordmark.svg'), 'w').write(wordmark_svg())
    print('wrote logo files in', OUT)
