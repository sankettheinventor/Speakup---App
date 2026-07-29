/* =============================================================================
   SnapJar — NotificationDirector v1.0
   -----------------------------------------------------------------------------
   Design law: a TOOL may only speak when it has something FOR the user — an
   unfinished task, a finished job, a saved thing. Humor is seasoning, never the
   meal. Every message must pass the strip test: remove the joke, is it still
   worth sending? If no, it isn't in this table.

   One gatekeeper (gate()), the trigger table as data, hard caps as constitution.
   All scheduling is LOCAL (Capacitor LocalNotifications) — no FCM, no server,
   no new data collection, nothing leaves the device.
   ========================================================================== */
/* Boot is load-order independent: this file is a classic deferred script while SnapJarKit is
   emitted as a module, so the two can execute in either order. Waiting for SnapJarKit instead
   of assuming it exists (an earlier version silently no-op'd whenever it won the race). */
(function boot(tries) {
  // Wait for the Capacitor BRIDGE too, not just SnapJarKit: K.plugin() returns null until
  // window.Capacitor exists, and bailing on that null was silently disabling notifications.
  var ready = window.SnapJarKit && window.SnapJarKit.plugin && document.body
           && window.Capacitor && (window.Capacitor.Plugins || window.Capacitor.registerPlugin);
  if (!ready) {
    if ((tries || 0) > 100) return;                       // ~10s then give up quietly
    return setTimeout(function () { boot((tries || 0) + 1); }, 100);
  }
  init();
})(0);

function init() {
  'use strict';
  var K = window.SnapJarKit;
  if (!document.body || document.body.className.indexOf('is-app') < 0) {
    // web build has no notifications; still expose a no-op so callers never guard
    window.SJNotify = { onEvent: function () {}, _test: function () {}, cancel: function () {}, askPermission: function () { return Promise.resolve(false); } };
    return;
  }

  var LN = K.plugin('LocalNotifications');
  if (!LN || !LN.schedule) { window.SJNotify = { onEvent: function () {}, _test: function () {} }; return; }

  /* DEBUG builds only: exercise the REAL triggers through the REAL gate, just on compressed
     timings — no new-user grace, no 48h spacing, and delayed nags arrive in seconds instead of
     hours. Release builds always report debug:false, so the full constitution stands for users. */
  var DEBUG = false;
  try {
    var BG = K.plugin('BgTts');
    if (BG && BG.isDebug) BG.isDebug().then(function (r) {
      if (r && r.debug) {
        DEBUG = true;
        CAPS.NEW_USER_GRACE_DAYS = 0;
        CAPS.MIN_HOURS_BETWEEN = 0;
        CAPS.MAX_PER_DAY = 99;
        CAPS.MAX_PER_WEEK = 99;
      }
    }).catch(function () {});
  } catch (e) {}

  /* ---------------- constitution ---------------- */
  var CAPS = {
    /* SnapJar is partly a reading/listening product, not only a utility — listening has a daily
       rhythm (the commute), so a value-backed daily prompt is legitimate here in a way it wouldn't
       be for a converter. Still one per day maximum, still never two in a row. */
    MAX_PER_WEEK: 6,
    MAX_PER_DAY: 1,
    MIN_HOURS_BETWEEN: 18,
    QUIET_START: 22, QUIET_END: 9,          // 10pm–9am local, always silent
    DISCOVERY_MAX_PER_MONTH: 1,
    REENGAGEMENT_MAX_PER_LAPSE: 2,
    NEW_USER_GRACE_DAYS: 2
  };

  var CH = { TASK: 'sj_task', TIPS: 'sj_tips', REMIND: 'sj_remind' };

  /* ---------------- tiny persistent store ---------------- */
  var SKEY = 'sj_notif_state';
  function state() {
    try { return JSON.parse(localStorage.getItem(SKEY) || '{}'); } catch (e) { return {}; }
  }
  function save(s) { try { localStorage.setItem(SKEY, JSON.stringify(s)); } catch (e) {} }
  function firstSeen() {
    var s = state();
    if (!s.installed) { s.installed = Date.now(); save(s); }
    return s.installed;
  }
  function log(id) {
    var s = state();
    s.sent = (s.sent || []).filter(function (t) { return Date.now() - t < 30 * 864e5; });
    s.sent.push(Date.now());
    s.last = Date.now();
    s.byId = s.byId || {};
    s.byId[id] = (s.byId[id] || 0) + 1;
    s.rot = s.rot || {};
    s.rot[id] = (s.rot[id] || 0) + 1;                 // copy rotation cursor
    save(s);
  }

  /* ---------------- the gate ---------------- */
  function inQuietHours(d) {
    var h = (d || new Date()).getHours();
    return h >= CAPS.QUIET_START || h < CAPS.QUIET_END;
  }
  function withinCaps(cand) {
    var s = state(), sent = s.sent || [], now = Date.now();
    if (sent.filter(function (t) { return now - t < 864e5; }).length >= CAPS.MAX_PER_DAY) return false;
    if (sent.filter(function (t) { return now - t < 7 * 864e5; }).length >= CAPS.MAX_PER_WEEK) return false;
    if (s.last && (now - s.last) < CAPS.MIN_HOURS_BETWEEN * 36e5) return false;
    // REENGAGEMENT_MAX_PER_LAPSE was declared in the constitution but never actually enforced.
    // Without this, the "come back" pings could repeat every lapse cycle instead of stopping
    // after two — which is precisely the behaviour that gets a utility app muted.
    if (cand && cand.priority === 5) {
      var used = (s.lapseSent || 0);
      if (used >= CAPS.REENGAGEMENT_MAX_PER_LAPSE) return false;
    }
    return true;
  }
  function newUserGrace() { return (Date.now() - firstSeen()) < CAPS.NEW_USER_GRACE_DAYS * 864e5; }

  function permGranted() {
    if (!LN.checkPermissions) return Promise.resolve(true);
    return LN.checkPermissions().then(function (p) { return !!(p && p.display === 'granted'); }).catch(function () { return false; });
  }

  /* ---------------- copy table ----------------
     Voice: SnapJar is a devoted filing clerk; the DOCUMENTS are the dramatic ones.
     Never mock the user. Title <= 35 chars, body <= 90. */
  var T = {
    CONVERSION_DONE: {
      id: 'N1', priority: 0, channel: CH.TASK,
      variants: [
        { t: 'Your PDF is ready! 🎉📄', b: 'It came out beautifully. The other files are jealous.' },
        { t: 'Conversion done ✅', b: 'One file walked in, a PDF walked out. Come see the transformation ✨' }
      ]
    },
    OCR_DONE: {
      id: 'N2', priority: 0, channel: CH.TASK,
      variants: [
        { t: 'Text extracted! 🔍📝', b: 'We read the whole thing so you don’t have to squint. Results inside.' }
      ]
    },
    SCAN_UNSAVED: {
      id: 'N3', priority: 1, channel: CH.TASK, delayMin: 120,
      variants: [
        { t: 'Your scan is waiting 📄🪑', b: 'It’s been very patient. Tap to save it before it files a complaint.' },
        { t: 'Unsaved scan alert 📸', b: 'One document, all dressed up, nowhere to go. Finish saving it?' }
      ]
    },
    EDIT_ABANDONED: {
      id: 'N4', priority: 1, channel: CH.TASK, delayMin: 180,
      variants: [
        { t: 'You left mid-edit ✏️', b: 'Your PDF is half-finished and a little confused. Want to wrap it up?' }
      ]
    },
    HIGHLIGHT_MILESTONE: {
      id: 'N8', priority: 3, channel: CH.TIPS,
      variants: [
        { t: 'Your first highlight! 🎉🖍️', b: 'It’s framed and hanging in your Highlights. Visit any time.' },
        { t: '{n}th highlight saved! 🖍️🏆', b: 'You’re officially a serious reader. The yellow marker is proud.' }
      ]
    },
    /* The daily driver. Backed by real persisted position (sj_reading), so the promise is true:
       tapping it resumes the actual book at the actual page. Framed as resuming, never as
       "come back" — the value is the listening, not the app. */
    BOOK_WAITING: {
      id: 'N12', priority: 3, channel: CH.TIPS,
      variants: [
        { t: '{doc} is waiting 🎧', b: 'You’re {pct}% through. Listen on the way — no need to read a word.' },
        { t: 'Pick up where you left off 📖', b: '{doc}, page {page}. Press play and let SnapJar read it to you.' },
        { t: 'Your commute, sorted 🎧', b: '{doc} is {pct}% done. Hands-free from here.' }
      ]
    },
    HIGHLIGHT_RESURFACE: {
      id: 'N7', priority: 3, channel: CH.TIPS,
      variants: [
        { t: 'Remember this? 🖍️✨', b: 'You highlighted something smart in {doc}. Worth a re-read.' },
        { t: 'Your highlights called 📞', b: 'They said, and we quote: “read us again.” Documents are dramatic.' }
      ]
    },
    LAPSE_7D: {
      id: 'N10', priority: 5, channel: CH.REMIND,
      variants: [
        { t: 'Your documents miss you 📄', b: 'Okay, documents can’t miss people. But your saved work is safe here.' }
      ]
    },
    LAPSE_21D: {
      id: 'N11', priority: 5, channel: CH.REMIND,
      variants: [
        { t: 'We’ll keep the lights on 💡', b: 'Your files are safe with us. One tap away whenever you need us. 📎' }
      ]
    }
  };

  /* deep links — every notification must land EXACTLY on its promise */
  var LINK = {
    N1: function (x) { return x.recentId ? '/tools/pdf-studio.html?recent=' + encodeURIComponent(x.recentId) : '/recent.html'; },
    N2: function (x) { return x.recentId ? '/tools/pdf-studio.html?recent=' + encodeURIComponent(x.recentId) : '/recent.html'; },
    N3: function () { return '/tools/scanner.html'; },
    N4: function (x) { return x.recentId ? '/tools/pdf-studio.html?recent=' + encodeURIComponent(x.recentId) : '/recent.html'; },
    N12: function (x) { return x.recentId ? '/tools/pdf-studio.html?recent=' + encodeURIComponent(x.recentId) + '&listen=1' : '/recent.html'; },
    N7: function (x) { return x.recentId ? '/tools/pdf-studio.html?recent=' + encodeURIComponent(x.recentId) + '&hl=1' : '/highlights.html'; },
    N8: function () { return '/highlights.html'; },
    N10: function () { return '/'; },
    N11: function () { return '/'; }
  };

  function fill(str, vars) {
    return String(str).replace(/\{(\w+)\}/g, function (m, k) {
      var v = vars && vars[k];
      return (v === undefined || v === null || v === '') ? '' : String(v);
    }).replace(/\s{2,}/g, ' ').trim();
  }
  function pickCopy(cand, vars) {
    var s = state(), i = (s.rot && s.rot[cand.id]) || 0;
    var v = cand.variants[i % cand.variants.length];
    // if a template needs a variable we don't have, fall back to a variant that doesn't
    if (/\{(\w+)\}/.test(v.t + v.b)) {
      var needed = (v.t + v.b).match(/\{(\w+)\}/g).map(function (x) { return x.slice(1, -1); });
      var missing = needed.some(function (k) { return !vars || vars[k] === undefined || vars[k] === '' || vars[k] === 0; });
      if (missing) {
        var alt = cand.variants.filter(function (x) { return !/\{(\w+)\}/.test(x.t + x.b); })[0];
        if (alt) v = alt; else return null;            // never send "your 0 highlights"
      }
    }
    return { t: fill(v.t, vars), b: fill(v.b, vars) };
  }

  /* Stable numeric id per trigger, so a scheduled notification can be CANCELLED when the
     user completes the task it was going to nag about (e.g. they saved the scan after all).
     'N3' -> 9003. */
  function idOf(cand) { return 9000 + (parseInt(String(cand.id).replace(/\D/g, ''), 10) || 0); }
  function send(cand, vars, whenMs) {
    var copy = pickCopy(cand, vars);
    if (!copy) return Promise.resolve(false);
    var link = LINK[cand.id] ? LINK[cand.id](vars || {}) : '/';
    var opts = {
      id: idOf(cand),
      title: copy.t,
      body: copy.b,
      channelId: cand.channel,
      smallIcon: 'ic_stat_icon_config_sample',
      extra: { sjLink: link, sjId: cand.id }
    };
    if (whenMs && whenMs > 0) opts.schedule = { at: new Date(Date.now() + whenMs), allowWhileIdle: false };
    return LN.schedule({ notifications: [opts] }).then(function () { log(cand.id); return true; }).catch(function () { return false; });
  }

  /* ---------------- public API ---------------- */
  var D = {
    /* Fire a trigger. P0 (task complete) is exempt from the weekly cap — the user asked
       for that work and is waiting on it — but still respects quiet hours only in the
       sense that it is immediate and expected. Everything else must pass the gate. */
    onEvent: function (event, vars) {
      var cand = T[event];
      if (!cand) return Promise.resolve(false);
      vars = vars || {};
      return permGranted().then(function (ok) {
        if (!ok) return false;
        if (cand.priority === 0) {
          if (document.visibilityState === 'visible') return false;   // they're looking at it already
          return send(cand, vars, 0);
        }
        if (newUserGrace() || (inQuietHours() && !DEBUG) || !withinCaps(cand)) return false;   // drop, never queue
        if (cand.priority === 5) { var st = state(); st.lapseSent = (st.lapseSent || 0) + 1; save(st); }
        var delay = (cand.delayMin || 0) * 60000;
        if (DEBUG && delay > 0) delay = 15000;                                 // hours -> 15s for QA
        return send(cand, vars, delay);
      });
    },
    /* contextual permission ask — at the first moment it obviously pays off */
    askPermission: function () {
      if (!LN.requestPermissions) return Promise.resolve(false);
      return LN.checkPermissions().then(function (p) {
        if (p && p.display === 'granted') return true;
        return LN.requestPermissions().then(function (r) { return !!(r && r.display === 'granted'); });
      }).catch(function () { return false; });
    },
    /* The user finished the thing we were going to nag about — pull the scheduled nag.
       A notification about an already-completed task is the fastest way to get muted. */
    cancel: function (event) {
      var cand = T[event]; if (!cand || !LN.cancel) return Promise.resolve();
      return LN.cancel({ notifications: [{ id: idOf(cand) }] }).catch(function () {});
    },
    /* Opening the app ends the lapse, so the two-message re-engagement budget resets for the
       NEXT lapse rather than being spent forever. */
    markActive: function () { var s = state(); s.lastOpen = Date.now(); s.lapseSent = 0; save(s); },

    /* ===== "Your book is waiting" =====
       JS cannot run while the app is closed, so this can't be evaluated later — it must be
       ARMED as the user leaves and CANCELLED when they come back. Fires tomorrow morning only
       if they left a book genuinely unfinished. */
    armBookReminder: function () {
      var cand = T.BOOK_WAITING, book = null;
      try {
        var all = JSON.parse(localStorage.getItem('sj_reading') || '[]');
        book = all.filter(function (x) { return x && x.pct > 3 && x.pct < 95; })[0];   // started, not finished
      } catch (e) { return; }
      if (!book) return;
      if (newUserGrace()) return;
      // next morning at 09:30 local — after quiet hours, before the commute
      var at = new Date(); at.setDate(at.getDate() + 1); at.setHours(9, 30, 0, 0);
      var delay = at.getTime() - Date.now();
      if (delay < 36e5) return;                       // too close to be a "tomorrow" nudge
      var s = state(), sent = s.sent || [], now = Date.now();
      if (sent.filter(function (t) { return now - t < 864e5; }).length >= CAPS.MAX_PER_DAY) return;
      var name = String(book.name || '').replace(/\.[^.]+$/, '');
      if (name.length > 20) name = name.slice(0, 19) + '…';
      permGranted().then(function (ok) {
        if (!ok) return;
        send(cand, { doc: name, pct: book.pct, page: book.page, recentId: book.docId }, delay);
      });
    },
    /* They came back on their own — the reminder would be redundant, so withdraw it. */
    cancelBookReminder: function () {
      if (!LN.cancel) return;
      try { LN.cancel({ notifications: [{ id: idOf(T.BOOK_WAITING) }] }); } catch (e) {}
    },
    _state: state,
    /* dev-only: fire a real notification now, bypassing caps, to verify plumbing */
    _test: function (event, vars) {
      var cand = T[event || 'CONVERSION_DONE'];
      return send(cand, vars || {}, 0);
    }
  };

  /* three channels so users can mute tips while keeping task alerts — this single
     decision is what keeps the notification-block rate down. */
  function ensureChannels() {
    if (!LN.createChannel) return;
    [
      { id: CH.TASK, name: 'Task updates', description: 'When a scan, conversion or text extraction finishes', importance: 4 },
      { id: CH.TIPS, name: 'Tips & highlights', description: 'Occasional useful tips and your saved highlights', importance: 3 },
      { id: CH.REMIND, name: 'Reminders', description: 'Rare reminders that your saved work is still here', importance: 2 }
    ].forEach(function (c) { try { LN.createChannel(c); } catch (e) {} });
  }

  /* tapping a notification lands on its promise; if the target is gone, go somewhere sane */
  function wireTaps() {
    if (!LN.addListener) return;
    try {
      LN.addListener('localNotificationActionPerformed', function (ev) {
        var ex = ev && ev.notification && ev.notification.extra;
        if (!ex || !ex.sjLink) return;
        location.href = ex.sjLink;
      });
    } catch (e) {}
  }

  ensureChannels();
  wireTaps();
  firstSeen();
  D.markActive();
  D.cancelBookReminder();          // they're here now; withdraw any armed reminder

  /* Arm on the way out. visibilitychange is the one handler proven to still run as the app
     leaves the screen (it's what makes the read-aloud handoff work), so it's where anything
     that must outlive the session has to be scheduled. */
  document.addEventListener('visibilitychange', function () {
    if (document.hidden) D.armBookReminder();
    else { D.markActive(); D.cancelBookReminder(); }
  });

  window.SJNotify = D;
}
