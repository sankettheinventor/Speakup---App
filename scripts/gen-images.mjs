// One-off asset generator: OG image, apple-touch-icon, PWA icons.
// Run: node scripts/gen-images.mjs   (uses sharp, already a dependency)
import sharp from 'sharp';
import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

mkdirSync(fileURLToPath(new URL('../public/', import.meta.url)), { recursive: true });
const out = (f) => fileURLToPath(new URL(`../public/${f}`, import.meta.url));

// Lightning bolt path on a violet→pink rounded square — the SnapJar mark.
const mark = (size, pad) => `
  <defs>
    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#ff5e7e"/><stop offset="1" stop-color="#7c5cff"/>
    </linearGradient>
  </defs>
  <rect x="${pad}" y="${pad}" width="${size-2*pad}" height="${size-2*pad}" rx="${size*0.22}" fill="url(#g)"/>
  <path transform="translate(${size*0.30},${size*0.22}) scale(${size*0.0046})"
    d="M55 0 L18 58 L44 58 L30 100 L82 36 L52 36 L66 0 Z" fill="#ffffff"/>
`;

const icon = (size) => Buffer.from(
  `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">${mark(size, 0)}</svg>`
);

const og = Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630" viewBox="0 0 1200 630">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#16121f"/><stop offset="1" stop-color="#0c0a14"/>
    </linearGradient>
    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#ff5e7e"/><stop offset="1" stop-color="#7c5cff"/>
    </linearGradient>
  </defs>
  <rect width="1200" height="630" fill="url(#bg)"/>
  <rect x="0" y="0" width="1200" height="8" fill="url(#g)"/>
  <g transform="translate(96,150)">
    <rect width="120" height="120" rx="28" fill="url(#g)"/>
    <path transform="translate(36,26) scale(0.55)" d="M55 0 L18 58 L44 58 L30 100 L82 36 L52 36 L66 0 Z" fill="#fff"/>
  </g>
  <text x="240" y="240" font-family="Segoe UI, Arial, sans-serif" font-size="92" font-weight="800" fill="#ece8f5">SnapJar</text>
  <text x="98" y="365" font-family="Segoe UI, Arial, sans-serif" font-size="52" font-weight="700" fill="#ece8f5">Free Online Calculators &amp; Image Tools</text>
  <text x="98" y="430" font-family="Segoe UI, Arial, sans-serif" font-size="34" font-weight="400" fill="#aea7c5">No signup · No upload · Everything runs in your browser</text>
  <g font-family="Segoe UI, Arial, sans-serif" font-size="28" font-weight="700" fill="#cfc8e8">
    <rect x="98" y="500" width="220" height="56" rx="28" fill="#1f1930" stroke="#2c2440"/>
    <text x="124" y="536">⚡ 40+ tools</text>
    <rect x="340" y="500" width="240" height="56" rx="28" fill="#1f1930" stroke="#2c2440"/>
    <text x="366" y="536">100% private</text>
  </g>
</svg>`);

await sharp(og).png().toFile(out('og.png'));
await sharp(icon(180)).png().toFile(out('apple-touch-icon.png'));
await sharp(icon(192)).png().toFile(out('icon-192.png'));
await sharp(icon(512)).png().toFile(out('icon-512.png'));
console.log('Generated og.png, apple-touch-icon.png, icon-192.png, icon-512.png');
