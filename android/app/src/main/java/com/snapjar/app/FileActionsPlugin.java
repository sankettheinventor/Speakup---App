package com.snapjar.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.ActivityResult;
import androidx.core.content.FileProvider;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Secure file hand-off. Files live in APP-PRIVATE storage; these methods let the user OPEN a file
 * in another app (temporary FileProvider grant + ACTION_VIEW) or EXPORT it to a location they pick
 * (SAF ACTION_CREATE_DOCUMENT). Nothing is ever placed in world-readable shared storage implicitly.
 */
@CapacitorPlugin(name = "FileActions")
public class FileActionsPlugin extends Plugin {

    private static File resolve(String s) {
        if (s == null) return null;
        if (s.startsWith("file://")) return new File(Uri.parse(s).getPath());
        return new File(s);
    }

    private static void copy(File a, File b) throws Exception {
        InputStream in = new FileInputStream(a);
        OutputStream out = new FileOutputStream(b);
        try { byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) > 0) out.write(buf, 0, n); }
        finally { try { out.close(); } catch (Exception e) {} try { in.close(); } catch (Exception e) {} }
    }

    /** Open an app-private file in another app via a TEMPORARY read grant (revoked when the task ends). */
    @PluginMethod
    public void openWith(PluginCall call) {
        String uriStr = call.getString("uri");
        String mime = call.getString("mime", "*/*");
        String name = call.getString("name", "file");
        try {
            File src = resolve(uriStr);
            if (src == null || !src.exists()) { call.reject("file not found"); return; }
            File dir = new File(getContext().getCacheDir(), "shares");
            if (!dir.exists()) dir.mkdirs();
            // clear stale hand-off copies so plaintext doesn't accumulate in the cache
            File[] stale = dir.listFiles(); if (stale != null) for (File s : stale) { try { s.delete(); } catch (Throwable t) {} }
            // Never trust a JS-supplied filename: strip any path components, then verify the
            // resolved target really is inside the shares dir. Without this a name like
            // "../../databases/x" writes outside the cache — and the copy happens BEFORE
            // FileProvider would have rejected it. (Same guard as MainActivity.handleIncoming.)
            String safeName = new File(name).getName().replaceAll("[^A-Za-z0-9._-]", "_");
            if (safeName.length() == 0 || safeName.equals(".") || safeName.equals("..")) safeName = "file";
            File out = new File(dir, safeName);
            if (!out.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)) { call.reject("invalid name"); return; }
            copy(src, out);
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", out);
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(uri, mime);
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(view, "Open with");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getContext().startActivity(chooser);
            call.resolve();
        } catch (Throwable t) {
            call.reject(t.getMessage() == null ? "could not open" : t.getMessage());
        }
    }

    /** Export an app-private file to a user-chosen destination (Downloads/Drive/…) via the OS picker. */
    @PluginMethod
    public void saveToDevice(PluginCall call) {
        String mime = call.getString("mime", "application/octet-stream");
        String name = call.getString("name", "file");
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(mime);
            intent.putExtra(Intent.EXTRA_TITLE, name);
            startActivityForResult(call, intent, "saveResult");
        } catch (Throwable t) {
            call.reject(t.getMessage() == null ? "could not open picker" : t.getMessage());
        }
    }

    @ActivityCallback
    private void saveResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        Intent data = result.getData();
        if (result.getResultCode() != Activity.RESULT_OK || data == null || data.getData() == null) {
            call.reject("cancelled");
            return;
        }
        String uriStr = call.getString("uri");
        InputStream in = null; OutputStream os = null;
        try {
            File src = resolve(uriStr);
            if (src == null || !src.exists()) { call.reject("file not found"); return; }
            in = new FileInputStream(src);
            os = getContext().getContentResolver().openOutputStream(data.getData());
            if (os == null) { call.reject("cannot write there"); return; }
            byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            JSObject ret = new JSObject(); ret.put("saved", true); call.resolve(ret);
        } catch (Throwable t) {
            call.reject(t.getMessage() == null ? "save failed" : t.getMessage());
        } finally {
            try { if (os != null) os.close(); } catch (Exception e) {}
            try { if (in != null) in.close(); } catch (Exception e) {}
        }
    }
}
