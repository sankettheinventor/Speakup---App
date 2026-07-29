# SnapJar — Brutal SEO / UX / Dev Audit

_Generated from the actual built `dist/` (58 pages) + full code review + parallel dev audits. No sugar-coating._

## Score: the site is **technically clean but content-thin**. It will get indexed fine and rank for nothing competitive until the content depth + structured data are fixed.

---

## 🔴 CRITICAL (fix now — Semrush flags these)

1. **21 of 45 tool/calculator meta descriptions are 161–198 chars.** Google truncates at ~160. Every one of these gets cut off mid-sentence in search results → lower CTR. Worst offenders: qr-code (198), loan-payoff (196), favicon (194), unit-converter (181), emi (175).
2. **FAQ schema exists on exactly 1 page** (`/calculators/emi`). The other 44 tool/calculator pages have zero structured Q&A. This is the single biggest miss for Google rich results AND AI Overviews (ChatGPT/Perplexity/Google AI quote FAQ blocks). 1/45 coverage is effectively "not implemented."
3. **No HowTo schema anywhere.** Every "how to watermark / convert / make a favicon" tool is a textbook HowTo candidate. Zero implemented.

## 🟡 HIGH (content depth — this is what Semrush's "listicle" tip is about)

4. **55 of 58 pages are 300–600 words. Only 2 pages exceed 600.** Tool/calculator pages are thin. For competitive terms ("EMI calculator", "HEIC to JPG") thin pages lose to iLovePDF/ClearTax/etc. Each needs: a real explainer, a worked example, and an FAQ block. That single change fixes thin-content AND the missing FAQ schema in one pass.
5. **Blog has 4 posts, none are listicles.** Semrush's panel is correct: listicles ("7 Best Free PDF Tools", "10 Image Formats Explained") match search intent, win Featured Snippets, and get cited in AI Overviews. The blog is your only thin-content escape hatch — it's barely used.
6. **Homepage `<title>` is 74 chars** (truncated) and **`<meta description>` is 173 chars** (truncated). The most important page in the site is cut off in SERPs.

## 🟢 ALREADY GOOD (verified, don't touch)

- ✅ Every page has a unique title + description (no duplicates across 58 pages).
- ✅ Every page has exactly one `<h1>` and a canonical tag.
- ✅ Zero images missing `alt`.
- ✅ robots.txt + sitemap.xml correct; sitemap referenced in robots.
- ✅ OG image + Twitter Cards + Organization/WebSite structured data (just added).
- ✅ Core Web Vitals: the only "long task" is 72ms (Semrush/PSI both say it doesn't affect score).
- ✅ No calculator is broken; all compute on load. Tools work when CDN is reachable.

## ⚙️ DEV / UX issues (from parallel code audit — defensive, not user-facing in normal use)

- **CDN-blocked race (≈10 tools):** every pdf-lib / JSZip / heic2any / qr tool no-ops or throws a *wrong* error if its CDN is blocked (ad-blocker/corporate network). `text-to-pdf` is the only one that shows a proper "engine not loaded" message — copy that pattern everywhere.
- **pdf-to-jpg:** the "PDF engine loading…" message writes into a still-`hidden` container → user sees nothing. 🔴
- **favicon "All sizes (.zip)":** silent no-op if JSZip blocked. 🔴
- **income-tax:** the "Tax + 4% cess" breakdown box duplicates the main total figure (redundant).
- **a11y:** readonly result inputs in color-picker / color-converter / gradient-generator and several calculator inputs lack `aria-label`.

---

## Priority order to execute

1. Trim the 21 long descriptions + homepage title/desc. _(mechanical, 1 pass)_
2. Build a reusable FAQ block (visible accordion + FAQPage JSON-LD) and drop 3–5 Q&As onto every calculator + tool. _(fixes #2, #3, #4 together)_
3. Write 3–4 listicle blog posts targeting head terms.
4. Add the CDN-loaded guard pattern to all library tools; fix pdf-to-jpg + favicon feedback.
5. a11y aria-label sweep.
