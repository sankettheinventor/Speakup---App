// Brutal SEO audit over the built dist/. Reports real defects, no sugar-coating.
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const dist = fileURLToPath(new URL('../dist/', import.meta.url));
const htmls = [];
(function walk(d) {
  for (const e of readdirSync(d)) {
    const p = join(d, e);
    if (statSync(p).isDirectory()) walk(p);
    else if (e.endsWith('.html')) htmls.push(p);
  }
})(dist);

const get = (re, s) => { const m = s.match(re); return m ? m[1].trim() : null; };
const rows = [];
for (const p of htmls) {
  const s = readFileSync(p, 'utf8');
  const url = p.replace(dist, '/').replace(/\\/g, '/').replace(/index\.html$/, '').replace(/\.html$/, '');
  const title = get(/<title>([^<]*)<\/title>/, s);
  const desc = get(/<meta name="description" content="([^"]*)"/, s);
  const h1s = (s.match(/<h1[\s>]/g) || []).length;
  const h2s = (s.match(/<h2[\s>]/g) || []).length;
  const canonical = /rel="canonical"/.test(s);
  const imgs = s.match(/<img\b[^>]*>/g) || [];
  const imgNoAlt = imgs.filter(i => !/\balt=/.test(i)).length;
  // visible text word count (strip scripts/styles/tags)
  const text = s.replace(/<script[\s\S]*?<\/script>/g, ' ').replace(/<style[\s\S]*?<\/style>/g, ' ').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
  const words = text ? text.split(' ').length : 0;
  const faq = /"@type":"FAQPage"/.test(s) || /"@type":"FAQ/.test(s);
  const howto = /"@type":"HowTo"/.test(s);
  rows.push({ url, title, titleLen: title?.length || 0, desc, descLen: desc?.length || 0, h1s, h2s, canonical, imgNoAlt, words, faq, howto });
}

const dup = (key) => {
  const m = {};
  for (const r of rows) { const v = r[key]; if (!v) continue; (m[v] ||= []).push(r.url); }
  return Object.entries(m).filter(([, u]) => u.length > 1);
};

console.log(`Pages scanned: ${rows.length}\n`);
console.log('=== 🔴 Missing meta description ===');
console.log(rows.filter(r => !r.desc).map(r => r.url).join('\n') || 'none');
console.log('\n=== 🔴 H1 not exactly 1 ===');
console.log(rows.filter(r => r.h1s !== 1).map(r => `${r.url}  (h1=${r.h1s})`).join('\n') || 'none');
console.log('\n=== 🔴 Duplicate <title> ===');
console.log(dup('title').map(([t, u]) => `"${t}"\n   ${u.join('\n   ')}`).join('\n') || 'none');
console.log('\n=== 🟡 Duplicate meta description ===');
console.log(dup('desc').map(([, u]) => `   ${u.join('\n   ')}`).join('\n---\n') || 'none');
console.log('\n=== 🟡 Title length out of 30-60 ===');
console.log(rows.filter(r => r.titleLen < 30 || r.titleLen > 60).map(r => `${r.url}  (${r.titleLen})`).join('\n') || 'none');
console.log('\n=== 🟡 Description length out of 70-160 ===');
console.log(rows.filter(r => r.descLen < 70 || r.descLen > 160).map(r => `${r.url}  (${r.descLen})`).join('\n') || 'none');
console.log('\n=== 🟡 Images missing alt ===');
console.log(rows.filter(r => r.imgNoAlt > 0).map(r => `${r.url}  (${r.imgNoAlt})`).join('\n') || 'none');
console.log('\n=== 🟡 Thin content (<300 visible words) ===');
console.log(rows.filter(r => r.words < 300).map(r => `${r.url}  (${r.words}w)`).join('\n') || 'none');
console.log('\n=== Missing canonical ===');
console.log(rows.filter(r => !r.canonical).map(r => r.url).join('\n') || 'none');
console.log('\n=== FAQ schema present on: ===');
console.log(rows.filter(r => r.faq).map(r => r.url).join('\n') || 'NONE — no page has FAQ schema');
console.log('\n=== HowTo schema present on: ===');
console.log(rows.filter(r => r.howto).map(r => r.url).join('\n') || 'NONE — no page has HowTo schema');
console.log(`\n=== Content depth: pages by word count ===`);
console.log(`<300w: ${rows.filter(r=>r.words<300).length} | 300-600: ${rows.filter(r=>r.words>=300&&r.words<600).length} | 600+: ${rows.filter(r=>r.words>=600).length}`);
