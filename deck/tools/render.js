// Render an HTML file to PNG with Playwright Chromium (node).
// Usage: node render.js in.html out.png W H [query]
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const [src, out, w, h, query] = process.argv.slice(2);
  const b = await chromium.launch();
  const pg = await b.newPage({ viewport: { width: +w, height: +h }, deviceScaleFactor: 1 });
  await pg.goto('file://' + path.resolve(src) + (query || ''));
  await pg.waitForTimeout(400);
  await pg.evaluate(() => document.fonts.ready.then(() => document.fonts.size));
  await pg.waitForTimeout(300);
  await pg.screenshot({ path: out });
  await b.close();
  console.log('wrote', out);
})();
