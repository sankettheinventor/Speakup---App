package com.snapjar.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;

/**
 * Native HEIC/HEIF → JPEG decode.
 *
 * The web decoder (heic2any → libheif) compiles glue with new Function(), which the app's
 * Content-Security-Policy blocks (script-src has only 'wasm-unsafe-eval', not 'unsafe-eval'),
 * so HEIC conversion throws in the packaged app. Android's own BitmapFactory decodes HEIF/HEIC
 * natively from API 28 (Android 9) — every target device — with no eval, no CSP hole, no wasm
 * payload. JS calls decode({ base64, quality }) and gets back { base64 } (a JPEG data-URL body).
 *
 * On API < 28 (or a codec that can't decode the file), decodeByteArray returns null and we
 * reject with a clear reason so the JS layer can fall back / skip the file gracefully.
 */
@CapacitorPlugin(name = "Heic")
public class HeicPlugin extends Plugin {

    @PluginMethod
    public void decode(final PluginCall call) {
        final String base64 = call.getString("base64");
        int q = call.getInt("quality", 85);
        if (q < 1) q = 1; if (q > 100) q = 100;
        if (base64 == null || base64.length() == 0) { call.reject("base64 required"); return; }
        try {
            String b = base64;
            int comma = b.indexOf(',');
            if (b.startsWith("data:") && comma >= 0) b = b.substring(comma + 1);   // strip data-URL prefix
            byte[] bytes = Base64.decode(b, Base64.DEFAULT);

            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp == null) {
                // Not decodable here — pre-API-28 device or an unsupported/corrupt file.
                call.reject("HEIC_DECODE_UNSUPPORTED");
                return;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, q, out);
            int w = bmp.getWidth(), h = bmp.getHeight();
            bmp.recycle();
            String jpeg = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);

            JSObject o = new JSObject();
            o.put("base64", jpeg);   // raw JPEG base64 (no data: prefix)
            o.put("width", w);
            o.put("height", h);
            call.resolve(o);
        } catch (Throwable t) {
            call.reject(t.getMessage() == null ? "HEIC decode error" : t.getMessage());
        }
    }
}
