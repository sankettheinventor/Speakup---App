package com.snapjar.app;

import android.net.Uri;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Download a document from a direct link and hand it to the reader.
 *
 * Done NATIVELY rather than with fetch() in the WebView for three reasons: it sidesteps CORS
 * (most file hosts send no permissive headers), it needs no relaxation of the app's strict CSP,
 * and it lets us inspect the bytes BEFORE anything is opened.
 *
 * Safety rules enforced here:
 *  - http/https only, re-checked on every redirect (so a redirect can't drop to file://)
 *  - a hard size ceiling, applied to the declared length AND while streaming
 *  - the file must actually start with %PDF — a Content-Type header is a claim, not proof
 *  - the filename is derived and sanitised locally; the server never chooses a path
 *  - written to app-private cache, never shared storage
 */
@CapacitorPlugin(name = "FileDownload")
public class FileDownloadPlugin extends Plugin {

    private static final int  MAX_BYTES   = 100 * 1024 * 1024;   // 100 MB
    private static final int  MAX_HTML_BYTES = 4 * 1024 * 1024;  // 4 MB is far more than any article
    private static final int  MAX_HOPS    = 5;
    private static final int  TIMEOUT_MS  = 30000;

    @PluginMethod
    public void download(final PluginCall call) {
        final String urlStr = call.getString("url");
        if (urlStr == null || urlStr.length() == 0) { call.reject("url required"); return; }
        if (!isHttp(urlStr)) { call.reject("Only http and https links can be opened"); return; }

        new Thread(new Runnable() { public void run() {
            HttpURLConnection conn = null;
            File out = null;
            try {
                String current = urlStr;
                int hops = 0;
                while (true) {
                    if (!isHttp(current)) { call.reject("That link redirected somewhere unsafe"); return; }
                    URL u = new URL(current);
                    conn = (HttpURLConnection) u.openConnection();
                    conn.setInstanceFollowRedirects(false);      // we vet each hop ourselves
                    conn.setConnectTimeout(TIMEOUT_MS);
                    conn.setReadTimeout(TIMEOUT_MS);
                    conn.setRequestProperty("User-Agent", "SnapJar/1.0 (Android)");
                    conn.connect();
                    int code = conn.getResponseCode();
                    if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP
                            || code == HttpURLConnection.HTTP_SEE_OTHER || code == 307 || code == 308) {
                        String next = conn.getHeaderField("Location");
                        conn.disconnect();
                        if (next == null || ++hops > MAX_HOPS) { call.reject("That link redirects too many times"); return; }
                        current = new URL(new URL(current), next).toString();   // resolve relative redirects
                        continue;
                    }
                    if (code != HttpURLConnection.HTTP_OK) { call.reject("The link returned an error (" + code + ")"); return; }
                    break;
                }

                int declared = conn.getContentLength();
                if (declared > MAX_BYTES) { call.reject("That file is too large to open (over 100 MB)"); return; }

                String name = safeName(current, conn.getHeaderField("Content-Disposition"));
                File dir = new File(getContext().getCacheDir(), "downloads");
                if (!dir.exists()) dir.mkdirs();
                out = new File(dir, name);
                if (!out.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)) { call.reject("bad filename"); return; }

                InputStream in = new BufferedInputStream(conn.getInputStream());
                FileOutputStream fos = new FileOutputStream(out);
                byte[] buf = new byte[16384];
                byte[] head = new byte[5];
                int headLen = 0, n; long total = 0;
                try {
                    while ((n = in.read(buf)) > 0) {
                        if (headLen < 5) {                       // capture the magic bytes as they stream past
                            int take = Math.min(5 - headLen, n);
                            System.arraycopy(buf, 0, head, headLen, take);
                            headLen += take;
                        }
                        total += n;
                        if (total > MAX_BYTES) { throw new Exception("too large"); }
                        fos.write(buf, 0, n);
                    }
                } finally {
                    try { fos.close(); } catch (Exception ignored) {}
                    try { in.close(); } catch (Exception ignored) {}
                }

                // Trust the BYTES, not the headers: a Content-Type of application/pdf proves nothing.
                if (!(headLen >= 4 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F')) {
                    out.delete();
                    call.reject("That link isn’t a PDF file");
                    return;
                }

                JSObject ret = new JSObject();
                ret.put("path", out.getAbsolutePath());
                ret.put("name", name);
                ret.put("size", total);
                call.resolve(ret);
            } catch (Throwable t) {
                if (out != null) { try { out.delete(); } catch (Throwable ignored) {} }
                call.reject("Could not download that file — check the link and your connection");
            } finally {
                if (conn != null) { try { conn.disconnect(); } catch (Throwable ignored) {} }
            }
        }}).start();
    }

    /**
     * Fetch a web page as TEXT so the reader can extract just the article from it.
     * Natively again — CORS would block this from the WebView, and doing it here means the CSP
     * stays locked down. Returns raw HTML; the web layer parses it in an inert document and
     * pulls out the article text. Nothing is ever injected into the live page.
     */
    @PluginMethod
    public void fetchText(final PluginCall call) {
        final String urlStr = call.getString("url");
        if (urlStr == null || urlStr.length() == 0) { call.reject("url required"); return; }
        if (!isHttp(urlStr)) { call.reject("Only http and https links can be opened"); return; }

        new Thread(new Runnable() { public void run() {
            HttpURLConnection conn = null;
            try {
                String current = urlStr;
                int hops = 0;
                while (true) {
                    if (!isHttp(current)) { call.reject("That link redirected somewhere unsafe"); return; }
                    conn = (HttpURLConnection) new URL(current).openConnection();
                    conn.setInstanceFollowRedirects(false);
                    conn.setConnectTimeout(TIMEOUT_MS);
                    conn.setReadTimeout(TIMEOUT_MS);
                    // A desktop UA gets the full article; some sites serve stubs to unknown agents.
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
                    conn.setRequestProperty("Accept", "text/html,application/xhtml+xml");
                    conn.connect();
                    int code = conn.getResponseCode();
                    if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP
                            || code == HttpURLConnection.HTTP_SEE_OTHER || code == 307 || code == 308) {
                        String next = conn.getHeaderField("Location");
                        conn.disconnect();
                        if (next == null || ++hops > MAX_HOPS) { call.reject("That link redirects too many times"); return; }
                        current = new URL(new URL(current), next).toString();
                        continue;
                    }
                    if (code != HttpURLConnection.HTTP_OK) { call.reject("The page returned an error (" + code + ")"); return; }
                    break;
                }

                String ctype = conn.getContentType();
                if (ctype != null && !ctype.toLowerCase().contains("html") && !ctype.toLowerCase().contains("text")) {
                    call.reject("That link isn’t a readable page");
                    return;
                }

                InputStream in = new BufferedInputStream(conn.getInputStream());
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n; long total = 0;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_HTML_BYTES) break;      // enough for any article; ignore the rest
                    bos.write(buf, 0, n);
                }
                in.close();

                String charset = "UTF-8";
                if (ctype != null) {
                    int ci = ctype.toLowerCase().indexOf("charset=");
                    if (ci >= 0) { try { charset = ctype.substring(ci + 8).trim().replace("\"", "").split(";")[0]; } catch (Throwable ignored) {} }
                }
                String html;
                try { html = new String(bos.toByteArray(), charset); }
                catch (Throwable t) { html = new String(bos.toByteArray(), "UTF-8"); }

                JSObject ret = new JSObject();
                ret.put("html", html);
                ret.put("url", current);
                call.resolve(ret);
            } catch (Throwable t) {
                call.reject("Could not open that page — check the link and your connection");
            } finally {
                if (conn != null) { try { conn.disconnect(); } catch (Throwable ignored) {} }
            }
        }}).start();
    }

    private boolean isHttp(String s) {
        try {
            String sc = Uri.parse(s).getScheme();
            return sc != null && (sc.equalsIgnoreCase("http") || sc.equalsIgnoreCase("https"));
        } catch (Throwable t) { return false; }
    }

    /** Build a safe local filename. The server never gets to choose a path. */
    private String safeName(String url, String disposition) {
        String name = null;
        if (disposition != null) {
            int i = disposition.toLowerCase().indexOf("filename=");
            if (i >= 0) name = disposition.substring(i + 9).replace("\"", "").replace(";", "").trim();
        }
        if (name == null || name.length() == 0) {
            try { name = Uri.parse(url).getLastPathSegment(); } catch (Throwable ignored) {}
        }
        if (name == null || name.length() == 0) name = "document.pdf";
        name = new File(name).getName();                        // strip any directory components
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.length() > 80) name = name.substring(name.length() - 80);
        if (!name.toLowerCase().endsWith(".pdf")) name = name + ".pdf";
        return name;
    }
}
