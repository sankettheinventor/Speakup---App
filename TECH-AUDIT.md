# SnapJar — Brutal Technical Audit (deep)

_2026-06-22. Mechanical probes across all 45 pages + 3 parallel deep-read auditors (PDF tools / image tools / calculators). Severity: 🔴 crash·hang·wrong-output · 🟡 should-fix · 🟢 minor. ✅ = fixed this pass; ⏳ = pending (often needs the device to measure/see)._

## CLEAN BILLS (verified, not a problem)
- ✅ **XSS: none.** Every `innerHTML` uses a static template or numeric computed value; all user text goes through `textContent`. qr-code/svg-optimizer output to canvas/`textarea.value`, never `innerHTML`. (Important: the WebView has Capacitor native-bridge access, so this mattered.)
- ✅ **Network/connection/timeout-from-network: N/A in the app.** Only `cloud-import.js` has `fetch()`, and it's stripped from the app build. The app makes **zero network calls** → no connection/offline-timeout failure modes.
- ✅ **localStorage** fully try/guarded (Base.astro only). No `JSON.parse`, no `eval`/`Function`.
- ✅ **Finance formulas correct:** EMI, mortgage, SIP FV, compound interest verified right; `num()` coercion + `i>0` guards mean **no NaN/Infinity/₹Infinity leaks** anywhere in the calculators.
- ✅ **Android config sane:** minSdk 24 (broad budget reach), targetSdk 36 (exceeds Play-2026 API-35), versionCode 1.

## 🔴 FIXED THIS PASS (deterministic, verified by reasoning)
- ✅ **income-tax: 87A rebate cliff → marginal relief.** New regime jumped tax from ₹0 at ₹12,00,000 taxable to ~₹60k+cess at ₹12,00,001 — **legally wrong**. Added marginal relief (`tax=min(tax, taxable−1200000)`). Old-regime ₹5L cliff left as-is (that one IS the law — no marginal relief in old regime).
- ✅ **age calculator: UTC off-by-one.** `toISOString()` + `new Date(string)` shifted the date a day for non-UTC zones (IST audience). Now formats/parses yyyy-mm-dd in **local** time.
- ✅ **extract-pages / remove-pages: silent range bug.** `5-3` (reversed) and `1-`/`-5` (NaN) silently produced nothing. Now swaps `a>b` and guards NaN.
- ✅ **qr-code: WiFi/vCard not escaped.** SSID/password/name with `; , : " \` made a **malformed, unscannable** QR. Added spec-correct escaping.

## ✅ ROOT-LEVEL CLOSURE — pass 2 (2026-06-22, via shared `SnapJarKit`)
The recurring robustness bugs are now closed at the root: a single shared toolkit
(`window.SnapJarKit`, defined once in `Base.astro`, loaded before every tool) provides
`fitDims` (canvas clamp), `loadImage` (onerror + timeout), `guard` (double-submit),
`breathe` (yield), `track`/`clear` (blob lifecycle) — and all 20 PDF/image tools were
wired to call them. Build green; all 65 pages compile; app strips intact.
- ✅ **Canvas OOM** — `fitDims` (4096px / 16.7MP ceiling) on every pdf.js render (re-derived viewport so render matches canvas) and every image-tool draw (composed with existing resize, not double-applied; shows a "downscaled" notice).
- ✅ **Frozen-on-bad-input** — `img.onerror` / `loadImage` timeout across the image tools (jpg-to-pdf no longer hangs forever); try/catch wraps every PDF load/process with a clear status message; pdf.js password PDFs now show an error instead of freezing.
- ✅ **Double-submit / concurrent corruption** — `guard()` disables the action button for the op across all 20 tools (reset/browse left alone).
- ✅ **Blob-URL leaks** — batch thumbnails use `track()` + `clear()` on reset.
- ✅ **UI freeze on big files** — `breathe()` yields between pages in every long loop, with progress text.
- ✅ **watermark "Download all"** — serialized (was N concurrent full-res encodes → OOM).
- ✅ **`toBlob` null**, **WebP feature-detect** (convert/favicon), **watermark-pdf long-text shrink-to-fit** — all handled.
- _Thresholds (4096/16.7MP, 20s timeout) are universally-safe defaults; tune up after one device measurement if budget phones tolerate more._

## 🟢 STILL OPEN — genuinely need the device, or cosmetic/minor

**Native (need device + native code — can't be verified blind):**
- ⏳ **Share-target handler NOT wired.** Manifest advertises SnapJar as a share destination, but there's no `appUrlOpen`/SendIntent handler → sharing a file to SnapJar opens the app and does nothing. Dead feature if shipped.
- ⏳ **No hardware back-button handling** — confirm Android back doesn't just exit from a tool screen.
- ⏳ **pdfjs worker offline** — confirm the bundled `pdf.worker` loads under the `https://localhost` Capacitor scheme on-device.
- ⏳ **No explicit Cancel button** on long ops — the UI no longer *freezes* (`breathe()` yields + progress), but there's still no way to abort a huge job mid-run. Add a Cancel that flips a flag the loops check. *(Worth doing with the device to feel the timing.)*

**Minor logic / cosmetic — FIXED 2026-06-22:**
- ✅ **income-tax slab breakdown table** — extended bands/labels to include the new-regime 16L/20L/24L edges, so the top row no longer lumps 20/25/30% into one mislabeled "20%" line.
- ✅ **loan-payoff headline** — now derived from the month-by-month sim (incl. yearly lump), not the closed-form (which ignored the lump and went NaN when a lump cleared a loan whose monthly payment alone didn't cover interest).
- ✅ **tip round-up** — per-person amounts now reconcile: total = `each × split`, tip = total − bill (no more "26+26 but total says 51").
- ✅ **Filename collisions** — the 6 single-source PDF tools (add-page-numbers/extract/remove/rotate/watermark-pdf/split) now prefix the output with the source filename (`report-rotated.pdf`), fixing collisions and giving useful names.

**Still minor / open (truly trivial):**
- ⏳ download `revokeObjectURL` on a fixed 1500ms timer — on a very slow phone the blob could be revoked before the download consumes it. Gate on the download instead.
- ⏳ svg-optimizer whitespace-collapse can alter text spacing inside `<text>`/`xml:space="preserve"`.
- ⏳ fd post-tax yield model deducts tax from a gross-compounded balance — internally inconsistent (not user-facing-broken).

_Everything in the original 🔴 crash/hang + 🟡 robustness lists is CLOSED (see "ROOT-LEVEL CLOSURE" above). What remains is native (device-gated) or low-impact cosmetic. The shared `SnapJarKit` is the permanent home for these patterns — new tools inherit the fixes by calling it._
