// Visor Launcher website. No framework, no external requests. The 3D controller is three.js (vendored, MIT).
const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
document.documentElement.classList.add('js');

// ---- release info: the version and size on the download button
fetch('release.json').then(r => r.ok ? r.json() : null).then(j => {
  if (!j) return;
  const m = document.getElementById('dl-meta');
  if (m) m.textContent = 'v' + j.version + ' · about ' + (j.size_kb >= 1024 ? (j.size_kb / 1024).toFixed(1) + ' MB' : j.size_kb + ' KB');
  const a = document.getElementById('dl');
  if (a && j.url && j.url.startsWith('https://github.com/Nokoding/visor-launcher/')) a.href = j.url;
}).catch(() => {});

// ---- typed headings (full text stays in the page for screen readers)
const typeTargets = Array.from(document.querySelectorAll('[data-type]'));
typeTargets.forEach(el => {
  const full = el.textContent.trim();
  el.dataset.full = full;
  el.textContent = '';
  const sr = document.createElement('span'); sr.className = 'sr'; sr.textContent = full;
  const vis = document.createElement('span'); vis.setAttribute('aria-hidden', 'true');
  el.append(sr, vis);
  if (reduce) vis.textContent = full;
});
function typeIn(el) {
  if (el.dataset.done) return;
  el.dataset.done = '1';
  const vis = el.querySelector('[aria-hidden]'), full = el.dataset.full;
  if (reduce) { vis.textContent = full; return; }
  let i = 0; vis.classList.add('caret');
  const step = () => { i += 1; vis.textContent = full.slice(0, i); if (i < full.length) setTimeout(step, 28); else vis.classList.remove('caret'); };
  step();
}

// ---- lock-on frames and typing when things scroll into view
const io = new IntersectionObserver(es => es.forEach(e => {
  if (!e.isIntersecting) return;
  e.target.classList.add('in');
  e.target.querySelectorAll('[data-type]').forEach(typeIn);
  if (e.target.matches('[data-type]')) typeIn(e.target);
}), { threshold: 0.15 });
document.querySelectorAll('.frame').forEach(f => io.observe(f));
typeTargets.forEach(t => io.observe(t));
if (reduce) document.querySelectorAll('.frame').forEach(f => f.classList.add('in'));

// ---- the 3D controller
const canvas = document.getElementById('gl'), still = document.getElementById('still');
const noteEl = document.getElementById('glow-note');
let glowSet = new Set(), glowAll = false;

function showStill() { canvas.hidden = true; still.hidden = false; document.querySelectorAll('.toggle button').forEach(b => b.disabled = true); }

async function start() {
  let THREE; const models = {};
  try {
    const test = document.createElement('canvas');
    if (!(test.getContext('webgl2') || test.getContext('webgl'))) throw new Error('no webgl');
    THREE = await import('./vendor/three.module.min.js');
    models.dualsense = decode(await fetch('models/dualsense.vmd').then(r => r.arrayBuffer()));
  } catch (e) { showStill(); return; }

  let renderer;
  try { renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true, powerPreference: 'low-power' }); }
  catch (e) { showStill(); return; }
  renderer.setClearColor(0x000000, 0);
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(32, 1, 0.1, 100);
  camera.position.set(0, 24, 36); camera.lookAt(0, -0.5, 0);
  scene.add(new THREE.AmbientLight(0xffffff, 0.9));
  const key = new THREE.DirectionalLight(0xffffff, 2.2); key.position.set(-6, 12, 10); scene.add(key);
  const rim = new THREE.DirectionalLight(0xa855f7, 1.2); rim.position.set(8, 2, -8); scene.add(rim);

  const COL = { body: new THREE.Color(0x4a2a7a), deco: new THREE.Color(0xa24bc0), hw: new THREE.Color(0x5a3590), glow: new THREE.Color(0xff2e97), glowHw: new THREE.Color(0xff6fb8) };
  const group = new THREE.Group(); scene.add(group);
  const built = {};

  // The model files are VMD: "VMD1", vertex count, triangle count (little endian ints), the size of one unit in cm (float),
  // vertices as 16 bit integers, triangles as 16 bit indices, one button number per triangle (255 = none).
  function decode(buf) {
    const dv = new DataView(buf);
    if (dv.getUint32(0, true) !== 0x31444d56) throw new Error('not a model');
    const nv = dv.getUint32(4, true), nt = dv.getUint32(8, true), unit = dv.getFloat32(12, true);
    const pos = new Float32Array(nv * 3);
    for (let i = 0; i < nv * 3; i++) pos[i] = dv.getInt16(16 + i * 2, true) * unit;
    const idxAt = 16 + nv * 6, idx = new Uint16Array(nt * 3);
    for (let i = 0; i < nt * 3; i++) idx[i] = dv.getUint16(idxAt + i * 2, true);
    const btnAt = idxAt + nt * 6, btn = new Uint8Array(buf, btnAt, nt);
    return { pos, idx, btn, nt };
  }
  function build(name) {
    const m = models[name];
    const g0 = new THREE.BufferGeometry();
    g0.setAttribute('position', new THREE.BufferAttribute(m.pos, 3));
    g0.setIndex(new THREE.BufferAttribute(m.idx, 1));
    g0.computeVertexNormals();
    const geo = g0.toNonIndexed();            // one colour per triangle, so a lit button is a clean patch
    g0.dispose();
    geo.setAttribute('color', new THREE.Float32BufferAttribute(new Float32Array(m.nt * 9), 3));
    const mesh = new THREE.Mesh(geo, new THREE.MeshStandardMaterial({ vertexColors: true, roughness: 0.5, metalness: 0.2, side: THREE.DoubleSide }));
    const g = new THREE.Group(); g.add(mesh);
    return { g, geo, m };
  }
  // Each button's glow is a soft pool of light around the middle of its faces, so it follows the shape instead of showing a hard patch.
  function buttonSpots(b) {
    const m = b.m, ix = m.idx, p = m.pos, spots = {};
    const cen = new Float32Array(m.nt * 3);
    for (let f = 0; f < m.nt; f++) for (let k = 0; k < 3; k++) cen[f * 3 + k] = (p[ix[f * 3] * 3 + k] + p[ix[f * 3 + 1] * 3 + k] + p[ix[f * 3 + 2] * 3 + k]) / 3;
    const acc = {};
    for (let f = 0; f < m.nt; f++) {
      const id = m.btn[f]; if (id === 255) continue;
      const a = acc[id] || (acc[id] = { x: 0, y: 0, z: 0, n: 0 });
      a.x += cen[f * 3]; a.y += cen[f * 3 + 1]; a.z += cen[f * 3 + 2]; a.n++;
    }
    for (const id in acc) { const a = acc[id]; spots[id] = { x: a.x / a.n, y: a.y / a.n, z: a.z / a.n, r: 0.6 }; }
    for (let f = 0; f < m.nt; f++) {
      const id = m.btn[f]; if (id === 255) continue;
      const s = spots[id], d = Math.hypot(cen[f * 3] - s.x, cen[f * 3 + 1] - s.y, cen[f * 3 + 2] - s.z);
      if (d > s.r && d < 2.2) s.r = d;
    }
    b.cen = cen; b.spots = spots;
  }
  function paint(b) {
    if (!b.spots) buttonSpots(b);
    const colA = b.geo.getAttribute('color'), arr = colA.array, nt = b.m.nt, cen = b.cen;
    const base = COL.body, hot = COL.glow;
    const active = [];
    const want = id => glowAll || glowSet.has(id) || (id === 4 && glowSet.has(6)) || (id === 5 && glowSet.has(7));
    for (const id in b.spots) if (want(+id)) active.push(b.spots[id]);
    for (let f = 0; f < nt; f++) {
      let w = 0;
      for (let i = 0; i < active.length; i++) {
        const s = active[i], d = Math.hypot(cen[f * 3] - s.x, cen[f * 3 + 1] - s.y, cen[f * 3 + 2] - s.z), reach = s.r * 1.5 + 0.15;
        if (d < reach) { const t = 1 - d / reach; const v = t * t * (3 - 2 * t); if (v > w) w = v; }
      }
      const r = base.r + (hot.r - base.r) * w, g = base.g + (hot.g - base.g) * w, bl = base.b + (hot.b - base.b) * w, o = f * 9;
      arr[o] = r; arr[o + 1] = g; arr[o + 2] = bl; arr[o + 3] = r; arr[o + 4] = g; arr[o + 5] = bl; arr[o + 6] = r; arr[o + 7] = g; arr[o + 8] = bl;
    }
    colA.needsUpdate = true;
  }
  let current = null, which = 'dualsense';
  async function use(name) {
    if (!models[name]) {
      try { models[name] = decode(await fetch('models/' + (name === 'xbox' ? 'xbox-one' : name) + '.vmd').then(r => r.arrayBuffer())); } catch (e) { return; }
    }
    which = name;
    if (current) group.remove(current.g);
    built[name] = built[name] || build(name);
    current = built[name]; paint(current); group.add(current.g);
    document.getElementById('t-ds').setAttribute('aria-pressed', String(name === 'dualsense'));
    document.getElementById('t-xb').setAttribute('aria-pressed', String(name === 'xbox'));
    draw();
  }
  document.getElementById('t-ds').addEventListener('click', () => use('dualsense'));
  document.getElementById('t-xb').addEventListener('click', () => use('xbox'));

  // which feature is on screen decides which buttons glow
  const fo = new IntersectionObserver(es => {
    es.forEach(e => {
      if (!e.isIntersecting) return;
      const d = e.target.dataset.glow || '';
      glowAll = d === 'all';
      glowSet = new Set(glowAll ? [] : d.split(',').filter(Boolean).map(Number));
      if (noteEl) noteEl.textContent = e.target.dataset.note || '';
      if (current) { paint(current); draw(); }
    });
  }, { rootMargin: '-35% 0px -35% 0px' });
  document.querySelectorAll('.feature').forEach(f => fo.observe(f));

  function size() {
    const w = canvas.clientWidth || 300, h = canvas.clientHeight || 200;
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5));
    renderer.setSize(w, h, false);
    camera.aspect = w / h; camera.updateProjectionMatrix();
  }
  let visible = true, ticking = false;
  function pose() {
    const max = Math.max(1, document.documentElement.scrollHeight - innerHeight);
    const p = reduce ? 0 : window.scrollY / max;
    group.rotation.y = -0.45 + p * Math.PI * 4;          // two turns over the whole page
    group.rotation.x = reduce ? 0.62 : 0.62 + Math.sin(p * Math.PI * 6) * 0.1;
  }
  function draw() { if (!visible) return; pose(); renderer.render(scene, camera); }
  function onScroll() { if (!ticking) { ticking = true; requestAnimationFrame(() => { ticking = false; draw(); }); } }
  // render only on scroll, resize or a change, and not at all while the canvas is off screen
  new IntersectionObserver(es => { visible = es[0].isIntersecting; if (visible) draw(); }).observe(canvas);
  addEventListener('scroll', onScroll, { passive: true });
  addEventListener('resize', () => { size(); draw(); });
  canvas.addEventListener('webglcontextlost', e => { e.preventDefault(); showStill(); });
  size(); use('dualsense');
  window.__visorReady = true;
}
start();
