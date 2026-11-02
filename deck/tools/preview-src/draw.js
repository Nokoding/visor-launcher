// Shared drawing helpers for the 2.7 design preview. Same colors and panel numbers as the 2.6 preview (SYNTHWAVE theme).
const A='#FF2E97', B='#A855F7', TEXT='#F4EEFF', DIM='#A99DC6', PANEL='#120A21', BG0='#07040D', BG1='#170A2C';
const c = document.getElementById('c').getContext('2d');
function rgba(hex,a){const n=parseInt(hex.slice(1),16);return `rgba(${n>>16&255},${n>>8&255},${n&255},${a})`;}
function mix(a,b,t){const x=parseInt(a.slice(1),16),y=parseInt(b.slice(1),16);const m=s=>Math.round(((x>>s)&255)*(1-t)+((y>>s)&255)*t);return `rgb(${m(16)},${m(8)},${m(0)})`;}
function cut(l,t,r,b,big,small){const m=Math.min(r-l,b-t)*0.45;big=Math.min(big,m);small=Math.min(small,m);
  c.beginPath();c.moveTo(l+big,t);c.lineTo(r-small,t);c.lineTo(r,t+small);c.lineTo(r,b-big);c.lineTo(r-big,b);c.lineTo(l+small,b);c.lineTo(l,b-small);c.lineTo(l,t+big);c.closePath();}
let seed=11;function rnd(){seed=(seed*1103515245+12345)&0x7fffffff;return seed/0x7fffffff;}
function sky(){const g=c.createLinearGradient(0,0,0,1080);g.addColorStop(0,BG1);g.addColorStop(1,BG0);c.fillStyle=g;c.fillRect(0,0,1920,1080);}
function skyline(col,base,hmin,hrange,wmin,wrange,shift){seed=11+Math.round(base);c.fillStyle=col;for(let x=-shift%300-300;x<1920;){const w=wmin+rnd()*wrange,h=hmin+rnd()*hrange;c.fillRect(x,base-h,w-6,h+400);x+=w;}}
function fonts(draw){Promise.all(['700 29px Orbitron','600 31px Rajdhani','400 17px ShareTech','500 16px OrbitronM'].map(f=>document.fonts.load(f))).then(draw, draw);}
