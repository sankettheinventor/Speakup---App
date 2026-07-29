package com.snapjar.app;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Open-with bridge: when another app sends SnapJar a PDF/image via an ACTION_VIEW
 * (tap a PDF in a file manager) or ACTION_SEND intent, MainActivity copies the file
 * into app storage and stashes its path here. The web layer polls getPending() on
 * launch (and on the 'sjIncomingFile' event) and loads the file in PDF Studio.
 */
@CapacitorPlugin(name = "IncomingFile")
public class IncomingFilePlugin extends Plugin {
    static volatile String pendingPath;
    static volatile String pendingName;
    static volatile String pendingMime;
    /** Shared plain text (Share → SnapJar from a browser/email) to be read aloud. */
    static volatile String pendingText;
    static volatile String pendingTextTitle;

    @PluginMethod
    public void getPending(PluginCall call) {
        JSObject ret = new JSObject();
        if (pendingText != null) {
            ret.put("text", pendingText);
            ret.put("title", pendingTextTitle);
            pendingText = null;
            pendingTextTitle = null;
        } else if (pendingPath != null) {
            ret.put("path", pendingPath);
            ret.put("name", pendingName);
            ret.put("mime", pendingMime);
            pendingPath = null;
            pendingName = null;
            pendingMime = null;
        } else {
            ret.put("none", true);
        }
        call.resolve(ret);
    }
}
