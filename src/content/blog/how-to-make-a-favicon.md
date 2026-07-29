---
title: How to Make a Favicon (and Every App Icon) for Your Website
description: Modern sites need a whole set of icon files. Here's what they are, what sizes you need, and how to generate them all from one image.
date: 2026-06-08
category: Web Design
emoji: ⭐
tool: /tools/favicon
toolName: Open Icon Generator
---

That little icon in a browser tab is your **favicon** — but a modern site or app actually needs a whole family of icons. Here's the full list and the fast way to make them.

## The icons you actually need

- **Favicon:** 16, 32, 48 px PNGs plus a real multi-resolution `favicon.ico`.
- **Apple touch icons:** 120, 152, 167, 180 px for iPhone & iPad home screens.
- **Android / PWA:** 192 and 512 px for the web-app manifest.
- **App Store:** a 1024 px master.

## Generate them all at once

1. Open the [favicon & app icon generator](/tools/favicon).
2. Start from an **image**, or type a **letter or emoji** and pick colours.
3. Download the `.ico` on its own, or grab **everything zipped** with the HTML snippet ready to paste.

## Add them to your site

Paste the generated snippet into your `<head>`:

```
<link rel="icon" type="image/png" sizes="32x32" href="/favicon-32x32.png">
<link rel="apple-touch-icon" sizes="180x180" href="/apple-touch-icon.png">
<link rel="icon" href="/favicon.ico" sizes="any">
```

That's it — a crisp icon everywhere your site shows up, generated in your browser without uploading anything.
