# SnapJar — Pre-Launch Brutal Audit (2026-07-22)

Method: mechanical probes (npm audit, git history, manifest, deps, CSP, bundle grep) + 3 parallel deep-read agents (security / privacy-legal-store / functionality-ux-perf-a11y). Every finding cites real `file:line`. Items needing a physical device, mitmproxy, TalkBack, or Play/AdMob console are marked **DEVICE** / **USER-ACTION** — not faked.

Build audited: versionCode 6 / v1.0.5, real ads, `APP_LINK_FIX` app build.

---

## VERDICT: 🔴 NOT LAUNCH-READY

Gate = 9+ average, no section < 8. **Average ≈ 7.1; four sections < 8; one hard blocker.** Fix the blockers below, complete a real device pass, then re-gate.

### Scorecard

| # | Section | Score | Blocking gaps |
|---|---------|:---:|---|
| 1 | Core Functionality & QA | 7 | Destructive/edge cases unverified on device; 500-pg memory, storage-full, mid-op kill all **DEVICE** |
| 2 | User Experience | 7 | 🟠 **Keyboard can cover input sheets** (no adjustResize/viewport); no mid-op Cancel |
| 3 | Performance & Device | 7 | Cold-start/mem/fps **DEVICE**; **APK ~38MB vs <30MB target** (real cost in Tier-2/3 India) |
| 4 | Security | 9 | No blockers; 3 MED defense-in-depth items |
| 5 | Privacy & Legal | 6 | 🔴 **Terms page ships blank**; Crashlytics over-disclosure; 1 license missing |
| 6 | Accessibility | 7 | Code foundation good; TalkBack flow + contrast **DEVICE** |
| 7 | Store Listing & ASO | 6 | Assets not in repo — **USER-ACTION** (icon/screenshots/listing) |
| 8 | Monetization (Ads) | 8 | Cold-launch app-open caution; hosting/account **USER-ACTION** |
| 9 | Release Engineering | 6 | **No crash reporting shipped**; no internal-test track / staged rollout done |

---

## ✅ BLOCKER STATUS (updated 2026-07-22, v1.0.7)

- **BL-1 Terms blank — CLOSED.** terms.astro → `doc-page`, reworded/re-dated, `/terms` added to APP_LINK_FIX. Crash-sweep confirms it renders (2045 chars, 0 errors).
- **BL-2 Crashlytics mismatch — CLOSED.** Removed the Firebase Crashlytics collection claim; policy now describes the on-device-only crash note. Data Safety can declare no diagnostics collected.
- **HEIC crash (found in sweep) — CLOSED.** New native `HeicPlugin.java` (Android BitmapFactory HEIF decode, no eval, no CSP hole); heic2any made a lazy web-only fallback (removed from app boot → load-time EvalError gone). Verified on device (ff5496ab) via CDP: plugin registers + callable, real-JPEG decode→encode→bridge round-trip OK. HEIC-specific decode is Android's platform codec (API 28+ guarantee; device is API 33+) — recommend one real-file tap-test.
- **BL-3 Device QA week — STILL OPEN (user-action).** A ≥1-week internal-testing track on a budget phone cannot be automated. v1.0.7 is the build for that track. (Smoke-run done: installs, launches, HEIC page loads clean, native plugin works.)

---

## 🔴 BLOCKERS (original detail)

### BL-1 — Terms of Use ships as a blank page (and links to a dead route)
`settings.astro:41` links `/terms`, but:
- `/terms` is **not** in the `APP_LINK_FIX` allowlist (`Base.astro:43`) → in the Capacitor WebView the extensionless path falls back to home / fails to load.
- Even if loaded, `terms.astro:6` wraps all text in `<div class="content">`, which `app.css:243-245` hides in-app (`body.is-app .content{display:none !important}`).
- Fix: (a) add `terms` (+ `about`) to the `APP_LINK_FIX` regex; (b) switch `terms.astro` to `class="wrap doc-page"` (like `privacy-policy.astro`). Re-date (currently "June 2026") and reword web-isms ("run in your browser"/"using the site").

### BL-2 — Privacy policy declares Firebase Crashlytics that is NOT shipped → Data Safety mismatch
`privacy-policy.astro:35-44` describes Crashlytics collecting Android version, device model, install ID. But there's **no** `google-services.json`, **no** firebase dependency, and `Base.astro:771-778` says the crash recorder is on-device/zero-network with Crashlytics "still the plan." If the Data Safety form is filled to match the policy, it declares collection that isn't happening (suspension risk); if filled to match reality, it contradicts the published policy. Fix: remove/soften the Crashlytics section until it's actually integrated — **and** decide §9 crash-reporting first (they're the same decision).

### BL-3 — No device QA on the target low-end matrix
The whole point of the app is ₹8–12k phones (2–3GB RAM, Android 10–12). None of the destructive/perf/memory items (500-pg PDF, OCR peak memory, storage-full, mid-conversion kill, rotation state-loss, cold-start <2.5s) are verified on such a device. This is a launch gate, not a nicety — Android Vitals crash/ANR thresholds decide ranking. **Run the internal-testing track on a real budget device for ≥1 week.**

---

## 🟠 FAILS (fix before submitting)

- **F0 (HIGH) — Soft keyboard can cover text-input sheets.** No `android:windowSoftInputMode="adjustResize"` on the activity (`AndroidManifest.xml:22`) and **zero** `visualViewport`/resize handling anywhere, yet the shared prompt is a `bottom:0` sheet with a text field (`app.css:226` `.sj-ask{align-items:flex-end}`; inputs `sj-ask-in` `Base.astro:229`, `sjcpHex` `:962`, folder-name `recent.astro:163`, listen-URL `BottomNav.astro:200`). The IME can sit *over* the field → user types blind. Fix: add `adjustResize` + a global `visualViewport.resize` handler that lifts the open sheet by the covered height. **DEVICE** to confirm exact clipping; the missing mitigation is verifiable now.
- **F1a (bug) — `jpg-to-pdf` has no image-count cap** (`jpg-to-pdf.astro:71` loops all `files.length`) while siblings `compress-pdf.astro:105` and `pdf-to-jpg.astro:90` cap at 150 + toast. 200+ photos accumulate into one in-memory pdf-lib doc → OOM on budget phones. Mirror the sibling cap.
- **F1b (bug) — image tools bypass the shared `loadImage` timeout.** `SnapJarKit.loadImage` (onerror **+** 20s timeout, `Base.astro:119`) is used only in pdf-studio; `jpg-to-pdf.astro:65`, `watermark.astro:241/269` hand-roll `new Image()` with no timeout → a truncated image that fires neither onload nor onerror hangs forever (the exact case the guard prevents). Route them through `loadImage`.
- **F1 — "Nothing leaves your device" is not literally true.** `FileDownloadPlugin.java` (`fetchText`, `download`) makes **live network fetches** for the read-aloud "read this article" feature and returns the body to JS. Contradicts the CSP comment (`Base.astro:51`) and the privacy promise. Also a mild **SSRF** (no loopback/RFC-1918 blocking → can read `192.168.x.x` router pages). Fix: block loopback/link-local/site-local + non-standard ports on every redirect hop, OR strip the network reader from the app build to keep the offline claim honest.
- **F2 — `@mozilla/readability` bundled (`package.json:30`, used `Base.astro:554-579`) but missing from `licenses.astro`.** Add it (Apache-2.0).
- **F3 — Weak/reused signing passphrase.** `keystore.properties` (correctly gitignored, not in history) uses `snapjar-upload-2026` for both store and key. Rotate to a long random unique passphrase; back it up offline in 2 places.
- **F4 — APK ~38MB vs <30MB target.** OpenCV.js (~9MB, scanner) + bundled tesseract (~32MB assets, though stripped/compressed) dominate. Every MB costs installs in this market. Investigate: the dist emits **two** pdf.worker files (~310KB + ~1MB) — dedupe; confirm opencv/tesseract are download-on-demand or in a dynamic feature, not the base install.

---

## 🟡 MED / caution

- **M1 — App-open ad fires on cold launch** (`aoShowCold`, `Base.astro:1366`). Allowed, but it's the placement Google scrutinizes hardest (must not precede content, must be skippable). Safest: limit to background→foreground return only.
- **M2 — `FileActions`/`FolderAccess` source reads are unconfined** (`FileActionsPlugin.resolve()` reads any JS-supplied path). Write side is hardened; add the same canonical-path containment to the read side. Defense-in-depth (needs WebView script-exec to reach, which CSP + `isEvalSupported:false` strongly mitigate).
- **M3 — No mid-operation CANCEL** on long ops. Compress (`compress-pdf.astro:107`), pdf-to-jpg, jpg-to-pdf, and OCR (`text-scan.astro:256`, which *hides* the close button at `:198`) run 30–60s+ on budget phones with no abort — only killing the app escapes. Add an `aborted` flag checked at each loop top (you already `breathe()`, so it's honored within one page).
- **M6 — OCR page counter gets no determinate progress bar.** The auto-bar decorator watches `.status,.tmsg,[data-sj-status],[role=status]` (`Base.astro:242`) but the OCR counter `#txLoadS` is class `tx-load-s` (`text-scan.astro:262`) — not matched. Add `data-sj-status` to it; bar comes free.
- **M4 — npm audit: 12 vulns (1 critical, 7 high)** — all in **dev/build deps** (`tar`, `node-pre-gyp`, `uuid`), none shipped in the app bundle. Run `npm audit fix`; low priority.
- **M5 — CSP uses `'unsafe-inline'` for scripts** (required by Astro inline hydration). Residual risk low given `connect-src 'self'`, `object-src 'none'`, `base-uri 'self'`, `form-action 'none'`. Optional nonce/hash migration later.

---

## 🟢 CONFIRMED PASS (hard evidence)

**Security (strong):**
- CVE-2024-4367 (malicious-PDF JS exec) mitigated on **all six** `getDocument()` calls via `isEvalSupported:false` (compress-pdf:102, organize-pdf:89, pdf-to-jpg:88, pdf-studio:930, pdf-to-word:86).
- No `eval()`/`new Function()` in `src/`. WebView has no `allowFileAccess`/`allowUniversalAccessFromFileURLs`/`addJavascriptInterface`. `androidScheme:https`, `allowMixedContent:false`, no `server.url`, no `allowNavigation`.
- Intent filters are **content:// only** (file:// deliberately excluded); incoming files sanitized + canonical-path contained. Shares via `FileProvider` (`file_paths.xml` scoped to `cache/shares/`), never `file://`.
- External links → **Chrome Custom Tab** / `_system`, never in-app WebView.
- No hardcoded secrets; only embedded IDs are public AdMob app/unit IDs. Keystore gitignored + absent from git history.
- `allowBackup=false`, no `debuggable`, `minifyEnabled`+`shrinkResources`+proguard, `esbuild drop:['console','debugger']`.

**Privacy/legal:**
- Privacy policy is app-visible (`doc-page`, in allowlist, linked from settings), dated July 2026, covers on-device processing + AdMob Ad ID + consent + permissions + COPPA + contact.
- UMP consent wired **before** `AdMob.initialize` with npa fallback on every failure path; withdraw-consent control in settings.
- Permissions minimal: INTERNET, AD_ID, POST_NOTIFICATIONS, FOREGROUND_SERVICE(+MEDIA_PLAYBACK), CAMERA (`required=false`), VIBRATE. **No MANAGE_EXTERNAL_STORAGE / READ|WRITE_EXTERNAL_STORAGE / location.** targetSdk 36 (≥ Play's API 35).
- Licenses page otherwise thorough; fonts self-hosted + license-safe (Jakarta OFL, Fluent MIT). app-ads.txt format valid, publisher ID matches manifest.

**Ads placement:** interstitials only at deliberate seams (post-export/reader-exit/read-aloud-end), never on timer/mid-task, 70s global cap; banner TOP_CENTER, auto-hidden under sheets, reserved strip (no CLS); app works if ads fail (`swallow()` everywhere). Plus the new no-fill backoff retry (10→20→40s).

**Functionality/UX/perf/a11y (code-level):**
- Shared robustness kit present + used: `fitDims` (canvas clamp), `loadImage` (onerror+timeout), `guard` (double-submit), blob `track/clear`, `breathe`, `checkFile`, global `%PDF` header sniff → corrupt/wrong-type/0-byte toasted, not silent.
- OCR native ML Kit primary (`TextOcr` plugin), bundled tesseract fallback at `/assets/tesseract` (no CDN). Airplane-mode safe.
- Heavy engines dynamically imported: pdf-lib, tesseract, mammoth, xlsx, pptx-preview, opencv (scanner). Not in boot path.
- Back button wired per-screen (scanner, text-scan, qr-scan, highlights, BottomNav) — leaves overlays before exiting.
- Safe-area insets (16 refs in app.css), 44px touch-target floor (`pointer:coarse`), `prefers-reduced-motion` respected, aria-labels present (pdf-studio 60, color-picker 7…), designed empty state in My Files.

---

## Fix order

1. **BL-1** Terms routing + `.content` → `doc-page` (code, ~10 min)
2. **BL-2 / §9** Decide crash reporting (ship Crashlytics or cut the policy section) — drives Data Safety
3. **F1** Reconcile offline claim vs network reader (block private IPs or strip from app build)
4. **F2/F3** readability license + rotate signing passphrase
5. **F4** APK size (dedupe pdf.worker, on-demand opencv/tesseract)
6. **BL-3** Real device QA week on a budget phone (destructive + perf + Vitals)
7. USER-ACTION: Data Safety form, UMP console messages, app-ads.txt hosting, foreground-service declaration, store assets, screenshot IP check
