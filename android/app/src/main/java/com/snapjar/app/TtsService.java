package com.snapjar.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Foreground service that reads a document aloud in the BACKGROUND.
 *
 * The in-app reader (pdf-studio) drives the on-screen, highlighted playback with the web
 * TextToSpeech plugin. The moment the user leaves the app, that WebView is frozen and the
 * JS sentence loop stalls — so playback would die after the current sentence. To keep the
 * whole book reading, we hand the remaining sentences to this service, which owns the native
 * TextToSpeech engine directly and queues every sentence (QUEUE_ADD). Because it runs as a
 * foreground service it is exempt from the cached-process freezer, so the engine's
 * UtteranceProgressListener keeps advancing sentence-by-sentence with the screen off.
 *
 * A MediaSession-backed "Now Playing" notification (like Spotify) is the "app is there"
 * presence the user asked for — book title, progress through the book, Play/Pause/Stop, and
 * lock-screen + headset-button controls.
 */
public class TtsService extends Service {

    public static final String ACTION_START  = "com.snapjar.app.tts.START";
    public static final String ACTION_PAUSE  = "com.snapjar.app.tts.PAUSE";
    public static final String ACTION_RESUME = "com.snapjar.app.tts.RESUME";
    public static final String ACTION_STOP   = "com.snapjar.app.tts.STOP";

    private static final String CHANNEL_ID = "snapjar_read_aloud";
    private static final int    NOTIF_ID   = 4711;

    // Sentences are handed over via a static field (an Intent extra would blow the ~1 MB
    // binder limit on a long book). Same process, so this is safe.
    static List<String> PENDING = new ArrayList<>();
    static int    startIndex = 0;
    static float  rate       = 1f;
    static float  pitch      = 1f;
    static String voiceURI   = null;
    static String lang       = "en-US";
    static String docTitle   = "SnapJar";

    // Live state the plugin reads back when the app returns to the foreground.
    static volatile int     currentIndex = 0;
    static volatile boolean isPlaying    = false;
    static volatile boolean isDone       = false;
    /** True only while the service is actually alive, so the plugin can skip pause/resume/stop
     *  intents entirely when nothing is playing — otherwise startService() would CREATE the
     *  service (and a TextToSpeech engine) just to immediately tear it down. */
    static volatile boolean isRunning    = false;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean startPending = false;
    private List<String> sentences = new ArrayList<>();
    private boolean paused = false;

    private MediaSessionCompat session;
    private Bitmap largeIcon;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        setupSession();
        loadLargeIcon();
        tts = new TextToSpeech(getApplicationContext(), status -> {
            ttsReady = (status == TextToSpeech.SUCCESS);
            if (ttsReady) {
                tts.setOnUtteranceProgressListener(progress);
                if (startPending) { startPending = false; beginSpeaking(); }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_PAUSE.equals(action))       { doPause(); return START_NOT_STICKY; }
        if (ACTION_RESUME.equals(action))      { doResume(); return START_NOT_STICKY; }
        if (ACTION_STOP.equals(action))        { doStop(); return START_NOT_STICKY; }

        // ACTION_START (or a bare start): pick up the pending sentence list + config.
        sentences = new ArrayList<>(PENDING);
        currentIndex = Math.max(0, Math.min(startIndex, Math.max(0, sentences.size() - 1)));
        isDone = false; paused = false; isRunning = true;
        updateMetadata();
        updatePlaybackState(true);
        startForegroundSafely(buildNotification(true));
        if (ttsReady) beginSpeaking(); else startPending = true;
        return START_NOT_STICKY;
    }

    /** Configure voice/rate, then queue every remaining sentence so the native engine reads
     *  straight through without needing any JS callbacks between sentences. */
    private void beginSpeaking() {
        if (sentences.isEmpty()) { emit("done", currentIndex); doStop(); return; }
        try { tts.setSpeechRate(rate > 0 ? rate : 1f); } catch (Throwable ignored) {}
        try { tts.setPitch(pitch > 0 ? pitch : 1f); } catch (Throwable ignored) {}
        applyVoice();
        isPlaying = true; paused = false;
        for (int i = currentIndex; i < sentences.size(); i++) {
            int mode = (i == currentIndex) ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            speakOne(sentences.get(i), i, mode);
        }
        updatePlaybackState(true);
        updateNotification();
    }

    private void applyVoice() {
        try { if (lang != null) tts.setLanguage(Locale.forLanguageTag(lang.replace('_', '-'))); } catch (Throwable ignored) {}
        if (voiceURI == null || voiceURI.length() == 0) return;
        try {
            for (Voice v : tts.getVoices()) {
                if (v != null && voiceURI.equals(v.getName())) { tts.setVoice(v); break; }
            }
        } catch (Throwable ignored) {}
    }

    private void speakOne(String text, int idx, int mode) {
        Bundle params = new Bundle();
        try { tts.speak(text == null ? "" : text, mode, params, String.valueOf(idx)); } catch (Throwable ignored) {}
    }

    private final UtteranceProgressListener progress = new UtteranceProgressListener() {
        @Override public void onStart(String utteranceId) {
            try { currentIndex = Integer.parseInt(utteranceId); } catch (Exception ignored) {}
            isPlaying = true; paused = false;
            updateMetadata();
            updatePlaybackState(true);
            updateNotification();
            emit("index", currentIndex);
        }
        @Override public void onDone(String utteranceId) {
            int idx = currentIndex;
            try { idx = Integer.parseInt(utteranceId); } catch (Exception ignored) {}
            if (idx >= sentences.size() - 1) {   // finished the last sentence → whole doc read
                isPlaying = false; isDone = true;
                emit("done", idx);
                doStop();
            }
        }
        @Override public void onError(String utteranceId) { /* skip; the next utterance keeps the queue moving */ }
    };

    private void doPause() {
        paused = true; isPlaying = false;
        try { tts.stop(); } catch (Throwable ignored) {}   // stop() clears the queue; we re-queue on resume
        updatePlaybackState(false);
        updateNotification();
        emit("paused", currentIndex);
    }

    private void doResume() {
        if (!ttsReady) return;
        paused = false;
        for (int i = currentIndex; i < sentences.size(); i++) {   // re-queue from where we paused
            int mode = (i == currentIndex) ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            speakOne(sentences.get(i), i, mode);
        }
        isPlaying = true;
        updatePlaybackState(true);
        updateNotification();
        emit("resumed", currentIndex);
    }

    private void doStop() {
        isPlaying = false; isRunning = false;
        try { if (tts != null) tts.stop(); } catch (Throwable ignored) {}
        updatePlaybackState(false);
        stopForeground(true);
        stopSelf();
        emit("stopped", currentIndex);
    }

    @Override
    public void onDestroy() {
        isPlaying = false; isRunning = false;
        try { if (tts != null) { tts.stop(); tts.shutdown(); } } catch (Throwable ignored) {}
        try { if (session != null) { session.setActive(false); session.release(); } } catch (Throwable ignored) {}
        super.onDestroy();
    }

    /* ---------------- media session ---------------- */

    private void setupSession() {
        try {
            session = new MediaSessionCompat(this, "SnapJarReadAloud");
            session.setCallback(new MediaSessionCompat.Callback() {
                @Override public void onPlay()  { doResume(); }
                @Override public void onPause() { doPause(); }
                @Override public void onStop()  { doStop(); }
                @Override public void onSkipToNext()     { /* reserved */ }
                @Override public void onSkipToPrevious() { /* reserved */ }
            });
            session.setActive(true);
        } catch (Throwable ignored) {}
    }

    private void loadLargeIcon() {
        try {
            Drawable d = getApplicationInfo().loadIcon(getPackageManager());
            if (d instanceof BitmapDrawable) { largeIcon = ((BitmapDrawable) d).getBitmap(); return; }
            if (d != null) {
                int w = Math.max(1, d.getIntrinsicWidth()), h = Math.max(1, d.getIntrinsicHeight());
                Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                android.graphics.Canvas c = new android.graphics.Canvas(b);
                d.setBounds(0, 0, w, h); d.draw(c); largeIcon = b;
            }
        } catch (Throwable t) {
            try { largeIcon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher); } catch (Throwable ignored) {}
        }
    }

    private void updateMetadata() {
        if (session == null) return;
        try {
            MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, docTitle)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "SnapJar · Reading aloud")
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, sentenceAt(currentIndex))
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, Math.max(1, sentences.size()));
            if (largeIcon != null) {
                b.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, largeIcon);
                b.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, largeIcon);
            }
            session.setMetadata(b.build());
        } catch (Throwable ignored) {}
    }

    private void updatePlaybackState(boolean playing) {
        if (session == null) return;
        try {
            long actions = PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE
                    | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_STOP;
            PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                    .setActions(actions)
                    .setState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                              currentIndex, playing ? 1f : 0f)
                    .build();
            session.setPlaybackState(state);
            session.setActive(true);
        } catch (Throwable ignored) {}
    }

    /* ---------------- notification ---------------- */

    private String sentenceAt(int i) {
        if (i >= 0 && i < sentences.size()) return sentences.get(i);
        return "Reading aloud…";
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Read aloud", NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("Shows the book SnapJar is reading aloud");
                ch.setShowBadge(false);
                ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                nm.createNotificationChannel(ch);
            }
        }
    }

    private PendingIntent actionIntent(String action) {
        Intent i = new Intent(this, TtsService.class).setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, action.hashCode(), i, flags);
    }

    private PendingIntent contentIntent() {
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(this, 0, i, flags);
    }

    private Notification buildNotification(boolean playing) {
        NotificationCompat.Action pauseResume = playing
                ? new NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause", actionIntent(ACTION_PAUSE))
                : new NotificationCompat.Action(android.R.drawable.ic_media_play, "Play", actionIntent(ACTION_RESUME));
        NotificationCompat.Action stop =
                new NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel, "Stop", actionIntent(ACTION_STOP));

        MediaStyle style = new MediaStyle()
                .setShowActionsInCompactView(0, 1)
                .setShowCancelButton(true)
                .setCancelButtonIntent(actionIntent(ACTION_STOP));
        if (session != null) style.setMediaSession(session.getSessionToken());

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)   // white speaker glyph in the status bar
                .setContentTitle(docTitle)
                .setContentText(sentenceAt(currentIndex))
                .setSubText("SnapJar · Reading aloud")
                .setLargeIcon(largeIcon)
                .setStyle(style)
                .setOnlyAlertOnce(true)
                .setOngoing(playing)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(contentIntent())
                .setDeleteIntent(actionIntent(ACTION_STOP))
                .addAction(pauseResume)
                .addAction(stop);
        return b.build();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(isPlaying && !paused));
    }

    private void startForegroundSafely(Notification n) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Throwable t) {
            try { startForeground(NOTIF_ID, n); } catch (Throwable ignored) {}
        }
    }

    private void emit(String state, int index) {
        try { TtsPlugin.emit(state, index); } catch (Throwable ignored) {}
    }
}
