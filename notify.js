/* AmkNotify: message center (bell), pop-up + sound for new notices, web push registration, "new version" banner.
   Independent of the page's theme: it builds its own markup and reads colours from CSS variables with fallbacks.
   Public API:  AmkNotify.onPoll(pollData)   AmkNotify.mount(container)   AmkNotify.reset()
                AmkNotify.alarmStart()       AmkNotify.alarmStop()        AmkNotify.chime() */
(function () {
  'use strict';
  var PAGE_VERSION = '2026-10-15.1';
  var S = { empId: '', seenAt: null, notices: [], unread: 0, bell: null, sheet: null, host: null, alarmTimer: 0, ac: null, fetching: false, pushTried: false };

  function $(t, c, h) { var e = document.createElement(t); if (c) e.className = c; if (h != null) e.innerHTML = h; return e; }
  function hx(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }
  function ls(k, v) { try { if (v === undefined) return localStorage.getItem(k); localStorage.setItem(k, v); } catch (e) { } return null; }
  function emp() { try { return (typeof currentEmp !== 'undefined' && currentEmp) ? currentEmp : null; } catch (e) { return null; } }
  function call(action, p) { return api(action, p); }

  /* ── sound ── */
  function ctx() {
    try { S.ac = S.ac || new (window.AudioContext || window.webkitAudioContext)(); if (S.ac.state === 'suspended') S.ac.resume(); } catch (e) { }
    return S.ac;
  }
  document.addEventListener('pointerdown', function () { ctx(); }, { once: true, capture: true });   // browsers only allow sound after a first tap
  function tone(freqs, step, type, vol, rounds) {
    var a = ctx(); if (!a) return;
    var o = a.createOscillator(), g = a.createGain(), t = a.currentTime, n = freqs.length * rounds;
    o.type = type; g.gain.value = vol; o.connect(g); g.connect(a.destination);
    for (var i = 0; i < n; i++) o.frequency.setValueAtTime(freqs[i % freqs.length], t + i * step);
    o.start(t); o.stop(t + n * step);
  }
  function chime() { tone([880, 1175], 0.18, 'sine', 0.25, 2); try { if (navigator.vibrate) navigator.vibrate([150, 80, 150]); } catch (e) { } }
  function alarmOnce() { tone([1000, 700], 0.16, 'square', 0.3, 6); try { if (navigator.vibrate) navigator.vibrate([500, 150, 500, 150, 500]); } catch (e) { } }
  function alarmStart() { alarmStop(); alarmOnce(); S.alarmTimer = setInterval(alarmOnce, 2600); }
  function alarmStop() { if (S.alarmTimer) { clearInterval(S.alarmTimer); S.alarmTimer = 0; } }

  /* ── styles ── */
  function css() {
    if (document.getElementById('amkNotifyCss')) return;
    var s = $('style'); s.id = 'amkNotifyCss';
    s.textContent =
      '.amk-bell{position:fixed;top:calc(env(safe-area-inset-top,0px) + 10px);right:12px;z-index:9000;width:42px;height:42px;border-radius:50%;border:1px solid var(--border,#dedfd4);background:var(--surface,#fff);color:var(--ink,#18201c);font-size:19px;line-height:1;cursor:pointer;box-shadow:0 2px 8px rgba(0,0,0,.12);display:none;align-items:center;justify-content:center}' +
      '.amk-bell.show{display:flex}.amk-bell.in-host{position:static;box-shadow:none}' +
      '.amk-badge{position:absolute;top:-4px;right:-4px;min-width:18px;height:18px;padding:0 5px;border-radius:9px;background:var(--red,#a03428);color:#fff;font:700 11px/18px sans-serif;text-align:center;display:none}' +
      '.amk-badge.show{display:block}' +
      '.amk-sheet{position:fixed;inset:0;z-index:9500;background:rgba(0,0,0,.45);display:none;align-items:flex-start;justify-content:center;padding:calc(env(safe-area-inset-top,0px) + 56px) 12px 12px}' +
      '.amk-sheet.open{display:flex}' +
      '.amk-panel{width:100%;max-width:460px;max-height:80vh;overflow:auto;background:var(--surface,#fff);color:var(--ink,#18201c);border-radius:14px;padding:14px;font-family:inherit}' +
      '.amk-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:8px}.amk-head b{font-size:16px}' +
      '.amk-btn{border:1px solid var(--border2,#c4c4b8);background:transparent;color:inherit;border-radius:8px;padding:6px 10px;font-size:13px;cursor:pointer}' +
      '.amk-btn.pri{background:var(--accent,#3d6b50);border-color:var(--accent,#3d6b50);color:#fff}' +
      '.amk-item{padding:10px 8px;border-top:1px solid var(--border,#dedfd4);cursor:pointer}.amk-item.unread{background:var(--accentBg,#d8e8dc);border-radius:8px}' +
      '.amk-item b{display:block;font-size:14px}.amk-item span{font-size:13px;color:var(--ink2,#3a4840)}.amk-item small{display:block;font-size:11px;color:var(--soft,#6b7a70);margin-top:2px}' +
      '.amk-empty{padding:18px 6px;text-align:center;color:var(--soft,#6b7a70);font-size:13px}' +
      '.amk-pop{position:fixed;left:12px;right:12px;top:calc(env(safe-area-inset-top,0px) + 8px);z-index:9800;max-width:460px;margin:0 auto;background:var(--surface,#fff);color:var(--ink,#18201c);border:2px solid var(--accent,#3d6b50);border-radius:14px;padding:12px 14px;box-shadow:0 8px 28px rgba(0,0,0,.25)}' +
      '.amk-pop b{display:block;font-size:15px;margin-bottom:2px}.amk-pop span{font-size:13px}.amk-pop .row{display:flex;gap:8px;margin-top:10px}' +
      '.amk-bar{position:fixed;left:12px;right:12px;bottom:calc(env(safe-area-inset-bottom,0px) + 10px);z-index:9700;max-width:460px;margin:0 auto;background:var(--ink,#18201c);color:#fff;border-radius:12px;padding:10px 12px;font-size:13px;display:flex;gap:10px;align-items:center;justify-content:space-between}' +
      '.amk-bar .amk-btn{color:#fff;border-color:rgba(255,255,255,.5)}';
    document.head.appendChild(s);
  }

  /* ── bell + message center ── */
  function ensureBell() {
    css();
    if (S.bell) return;
    S.bell = $('button', 'amk-bell', '🔔<span class="amk-badge"></span>'); S.bell.type = 'button'; S.bell.setAttribute('aria-label', 'Messages');
    S.bell.onclick = openSheet;
    (S.host || document.body).appendChild(S.bell);
    if (S.host) S.bell.classList.add('in-host');
  }
  function mount(container) {
    S.host = container || null;
    if (S.bell) { (S.host || document.body).appendChild(S.bell); S.bell.classList.toggle('in-host', !!S.host); }
  }
  function paintBell() {
    ensureBell();
    S.bell.classList.toggle('show', !!S.empId);
    var b = S.bell.querySelector('.amk-badge');
    b.textContent = S.unread > 99 ? '99+' : String(S.unread); b.classList.toggle('show', S.unread > 0);
  }
  function when(ts) { return hx(String(ts || '').replace('T', ' ').slice(0, 16)); }
  function openSheet() {
    css();
    if (!S.sheet) {
      S.sheet = $('div', 'amk-sheet');
      S.sheet.onclick = function (e) { if (e.target === S.sheet) closeSheet(); };
      document.body.appendChild(S.sheet);
    }
    paintSheet(); S.sheet.classList.add('open');
  }
  function closeSheet() { if (S.sheet) S.sheet.classList.remove('open'); }
  function paintSheet() {
    if (!S.sheet) return;
    var items = S.notices.map(function (n) {
      return '<div class="amk-item' + (n.readAt ? '' : ' unread') + '" data-id="' + hx(n.id) + '"><b>' + hx(n.title) + '</b><span>' + hx(n.body) + '</span><small>' + when(n.createdAt) + '</small></div>';
    }).join('') || '<div class="amk-empty">No messages yet. Reminders will show up here.</div>';
    S.sheet.innerHTML = '<div class="amk-panel"><div class="amk-head"><b>Messages</b><span><button class="amk-btn" data-act="all">Mark all read</button> <button class="amk-btn" data-act="close">Close</button></span></div>' + items + '</div>';
    S.sheet.querySelector('[data-act=close]').onclick = closeSheet;
    S.sheet.querySelector('[data-act=all]').onclick = function () { markRead(null); };
    Array.prototype.forEach.call(S.sheet.querySelectorAll('.amk-item.unread'), function (el) { el.onclick = function () { markRead([el.getAttribute('data-id')]); }; });
  }
  function markRead(ids) {
    var e = emp(); if (!e) return;
    S.notices.forEach(function (n) { if (!n.readAt && (!ids || ids.indexOf(n.id) >= 0)) n.readAt = 'now'; });
    S.unread = S.notices.filter(function (n) { return !n.readAt; }).length; paintBell(); paintSheet();
    var p = { empId: e.id }; if (ids) p.ids = ids;
    call('markNoticesRead', p).catch(function () { });
  }

  /* ── pop-up for new notices ── */
  function alertedKey() { return 'amk_alerted_' + S.empId; }
  function alertedSet() { try { return JSON.parse(ls(alertedKey()) || '[]'); } catch (e) { return []; } }
  function popup(n) {
    css();
    var old = document.getElementById('amkPop'); if (old) old.remove();
    var el = $('div', 'amk-pop', '<b>' + hx(n.title) + '</b><span>' + hx(n.body) + '</span><div class="row"><button class="amk-btn pri" data-a="open">Open messages</button><button class="amk-btn" data-a="ok">OK</button></div>');
    el.id = 'amkPop';
    el.querySelector('[data-a=ok]').onclick = function () { el.remove(); };
    el.querySelector('[data-a=open]').onclick = function () { el.remove(); openSheet(); };
    document.body.appendChild(el);
  }
  function refresh() {
    var e = emp(); if (!e || S.fetching) return Promise.resolve();
    S.fetching = true; var id = e.id;
    return call('getNotices', { empId: id }).then(function (d) {
      if (S.empId !== id) return;
      S.notices = d.notices || []; S.unread = d.unread || 0; paintBell(); paintSheet();
      var seen = alertedSet(), fresh = S.notices.filter(function (n) {
        return !n.readAt && seen.indexOf(n.id) < 0 && n.kind !== 'locationCheck' && (Date.now() - Date.parse(String(n.createdAt).replace(' ', 'T') + '+05:30') < 6 * 3600 * 1000);
      });
      var all = seen.concat(S.notices.map(function (n) { return n.id; })).slice(-200);
      ls(alertedKey(), JSON.stringify(all));
      if (fresh.length) { chime(); popup(fresh[0]); }
    }).catch(function () { }).then(function () { S.fetching = false; });
  }

  /* ── called from the existing 60-second poll ── */
  function onPoll(d) {
    var e = emp(); if (!e) return;
    if (S.empId !== e.id) { S.empId = e.id; S.seenAt = null; S.notices = []; S.unread = 0; paintBell(); }
    var at = (d && d.noticeAt) || '';
    if (S.seenAt === null || at !== S.seenAt) { S.seenAt = at; if (at || S.notices.length) refresh(); }
    if (!S.pushTried) { S.pushTried = true; setTimeout(pushSetup, 3000); }
  }
  function reset() {
    alarmStop(); S.empId = ''; S.seenAt = null; S.notices = []; S.unread = 0; S.pushTried = false;
    paintBell(); closeSheet();
    var p = document.getElementById('amkPop'); if (p) p.remove();
    var b = document.getElementById('amkPushBar'); if (b) b.remove();
  }

  /* ── web push ── */
  function isNative() { try { return typeof NATIVE !== 'undefined' && !!NATIVE; } catch (e) { return false; } }
  function bar(id, html, actions) {
    css(); var old = document.getElementById(id); if (old) old.remove();
    var el = $('div', 'amk-bar', '<span>' + html + '</span><span></span>'); el.id = id;
    actions.forEach(function (a) { var b = $('button', 'amk-btn', a.t); b.type = 'button'; b.onclick = function () { el.remove(); a.f(); }; el.lastChild.appendChild(b); });
    document.body.appendChild(el); return el;
  }
  function pushSetup() {
    var cfg = self.AMK_FIREBASE;
    if (isNative() || !cfg || !cfg.apiKey || !cfg.vapidKey || !emp()) return;
    var ios = /iPad|iPhone|iPod/.test(navigator.userAgent), standalone = (window.matchMedia && matchMedia('(display-mode: standalone)').matches) || navigator.standalone;
    if (ios && !standalone) { if (!ls('amk_ios_hint')) { ls('amk_ios_hint', '1'); bar('amkPushBar', 'To get reminders on iPhone, add this page to your Home Screen (Share → Add to Home Screen).', [{ t: 'OK', f: function () { } }]); } return; }
    if (!('Notification' in window) || !('serviceWorker' in navigator)) return;
    if (Notification.permission === 'granted') return registerToken();
    if (Notification.permission === 'default' && ls('amk_push_no') !== '1') {
      bar('amkPushBar', 'Get punch-in and punch-out reminders even when the app is closed.', [
        { t: 'Turn on', f: function () { Notification.requestPermission().then(function (p) { if (p === 'granted') registerToken(); }); } },
        { t: 'Not now', f: function () { ls('amk_push_no', '1'); } }]);
    }
  }
  function loadScript(src) { return new Promise(function (ok, no) { var s = document.createElement('script'); s.src = src; s.onload = ok; s.onerror = no; document.head.appendChild(s); }); }
  function registerToken() {
    var cfg = self.AMK_FIREBASE, e = emp(); if (!e) return;
    loadScript('https://www.gstatic.com/firebasejs/10.12.2/firebase-app-compat.js')
      .then(function () { return loadScript('https://www.gstatic.com/firebasejs/10.12.2/firebase-messaging-compat.js'); })
      .then(function () { return navigator.serviceWorker.register('firebase-messaging-sw.js'); })
      .then(function (reg) {
        if (!firebase.apps.length) firebase.initializeApp(cfg);
        var m = firebase.messaging();
        m.onMessage(function () { refresh(); });
        return m.getToken({ vapidKey: cfg.vapidKey, serviceWorkerRegistration: reg });
      })
      .then(function (tok) { if (tok && emp() && emp().id === e.id) return call('registerPushToken', { empId: e.id, pushToken: tok, platform: 'web' }); })
      .catch(function () { });
  }

  /* ── "new version" banner ── */
  var lastVer = 0;
  function checkVersion() {
    if (Date.now() - lastVer < 10 * 60000) return; lastVer = Date.now();
    fetch('version.json?t=' + Date.now(), { cache: 'no-store' }).then(function (r) { return r.ok ? r.json() : null; }).then(function (v) {
      if (!v || !v.web || v.web === PAGE_VERSION || document.getElementById('amkVerBar')) return;
      bar('amkVerBar', 'A new version of the app is ready.', [{ t: 'Reload', f: function () { location.reload(); } }, { t: 'Later', f: function () { } }]);
    }).catch(function () { });
  }
  document.addEventListener('visibilitychange', function () { if (document.visibilityState === 'visible') checkVersion(); });
  setTimeout(checkVersion, 4000);

  window.AmkNotify = { onPoll: onPoll, mount: mount, reset: reset, alarmStart: alarmStart, alarmStop: alarmStop, chime: chime, refresh: refresh, _state: S };
})();
