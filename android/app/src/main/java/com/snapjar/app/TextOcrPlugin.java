package com.snapjar.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;

import androidx.core.app.NotificationCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

/**
 * Native ML Kit OCR (Latin script) for Scan-to-Text.
 *
 * tesseract.js hangs / reloads the WebView, so OCR runs natively instead: fast (<1s),
 * accurate, fully offline. JS calls recognize({ path }) for a scanned/captured file URI,
 * or recognize({ base64 }) for a gallery image, and gets back { text }.
 */
@CapacitorPlugin(name = "TextOcr")
public class TextOcrPlugin extends Plugin {

    /** Whether the app is currently on screen. Tracked here rather than asked of the WebView,
     *  because the WebView is exactly what stops answering once we're backgrounded. */
    private static volatile boolean inForeground = true;

    @Override protected void handleOnResume() { inForeground = true; }
    @Override protected void handleOnPause()  { inForeground = false; }

    @PluginMethod
    public void recognize(final PluginCall call) {
        final String path = call.getString("path");
        final String base64 = call.getString("base64");
        // Optional: post the "text is ready" notification from NATIVE code if the user has left
        // the app. Recognition runs natively and finishes fine, but the JS that would normally
        // schedule the notification is frozen while backgrounded — so it must be posted here.
        final boolean notifyOnBg = Boolean.TRUE.equals(call.getBoolean("notifyOnBackground", Boolean.FALSE));
        final String nTitle = call.getString("notifyTitle", "Text extracted! 🔍📝");
        final String nBody  = call.getString("notifyBody", "We read the whole thing so you don’t have to squint.");
        try {
            InputImage image;
            if (base64 != null && base64.length() > 0) {
                String b = base64;
                int comma = b.indexOf(',');
                if (b.startsWith("data:") && comma >= 0) b = b.substring(comma + 1);   // strip data-URL prefix
                byte[] bytes = Base64.decode(b, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bmp == null) { call.reject("Could not decode the image"); return; }
                image = InputImage.fromBitmap(bmp, 0);
            } else if (path != null && path.length() > 0) {
                image = InputImage.fromFilePath(getContext(), Uri.parse(path));
            } else {
                call.reject("path or base64 required");
                return;
            }
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer
                .process(image)
                .addOnSuccessListener(result -> {
                    // Reconstruct clean paragraphs: ML Kit's getText() puts a line break after EVERY
                    // detected line (choppy). Instead join wrapped lines within a block into one
                    // paragraph (de-hyphenating line-wrap hyphens), and separate blocks with a blank line.
                    StringBuilder sb = new StringBuilder();
                    for (com.google.mlkit.vision.text.Text.TextBlock block : result.getTextBlocks()) {
                        StringBuilder para = new StringBuilder();
                        for (com.google.mlkit.vision.text.Text.Line line : block.getLines()) {
                            String lt = line.getText() == null ? "" : line.getText().trim();
                            if (lt.length() == 0) continue;
                            if (para.length() > 0) {
                                if (para.charAt(para.length() - 1) == '-') para.setLength(para.length() - 1); // join hyphenated word
                                else para.append(' ');
                            }
                            para.append(lt);
                        }
                        if (para.length() == 0) continue;
                        if (sb.length() > 0) sb.append("\n\n");
                        sb.append(para);
                    }
                    String text = sb.length() > 0 ? sb.toString() : result.getText();
                    // Only speak up if they actually left — if the app is on screen they can
                    // already see the result, and a notification would be noise.
                    if (notifyOnBg && !inForeground && text != null && text.trim().length() > 0) {
                        postDoneNotification(nTitle, nBody);
                    }
                    JSObject o = new JSObject();
                    o.put("text", text);
                    call.resolve(o);
                })
                .addOnFailureListener(e -> call.reject(e.getMessage() == null ? "OCR failed" : e.getMessage()));
        } catch (Throwable t) {
            call.reject(t.getMessage() == null ? "OCR error" : t.getMessage());
        }
    }

    /* ---------------- native completion notification ----------------
       Posts on the SAME "Task updates" channel the NotificationDirector defines, so the user's
       per-channel mute choice is honoured whichever side posted it. Created here too, because
       native OCR can finish before the web layer has ever run. */
    private static final String CHANNEL_ID = "sj_task";
    private static final int    NOTIF_ID   = 4720;

    private void postDoneNotification(String title, String body) {
        try {
            Context ctx = getContext();
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Task updates", NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("When a scan, conversion or text extraction finishes");
                nm.createNotificationChannel(ch);
            }
            Intent open = new Intent(ctx, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getActivity(ctx, 4720, open, flags);

            Notification n = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_search)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .build();
            nm.notify(NOTIF_ID, n);
        } catch (Throwable ignored) { /* never let a notification failure break OCR */ }
    }
}
