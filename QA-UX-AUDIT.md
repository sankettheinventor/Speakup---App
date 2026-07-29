# SnapJar — Brutal UX/UI Audit + Extreme QA (pre-device)

_Generated 2026-06-22 against the real codebase. No sugar-coating. Severity: 🔴 blocker · 🟡 should-fix · 🟢 verify-on-device. "Fixed" = done in this pass; "Device" = needs the phone to confirm/fix._

---

## PART A — UX/UI AUDIT (brutal)

### A1. Responsiveness / small-phone (primary target = ₹8–12k Android, 320–360px)
- 🟡→**Fixed** **20 of 25 tool pages had NO ≤560px breakpoint.** Each tool defines its own `.row2`/`.wm-grid`/`.acts`; many stayed 2-up and cramped on a 360px phone. Added global small-phone overrides (`style.css` → "SMALL-PHONE HARDENING") that force single-column stacking + action-row wrap regardless of per-tool CSS. **Verify visually on device.**
- 🟡→**Fixed** **Tap targets too small.** `organize-pdf .pbtns button` = 26px, `pdf-tools .fl-btn` = 30px (rotate/delete/reorder). Now floored to 44px on touch devices via `@media(pointer:coarse)` in style.css (`.pbtns` has flex-wrap so cells re-flow). **Verify on device** the thumbnail cells don't look broken.
- 🟢 **Device** Fixed-width preview cells: `heic-to-jpg .cv-item` 140px, `organize-pdf .pcell` 130px. Flex-wrap should give 2-per-row at 320px — **confirm no horizontal scroll.**
- 🟢 **Device** Mega-menu becomes a fixed full-screen panel ≤680px (`max-height:74vh;overflow:auto`) — confirm it scrolls and closes cleanly; it's the app's primary nav.

### A2. Interaction / friction (UX doc 01)
- 🟡 **Fragmented primary-button classes** (`.wm-btn`/`.dlbtn`/`.make`/`.go`) across tools — "multiple traditional designs." *Behavior* unified via system CSS (press/hover), but a real shared button component is still owed.
- 🟡 **Device** **The "win" is weak.** Most tools end on a plain `#status` text line; calculators now get the `.result` rise-in, but file-output tools don't celebrate. `.anim-pop` utility is ready — wire it on success per tool (do on device, where it can be seen/felt).
- 🟡 **Device** **No real progress on long ops.** Multi-page compress/OCR/render show "Compressing page N…" text only — file 16 §3.3 wants a true progress indicator, not a frozen-looking line. Verify on a 40-page PDF; add a progress bar if it feels dead.
- 🟡→**Fixed** **App ad placeholders were dead boxes.** 45 `.ad` slots rendered an empty dashed "Advertisement" box → looked unfinished in the app. Now hidden in the app build (`body.is-app .ad{display:none}`, set only when `PUBLIC_APP_BUILD=1`); web still shows them. Replace with real AdMob native units in the ad phase.
- 🟢 **Device** **No "My Files"/Recents yet** — a saved output has nowhere to live (file 16 §2.1.3). Outputs currently just trigger a browser-style download. Core retention feature still to build.

### A3. State & edge cases (the unhappy paths — where quality lives)
- 🟡 **Device** Corrupt/password-protected PDF: tools use `ignoreEncryption:true` and a try/catch, but the error copy varies per tool. Confirm every tool shows a *clear* error, never a silent no-op or a stuck spinner.
- 🟡 **Device** Huge file (200MB / 40+ pages): WebView memory death risk (file 16 §2.1.1). No page-count/size guardrail exists yet. **Must test on a budget device** — likely needs a size cap + chunking.
- 🟢 **Device** Empty/zero input: e.g. text-to-pdf with empty box generates a 1-page blank PDF (acceptable); extract-pages with no range shows "Enter pages." Confirm no tool crashes on empty/whitespace input.
- 🟢 **Device** Backgrounding mid-task (call/notification) — does in-progress work survive? (file 16 §2.1.4). No state persistence yet.

### A4. Accessibility (structural, not a checkbox)
- 🟡→**Fixed (partial)** Readonly result inputs lacked `aria-label`. Added to color-picker (HEX/RGB/HSL), color-converter (RGB/HSL), gradient-generator (CSS), plus disambiguated the duplicate "Copy" buttons. **Remaining:** a full sweep of every calculator/slider for stragglers (do on device with a screen reader).
- 🟢 Focus-visible ring exists globally ✓. Reduced-motion guard exists ✓ (and now covers the new motion system).
- 🟢 **Device** Color-only state: drop-zone "drag" state is border-color + a subtle scale now — confirm it's perceivable.

---

## PART B — EXTREME QA TEST CASES (run on device)

### B1. Per-tool functional smoke (every tool, the canonical path)
For EACH tool: load → do the work → get correct output → output opens elsewhere.
- [ ] PDF: merge, split, compress, organize, rotate, add-page-numbers, watermark, extract-pages, remove-pages, jpg→pdf, pdf→jpg, text→pdf
- [ ] Image: compress, convert (6 format pairs), heic→jpg, watermark, favicon, qr-code, color tools, svg-optimizer, palette
- [ ] Calculators (12): result matches the web version exactly; `.result` animates in once.

### B2. Extreme inputs (stress — the Tuesday-with-bad-data reality)
- [ ] **Biggest plausible file** on the weakest phone: 50MB image, 40-page scanned PDF, 200-page PDF → no WebView kill, no white screen.
- [ ] **Malformed/corrupt** PDF and image → clear error, never a crash or silent hang.
- [ ] **Password-protected PDF** → opens (ignoreEncryption) or fails with a readable message.
- [ ] **0-byte / empty / whitespace-only** input → graceful, no crash.
- [ ] **Huge batch**: 100 HEIC files / 50 images to convert → progress shown, memory holds or degrades gracefully.
- [ ] **Wrong file type** dropped (e.g. .txt into a PDF tool) → rejected with a message, not a broken state.
- [ ] **Rapid repeat**: run the same tool 20× in one session → no memory leak / slowdown.
- [ ] **Unicode / RTL / emoji** in text→pdf, qr, watermark, word-counter → renders, no mojibake.
- [ ] **Extreme slider values** (quality 20, scale 3.0 on a 40-page PDF) → completes or fails cleanly.

### B3. Offline & environment (the whole point of the app)
- [ ] **Airplane mode**: every tool works fully offline (this is the core promise — must be 100%).
- [ ] **Zero external requests**: confirm via a proxy/devtools that the app makes NO network calls (we stripped them; verify at runtime).
- [ ] **Low storage / low memory**: graceful messaging, no crash.
- [ ] **Dark + light mode**: every tool legible in both; toggle persists.

### B4. Small-screen layout (the fixes above — VERIFY)
- [ ] 320px (very small) and 360px (typical budget): NO horizontal scroll on any page.
- [ ] Every paired control row stacks to 1 column; no overlapping controls.
- [ ] All buttons tappable without zoom (thumb test); PDF reorder/rotate buttons not too small.
- [ ] Nav mega-menu opens, scrolls, closes; reaches every tool in ≤3 taps.
- [ ] Long content (longest tool title, 14-item list, longest filename) doesn't break layout.

### B5. Interruption & lifecycle
- [ ] Background the app mid-conversion (simulate a call) → returns without losing/corrupting work, or fails cleanly.
- [ ] Rotate device mid-task → layout survives, state preserved.
- [ ] Hardware back button at each screen → expected navigation, no dead-ends.

---

## PART C — PRIORITY FIX ORDER (after device pass confirms)
1. WebView memory guardrails (size/page cap + chunking) — the #1 one-star generator.
2. Tap-target floor on PDF reorder/rotate buttons.
3. The "win" pop wired per tool + real progress bar on slow ops.
4. My Files / Recents + state persistence.
5. Replace ad placeholders with real AdMob native units; a11y aria-label sweep.

---

## PART D — CLOSED AT ROOT LEVEL (this pass, build-verified, device-independent)
- ✅ Small-phone stacking (20 tools that lacked a ≤560px breakpoint) — global override.
- ✅ Touch-target floor (44px on `.pbtns button` / `.fl-btn`, touch devices only).
- ✅ App ad placeholders hidden in app build (`body.is-app .ad`).
- ✅ aria-labels on color/gradient readonly outputs + Copy buttons.
- ✅ Ad label consistency (45× "Advertisement", one theme).
- ✅ Motion system (press/drag/focus/slider/result-reveal), reduced-motion-safe.
- ✅ **Closed by the CDN→bundled migration** (no longer possible): the "PDF engine loading" hidden-container bug, the favicon `.zip` silent no-op, and the entire "CDN-blocked race" class — bundled engines are always present, so those `typeof X==='undefined'` failure paths can't fire.

**Genuinely NEEDS the device (can't fix blind — needs measurement or eyes):** WebView memory guardrails for big files (#1 risk — needs to know what size kills a budget phone), per-tool "win" pop wiring + real progress bars (must be seen/felt), My Files/Recents + state persistence (native feature), full a11y sweep with a screen reader, and confirming the small-phone/tap-target fixes actually look right.

_This file is the device-session script. Walk B1→B5 on the real phone; fix in C order._
