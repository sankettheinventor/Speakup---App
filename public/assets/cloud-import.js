/* ===== Thor Tools — Google Drive + Dropbox import =====
   Auto-adds "Google Drive" and "Dropbox" buttons to every upload box.
   The chosen file is fetched INTO the browser and handed to the tool exactly
   like a normal upload — processed locally, never uploaded to us.

   To enable, paste your free keys below:
     • Dropbox app key:  https://www.dropbox.com/developers/apps  (enable Chooser)
     • Google API key + OAuth client ID:  https://console.cloud.google.com
       (enable "Google Picker API"; add your live domain to authorized origins)
   Leave a key empty to keep that button hidden. Buttons only appear once keys are set. */
(function () {
  var CFG = {
    dropboxAppKey: '',     // e.g. 'abc123def456'
    googleApiKey: '',      // e.g. 'AIza...'
    googleClientId: '',    // e.g. '12345-xyz.apps.googleusercontent.com'
  };
  var hasDbx = !!CFG.dropboxAppKey;
  var hasGoogle = !!(CFG.googleApiKey && CFG.googleClientId);
  if (!hasDbx && !hasGoogle) return; // nothing configured → stay invisible

  function loadScript(src, cb, attrs) {
    var s = document.createElement('script'); s.src = src; s.async = true;
    if (attrs) Object.keys(attrs).forEach(function (k) { s.setAttribute(k, attrs[k]); });
    s.onload = cb; document.body.appendChild(s);
  }
  // Hand a File to a tool's hidden <input type=file> exactly like a real upload
  function deliver(input, file) {
    try {
      var dt = new DataTransfer(); dt.items.add(file); input.files = dt.files;
      input.dispatchEvent(new Event('change', { bubbles: true }));
    } catch (e) { alert('Your browser blocked this import. Try the regular Browse button.'); }
  }
  function fetchInto(url, name, headers, input) {
    fetch(url, headers ? { headers: headers } : undefined)
      .then(function (r) { return r.blob(); })
      .then(function (b) { deliver(input, new File([b], name || 'file', { type: b.type })); })
      .catch(function () { alert('Could not fetch that file.'); });
  }

  function pickDropbox(input) {
    function go() {
      window.Dropbox.choose({
        linkType: 'direct', multiselect: false,
        success: function (files) { fetchInto(files[0].link, files[0].name, null, input); },
      });
    }
    if (window.Dropbox) return go();
    loadScript('https://www.dropbox.com/static/api/2/dropins.js', go,
      { id: 'dropboxjs', 'data-app-key': CFG.dropboxAppKey });
  }

  function pickGoogle(input) {
    loadScript('https://apis.google.com/js/api.js', function () {
      gapi.load('picker', function () {
        loadScript('https://accounts.google.com/gsi/client', function () {
          google.accounts.oauth2.initTokenClient({
            client_id: CFG.googleClientId,
            scope: 'https://www.googleapis.com/auth/drive.readonly',
            callback: function (resp) {
              if (!resp.access_token) return;
              var token = resp.access_token;
              var view = new google.picker.DocsView(google.picker.ViewId.DOCS).setIncludeFolders(false);
              new google.picker.PickerBuilder().setOAuthToken(token).setDeveloperKey(CFG.googleApiKey)
                .addView(view).setCallback(function (data) {
                  if (data.action === google.picker.Action.PICKED) {
                    var doc = data.docs[0];
                    fetchInto('https://www.googleapis.com/drive/v3/files/' + doc.id + '?alt=media',
                      doc.name, { Authorization: 'Bearer ' + token }, input);
                  }
                }).build().setVisible(true);
            },
          }).requestAccessToken();
        });
      });
    });
  }

  function makeBtn(label, icon, onClick) {
    var b = document.createElement('button');
    b.type = 'button'; b.className = 'cloud-btn';
    b.innerHTML = icon + '<span>' + label + '</span>';
    b.addEventListener('click', function (e) { e.stopPropagation(); onClick(); });
    return b;
  }

  function inject() {
    var drops = document.querySelectorAll('.drop');
    Array.prototype.forEach.call(drops, function (drop) {
      var input = drop.querySelector('input[type=file]');
      if (!input || drop.querySelector('.cloud')) return;
      var row = document.createElement('div'); row.className = 'cloud';
      if (hasGoogle) row.appendChild(makeBtn('Google Drive', '📁 ', function () { pickGoogle(input); }));
      if (hasDbx) row.appendChild(makeBtn('Dropbox', '🗂️ ', function () { pickDropbox(input); }));
      drop.appendChild(row);
    });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', inject);
  else inject();
})();
