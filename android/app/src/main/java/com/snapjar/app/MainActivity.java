package com.snapjar.app;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import com.getcapacitor.BridgeActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(FolderAccessPlugin.class);
        registerPlugin(IncomingFilePlugin.class);
        registerPlugin(AppOpenPlugin.class);
        registerPlugin(FileActionsPlugin.class);
        registerPlugin(TextOcrPlugin.class);
        registerPlugin(TtsPlugin.class);
        registerPlugin(FileDownloadPlugin.class);
        registerPlugin(HeicPlugin.class);
        super.onCreate(savedInstanceState);
        handleIncoming(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncoming(intent);   // copy runs on a background thread; it posts the event when done
    }

    private void postIncomingEvent() {
        try {
            if (getBridge() != null && getBridge().getWebView() != null) {
                getBridge().getWebView().post(() ->
                    getBridge().getWebView().evaluateJavascript(
                        "window.dispatchEvent(new Event('sjIncomingFile'))", null));
            }
        } catch (Exception ignored) {}
    }

    /** Copy an incoming VIEW/SEND file (PDF or image) into app storage for the web layer.
     *  The copy runs on a BACKGROUND thread — doing it on the UI thread during onCreate/onNewIntent
     *  blocked the main thread on large files → ANR during the open-with launch. */
    private void handleIncoming(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();

        // Shared plain TEXT (no file): hand it straight to the web layer to read aloud.
        // Nothing is fetched or uploaded — the user handed us the text, so the on-device
        // privacy promise is untouched.
        if (Intent.ACTION_SEND.equals(action) && intent.getParcelableExtra(Intent.EXTRA_STREAM) == null) {
            CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (shared != null && shared.toString().trim().length() > 0) {
                IncomingFilePlugin.pendingText = shared.toString();
                CharSequence subj = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT);
                IncomingFilePlugin.pendingTextTitle = subj == null ? null : subj.toString();
                postIncomingEvent();
                return;
            }
        }

        final Uri uri;
        if (Intent.ACTION_VIEW.equals(action)) uri = intent.getData();
        else if (Intent.ACTION_SEND.equals(action)) uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        else uri = null;
        if (uri == null) return;
        new Thread(() -> {
            try {
                String mime = getContentResolver().getType(uri);
                String name = resolveName(uri, mime);
                File dir = new File(getFilesDir(), "incoming");
                if (!dir.exists()) dir.mkdirs();
                // clear stale copies — only the newest incoming file is ever pending, so don't let
                // plaintext copies of every opened-with file accumulate in private storage.
                File[] stale = dir.listFiles(); if (stale != null) for (File s : stale) { try { s.delete(); } catch (Throwable t) {} }
                File out = new File(dir, name);
                // never let an externally-supplied name escape the incoming dir
                if (!out.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)) return;
                InputStream in = getContentResolver().openInputStream(uri);
                if (in == null) return;
                FileOutputStream fos = new FileOutputStream(out);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                fos.close();
                in.close();
                IncomingFilePlugin.pendingPath = out.getAbsolutePath();
                IncomingFilePlugin.pendingName = name;
                IncomingFilePlugin.pendingMime = mime == null ? "application/octet-stream" : mime;
                postIncomingEvent();
            } catch (Throwable ignored) {}
        }).start();
    }

    private String resolveName(Uri uri, String mime) {
        String name = null;
        try {
            Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && c.moveToFirst()) name = c.getString(idx);
                c.close();
            }
        } catch (Exception ignored) {}
        if (name == null || name.length() == 0) {
            name = uri.getLastPathSegment();
            if (name == null) name = "document";
        }
        if (!name.contains(".")) {
            if (mime != null && mime.contains("pdf")) name += ".pdf";
            else if (mime != null && mime.startsWith("image/")) name += "." + mime.substring(6);
        }
        name = new File(name).getName();                 // strip any path components
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");   // whitelist safe filename chars
        if (name.length() == 0) name = "document";
        return name;
    }
}
