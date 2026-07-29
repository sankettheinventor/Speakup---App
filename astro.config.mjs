import { defineConfig } from 'astro/config';
import fs from 'node:fs';
import path from 'node:path';

// Update `site` to your real domain when you have one — powers sitemap + canonical URLs.
// APP build → 'file' (/tools/x.html): Capacitor's WebView asset server resolves these
// directly. (Directory/index.html paths fall back to the root index, so every tool
// opened the home.) WEB build keeps 'directory' for pretty SEO URLs.
const isApp = process.env.PUBLIC_APP_BUILD === '1';

// SECURITY/PRIVACY: strip pages that reach external servers from the APP bundle so they can't
// be hit by direct URL inside the offline app. video-to-mp3 fetches a 32MB ffmpeg wasm + jszip
// from jsDelivr/unpkg; /admin (Decap CMS) loads unpkg; cloud-import.js talks to Google/Dropbox.
// All three are web-only — never linked from the app UI — so removing them from dist is safe.
const stripFromApp = {
  name: 'snapjar-strip-app-web-only',
  hooks: {
    'astro:build:done': ({ dir }) => {
      if (!isApp) return;
      const root = dir && dir.pathname ? dir.pathname : path.resolve('dist');
      const base = process.platform === 'win32' && root.startsWith('/') ? root.slice(1) : root;
      // 'assets/tesseract' (~15MB) and 'ffmpeg' (~233KB): dead weight in the APP build.
      // OCR in the app runs on native ML Kit (TextOcrPlugin) — tesseract.js is only the WEB
      // fallback — and video-to-mp3 (the only ffmpeg consumer) is stripped just above.
      // This is the single biggest install-size win for low-end/Tier-2 devices.
      const targets = ['tools/video-to-mp3.html', 'admin', 'assets/cloud-import.js', 'assets/tesseract', 'ffmpeg'];
      for (const t of targets) {
        const p = path.join(base, t);
        try { fs.rmSync(p, { recursive: true, force: true }); console.log('[strip-app] removed ' + t); } catch (e) {}
      }
    },
  },
};

/* Guard against the sheet-under-the-ad bug coming back.
   The AdMob banner is a NATIVE view painted over the WebView, so no z-index can sit above it.
   app.css protects every sheet by keying its height cap to [role="dialog"] — which only works
   if new sheets actually carry that attribute. This fails the BUILD if a sheet card ships
   without it, so the protection can't be silently lost months from now. It doubles as an
   accessibility check: a dialog without role="dialog" is a screen-reader bug too. */
const sheetRoleGuard = {
  name: 'snapjar-sheet-role-guard',
  hooks: {
    'astro:build:done': ({ dir }) => {
      if (!isApp) return;
      const root = dir && dir.pathname ? dir.pathname : path.resolve('dist');
      const base = process.platform === 'win32' && root.startsWith('/') ? root.slice(1) : root;
      const CARD = /class="[^"]*\b(scan-card|pr-sheet-card|lib-sheet-card|tx-vcard|sj-lscard|lsn-card)\b[^"]*"[^>]*>/g;
      const offenders = [];
      const walk = (d) => {
        for (const e of fs.readdirSync(d, { withFileTypes: true })) {
          const f = path.join(d, e.name);
          if (e.isDirectory()) { walk(f); continue; }
          if (!e.name.endsWith('.html')) continue;
          const html = fs.readFileSync(f, 'utf8');
          for (const m of html.matchAll(CARD)) {
            if (!m[0].includes('role="dialog"')) offenders.push(path.relative(base, f) + ' → .' + m[1]);
          }
        }
      };
      try { walk(base); } catch (e) { return; }
      if (offenders.length) {
        throw new Error(
          '[sheet-guard] Sheet card(s) missing role="dialog" — they will slide under the ad banner:\n  ' +
          offenders.join('\n  ') +
          '\nAdd role="dialog" (and aria-modal/aria-label) to each.'
        );
      }
      console.log('[sheet-guard] all sheets carry role="dialog" — ad-safe');
    },
  },
};

export default defineConfig({
  site: 'https://snapjar.sankettoraskar-business.workers.dev',
  build: { format: isApp ? 'file' : 'directory' },
  // Drop console/debugger from shipped bundles: on Android these write to logcat, where any
  // process with adb can read them, and they can retain references to user data.
  vite: { esbuild: { drop: ['console', 'debugger'] } },
  integrations: isApp ? [stripFromApp, sheetRoleGuard] : [],
});
