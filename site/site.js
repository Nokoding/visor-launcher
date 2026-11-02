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
    models.dualsense = await fetch('models-dualsense.json').then(r => r.json());
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

  function build(name) {
    const m = models[name];
    const pos = [], col = [], nrm = [], faceOf = [];
    const V = i => new THREE.Vector3(m.v[i * 3], m.v[i * 3 + 1], m.v[i * 3 + 2]);
    for (let fi = 0; fi < m.t.length / 3; fi++) {
      const a = V(m.t[fi * 3]), b = V(m.t[fi * 3 + 1]), c = V(m.t[fi * 3 + 2]);
      const n = new THREE.Vector3().subVectors(b, a).cross(new THREE.Vector3().subVectors(c, a)).normalize();
      [a, b, c].forEach(p => { pos.push(p.x, p.y, p.z); nrm.push(n.x, n.y, n.z); col.push(0, 0, 0); faceOf.push(fi); });
    }
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    geo.setAttribute('normal', new THREE.Float32BufferAttribute(nrm, 3));
    geo.setAttribute('color', new THREE.Float32BufferAttribute(col, 3));
    const mesh = new THREE.Mesh(geo, new THREE.MeshStandardMaterial({ vertexColors: true, roughness: 0.55, metalness: 0.15, flatShading: true, side: THREE.DoubleSide }));
    const lines = new THREE.LineSegments(new THREE.EdgesGeometry(geo, 35), new THREE.LineBasicMaterial({ color: 0xff2e97, transparent: true, opacity: 0.28 }));
    const g = new THREE.Group(); g.add(mesh, lines);
    return { g, geo, faceOf, m };
  }
  function paint(b) {
    const colA = b.geo.getAttribute('color'), n = colA.count;
    for (let i = 0; i < n; i++) {
      const btn = b.m.b[b.faceOf[i]];
      let lit = false;
      if (btn >= 0) {
        // the front corner faces are bumper and trigger together (4,5 and 6,7)
        const want = glowAll || glowSet.has(btn) || ((btn === 4 && glowSet.has(6)) || (btn === 5 && glowSet.has(7)));
        lit = want;
      }
      const c = lit ? COL.glow : COL.body;
      colA.setXYZ(i, c.r, c.g, c.b);
    }
    colA.needsUpdate = true;
  }
  let current = null, which = 'dualsense';
  async function use(name) {
    if (!models[name]) {
      try { models[name] = await fetch('models-' + name + '.json').then(r => r.json()); } catch (e) { return; }
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
