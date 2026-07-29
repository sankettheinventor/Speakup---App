package com.snapjar.app;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * BgTts — background read-aloud bridge.
 *
 * When the reader is playing and the app goes to the background, JS hands the remaining
 * sentences here; we spin up {@link TtsService} (a foreground service) which owns the native
 * TTS engine and keeps reading with the screen off. On return to the foreground, JS calls
 * stop() to reclaim the position and resume the on-screen, highlighted playback.
 *
 * Progress is pushed back to JS via the "bgTtsEvent" listener ({ state, index }).
 */
@CapacitorPlugin(
    name = "BgTts",
    permissions = {
        @Permission(alias = "notifications", strings = { Manifest.permission.POST_NOTIFICATIONS })
    }
)
public class TtsPlugin extends Plugin {

    private static TtsPlugin self;

    @Override public void load() { self = this; }

    /** The "Now Playing" card can't render at all unless POST_NOTIFICATIONS is granted
     *  (Android 13+), so the reader asks for it before the first background handoff. */
    /** Lets the web layer know it's running in a DEBUG build, so QA can exercise the real
     *  notification triggers on compressed timings. Release builds always report false, so the
     *  full frequency constitution is never relaxed for real users. */
    @PluginMethod
    public void isDebug(PluginCall call) {
        JSObject o = new JSObject();
        // resolve BuildConfig reflectively — the app's BuildConfig class isn't on this
        // plugin's compile classpath in all Gradle configurations
        boolean dbg = false;
        try {
            Class<?> bc = Class.forName(getContext().getPackageName() + ".BuildConfig");
            dbg = bc.getField("DEBUG").getBoolean(null);
        } catch (Throwable ignored) {}
        o.put("debug", dbg);
        call.resolve(o);
    }

    @PluginMethod
    public void ensurePermission(PluginCall call) {
        if (Build.VERSION.SDK_INT < 33) { resolveGranted(call, true); return; }
        if (getPermissionState("notifications") == PermissionState.GRANTED) { resolveGranted(call, true); return; }
        requestPermissionForAlias("notifications", call, "notifPermCallback");
    }

    @PermissionCallback
    private void notifPermCallback(PluginCall call) {
        resolveGranted(call, getPermissionState("notifications") == PermissionState.GRANTED);
    }

    private void resolveGranted(PluginCall call, boolean granted) {
        JSObject o = new JSObject();
        o.put("granted", granted);
        call.resolve(o);
    }

    /** Fallback when the system won't prompt again (already denied, or the OEM defaults
     *  notifications off): drop the user straight on SnapJar's notification settings. */
    @PluginMethod
    public void openNotificationSettings(PluginCall call) {
        try {
            Intent i;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
            } else {
                i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", getContext().getPackageName(), null));
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Throwable t) { call.reject("could not open settings"); }
    }

    /** Called from TtsService on the engine thread to notify the WebView of progress. */
    static void emit(String state, int index) {
        TtsPlugin p = self;
        if (p == null) return;
        JSObject o = new JSObject();
        o.put("state", state);
        o.put("index", index);
        p.notifyListeners("bgTtsEvent", o, true);
    }

    @PluginMethod
    public void start(PluginCall call) {
        JSArray arr = call.getArray("sentences");
        List<String> list = new ArrayList<>();
        try {
            if (arr != null) for (int i = 0; i < arr.length(); i++) list.add(String.valueOf(arr.get(i)));
        } catch (Exception e) { call.reject("bad sentences"); return; }
        if (list.isEmpty()) { call.reject("no sentences"); return; }

        TtsService.PENDING    = list;
        TtsService.startIndex = call.getInt("index", 0);
        TtsService.rate       = call.getFloat("rate", 1f);
        TtsService.pitch      = call.getFloat("pitch", 1f);
        TtsService.voiceURI   = call.getString("voiceURI", null);
        TtsService.lang       = call.getString("lang", "en-US");
        TtsService.docTitle   = call.getString("title", "SnapJar");
        TtsService.currentIndex = TtsService.startIndex;
        TtsService.isDone = false;

        Intent i = new Intent(getContext(), TtsService.class).setAction(TtsService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getContext().startForegroundService(i);
            else getContext().startService(i);
        } catch (Throwable t) { call.reject("could not start background reading"); return; }
        call.resolve();
    }

    @PluginMethod
    public void pause(PluginCall call)  { send(TtsService.ACTION_PAUSE);  call.resolve(); }

    @PluginMethod
    public void resume(PluginCall call) { send(TtsService.ACTION_RESUME); call.resolve(); }

    /** Stop background reading and hand the current position back so the in-app reader can
     *  resume exactly where the voice left off. */
    @PluginMethod
    public void stop(PluginCall call) {
        JSObject o = new JSObject();
        o.put("index", TtsService.currentIndex);
        o.put("done", TtsService.isDone);
        send(TtsService.ACTION_STOP);
        call.resolve(o);
    }

    @PluginMethod
    public void state(PluginCall call) {
        JSObject o = new JSObject();
        o.put("index", TtsService.currentIndex);
        o.put("playing", TtsService.isPlaying);
        o.put("done", TtsService.isDone);
        call.resolve(o);
    }

    /** No-op when the service isn't running: startService() would otherwise CREATE it (and a
     *  TextToSpeech engine) purely to tear it down again — which happened on every page that
     *  calls ttsStop() during init. */
    private void send(String action) {
        if (!TtsService.isRunning) return;
        try { getContext().startService(new Intent(getContext(), TtsService.class).setAction(action)); }
        catch (Throwable ignored) {}
    }
}
