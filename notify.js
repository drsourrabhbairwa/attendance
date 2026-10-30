/* AmkNotify: message center (bell), pop-up + sound for new notices, web push registration, "new version" banner.
   Independent of the page's theme: it builds its own markup and reads colours from CSS variables with fallbacks.
   Public API:  AmkNotify.onPoll(pollData)   AmkNotify.mount(container)   AmkNotify.reset()
                AmkNotify.alarmStart()       AmkNotify.alarmStop()        AmkNotify.chime() */
(function () {
  'use strict';
  var PAGE_VERSION = window.AMK_PAGE_VERSION || '';   // stamped into index.html by tools/release.py, so a refreshed page always knows its own version
  var S = { empId: '', seenAt: null, notices: [], unread: 0, bell: null, sheet: null, host: null, alarmTimer: 0, ac: null, fetching: false, pushTried: false, nativeAt: 0, cleared: {}, filter: 'all' };

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
      '.amk-sheet{position:fixed;inset:0;z-index:9500;background:rgba(10,18,14,.5);display:none;align-items:flex-end;justify-content:center;padding:0}' +
      '.amk-sheet.open{display:flex}' +
      '.amk-panel{width:100%;max-width:520px;height:min(86vh,720px);display:flex;flex-direction:column;background:#fff;color:var(--ink,#18201c);border-radius:18px 18px 0 0;font-family:inherit;overflow:hidden;animation:amkUp .22s ease-out}' +
      '@keyframes amkUp{from{transform:translateY(24px);opacity:.6}to{transform:none;opacity:1}}@keyframes amkDown{from{transform:translateY(-14px);opacity:0}to{transform:none;opacity:1}}' +
      '@media(prefers-reduced-motion:reduce){.amk-panel,.amk-pop{animation:none}}' +
      '@media(min-width:700px){.amk-sheet{align-items:center}.amk-panel{border-radius:18px;height:min(80vh,680px)}}' +
      '.amk-head{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:14px 14px 8px}.amk-head b{font-size:18px}.amk-head b small{font-weight:600;font-size:12px;color:var(--soft,#6b7a70);margin-left:8px}' +
      '.amk-head .amk-btn[disabled]{opacity:.45;cursor:default}' +
      '.amk-chips{display:flex;gap:6px;padding:0 14px 10px;overflow-x:auto}' +
      '.amk-chip{flex:none;min-height:34px;border:1px solid var(--border2,#c4c4b8);background:transparent;color:inherit;border-radius:999px;padding:6px 13px;font-size:13px;font-weight:600;cursor:pointer}' +
      '.amk-chip.on{background:var(--ink,#18201c);border-color:var(--ink,#18201c);color:#fff}' +
      '.amk-list{flex:1;overflow:auto;padding:0 8px 18px;-webkit-overflow-scrolling:touch}' +
      '.amk-day{position:sticky;top:0;z-index:1;background:#fff;padding:10px 8px 4px;font-size:11px;font-weight:700;letter-spacing:.07em;text-transform:uppercase;color:var(--soft,#6b7a70)}' +
      '.amk-item{display:flex;gap:10px;align-items:flex-start;padding:10px 8px;margin:2px 0;border-radius:12px;cursor:pointer}.amk-item:hover{background:#f3f6f4}.amk-item:focus-visible{outline:2px solid var(--accent,#3d6b50);outline-offset:1px}' +
      '.amk-item.unread{background:#e3f1ec}' +
      '.amk-ic{flex:none;width:36px;height:36px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:17px;font-style:normal;background:var(--cb,#e8ece6);color:var(--c,#3d6b50)}' +
      '.amk-tx{flex:1;min-width:0}.amk-item b{display:block;font-size:14px;line-height:1.3}.amk-item span{display:block;font-size:13px;color:var(--ink2,#3a4840);white-space:pre-line;line-height:1.35;margin-top:1px}' +
      '.amk-item small{display:block;font-size:11px;color:var(--soft,#6b7a70);margin-top:3px}.amk-item small .amk-go{display:inline;margin:0;font-size:11px;font-weight:700;color:var(--accent,#3d6b50)}' +
      '.amk-dot{flex:none;width:9px;height:9px;border-radius:50%;background:var(--accent,#3d6b50);margin-top:7px;display:none}.amk-item.unread .amk-dot{display:block}' +
      '.amk-btn{border:1px solid var(--border2,#c4c4b8);background:transparent;color:inherit;border-radius:8px;padding:6px 10px;font-size:13px;cursor:pointer}' +
      '.amk-btn.pri{background:var(--accent,#3d6b50);border-color:var(--accent,#3d6b50);color:#fff}' +
      '.amk-act{display:flex;gap:8px;flex-wrap:wrap;margin-top:8px}.amk-act .amk-btn{min-height:40px;font-weight:600}' +
      '.amk-note{margin-top:6px;font-size:12px;font-weight:600;color:var(--accent,#3d6b50)}.amk-note.err{color:var(--red,#a03428)}' +
      '.amk-empty{padding:34px 10px;text-align:center;color:var(--soft,#6b7a70);font-size:14px}' +
      '.amk-k-att{--c:var(--blue,#2048a0);--cb:var(--blueBg,#d4e0f5)}.amk-k-warn{--c:var(--orange,#b86018);--cb:var(--orangeBg,#f8e4cc)}.amk-k-red{--c:var(--red,#a03428);--cb:var(--redBg,#f5d8d4)}' +
      '.amk-k-task{--c:var(--green,#2d7a48);--cb:var(--greenBg,#d4ebd8)}.amk-k-leave{--c:var(--purple,#6040b0);--cb:var(--purpleBg,#e4dcf8)}.amk-k-plan{--c:var(--amber,#a87020);--cb:var(--amberBg,#f5e4c4)}.amk-k-ann{--c:#0e6e7e;--cb:#d2ecf0}' +
      '.amk-pop{position:fixed;left:12px;right:12px;top:calc(env(safe-area-inset-top,0px) + 8px);z-index:9800;max-width:460px;margin:0 auto;display:flex;gap:10px;align-items:flex-start;background:#fff;color:var(--ink,#18201c);border:1px solid var(--border,#dedfd4);border-left:5px solid var(--c,#3d6b50);border-radius:14px;padding:12px 14px;box-shadow:0 10px 30px rgba(0,0,0,.25);animation:amkDown .22s ease-out}' +
      '.amk-pop .tx{flex:1;min-width:0}.amk-pop b{display:block;font-size:15px;margin-bottom:2px}.amk-pop span{display:block;font-size:13px;white-space:pre-line}.amk-pop small{display:block;font-size:12px;font-weight:600;color:var(--soft,#6b7a70);margin-top:4px}.amk-pop .row{display:flex;flex-wrap:wrap;gap:8px;margin-top:10px}' +
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
  /* what each kind of message looks like (icon, colour, label) and where a tap on it goes: today | out | tasks | leave. imp = shows under "Important". */
  var KIND = {
    punchIn: { i: '⏰', c: 'att', l: 'Attendance', go: 'today' }, punchOut: { i: '⏰', c: 'att', l: 'Attendance', go: 'out' },
    punchInDone: { i: '✅', c: 'att', l: 'Attendance' }, punchOutDone: { i: '✅', c: 'att', l: 'Attendance' },
    autoPunchOut: { i: '🔵', c: 'att', l: 'Attendance', go: 'today', imp: 1 }, hoursAway: { i: '🚶', c: 'warn', l: 'Attendance', go: 'today' },
    leftOffice: { i: '🚪', c: 'warn', l: 'Attendance', imp: 1 }, gpsOff: { i: '📵', c: 'warn', l: 'Location', go: 'today', imp: 1 },
    siteAway: { i: '📍', c: 'warn', l: 'Location', go: 'today', imp: 1 }, siteStopped: { i: '📍', c: 'warn', l: 'Location', go: 'today', imp: 1 },
    meetingAway: { i: '🤝', c: 'warn', l: 'Meeting', go: 'today', imp: 1 }, meetingEnded: { i: '🤝', c: 'warn', l: 'Meeting', go: 'today', imp: 1 },
    meetingPaused: { i: '⏸', c: 'warn', l: 'Meeting', go: 'today', imp: 1 }, meetingResumed: { i: '▶', c: 'task', l: 'Meeting', go: 'today' },
    workAway: { i: '🛠', c: 'warn', l: 'Task', go: 'today', imp: 1 }, workStopped: { i: '🛠', c: 'warn', l: 'Task', go: 'today', imp: 1 },
    workPaused: { i: '⏸', c: 'warn', l: 'Task', go: 'today', imp: 1 }, workResumed: { i: '▶', c: 'task', l: 'Task', go: 'today' },
    dayModeAuto: { i: '🏢', c: 'att', l: 'Attendance', go: 'today' },
    daySummary: { i: '🗒️', c: 'att', l: 'Attendance', go: 'today' },
    locationCheck: { i: '📍', c: 'warn', l: 'Location', imp: 1 }, alert: { i: '🚨', c: 'red', l: 'Alert', imp: 1 }, report: { i: '📋', c: 'red', l: 'Report' },
    task: { i: '✅', c: 'task', l: 'Task', go: 'tasks' }, leave: { i: '🌴', c: 'leave', l: 'Leave', go: 'leave' },
    plan: { i: '🗓️', c: 'plan', l: 'Plan', go: 'today' }, weekly: { i: '📊', c: 'plan', l: 'Weekly' }, announce: { i: '📣', c: 'ann', l: 'Announcement', imp: 1 },
    policy: { i: '📜', c: 'att', l: 'Policies', go: 'policies', imp: 1 }
  };
  function meta(n) { return KIND[n && n.kind] || { i: '🔔', c: 'att', l: 'Message' }; }
  function isImp(n) { return !!meta(n).imp || leaveOpen(n); }
  function goOf(n) { return leaveOpen(n) ? '' : (meta(n).go || ''); }
  var MON = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  function dayKey(n) { return String(n.createdAt || '').slice(0, 10); }
  function todayKey() { try { if (typeof todayStr === 'function') return todayStr(); } catch (e) { } var d = new Date(), z = function (x) { return ('0' + x).slice(-2); }; return d.getFullYear() + '-' + z(d.getMonth() + 1) + '-' + z(d.getDate()); }
  function addDay(k, delta) { var t = Date.parse(k + 'T12:00:00Z'); return isNaN(t) ? '' : new Date(t + delta * 86400000).toISOString().slice(0, 10); }
  function groupOf(n) { var k = dayKey(n), t = todayKey(); return k === t ? 'Today' : k === addDay(t, -1) ? 'Yesterday' : 'Earlier'; }
  function clock(ts) { var m = /(\d{2}):(\d{2})/.exec(String(ts || '').replace('T', ' ').slice(11)); if (!m) return ''; var h = +m[1]; return ((h % 12) || 12) + ':' + m[2] + ' ' + (h >= 12 ? 'PM' : 'AM'); }
  function ago(n) {
    var g = groupOf(n), t = Date.parse(String(n.createdAt || '').replace(' ', 'T') + '+05:30'), d = Date.now() - t;
    if (g === 'Today') { if (isNaN(t) || d < 0) return clock(n.createdAt); if (d < 60000) return 'just now'; if (d < 3600000) return Math.floor(d / 60000) + ' min ago'; return clock(n.createdAt); }
    if (g === 'Yesterday') return clock(n.createdAt);
    var k = dayKey(n).split('-'); return k.length === 3 ? (+k[2]) + ' ' + (MON[+k[1] - 1] || '') + ' · ' + clock(n.createdAt) : '';
  }
  function openSheet() {
    css(); S.filter = 'all';
    if (!S.sheet) {
      S.sheet = $('div', 'amk-sheet');
      S.sheet.onclick = function (e) { if (e.target === S.sheet) closeSheet(); };
      document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && S.sheet && S.sheet.classList.contains('open')) closeSheet(); });
      document.body.appendChild(S.sheet);
    }
    paintSheet(); S.sheet.classList.add('open');
  }
  function closeSheet() { if (S.sheet) S.sheet.classList.remove('open'); }
  function itemHtml(n) {
    var m = meta(n), go = goOf(n);
    return '<div class="amk-item' + (n.readAt ? '' : ' unread') + '" data-id="' + hx(n.id) + '" data-k="' + hx(n.kind) + '"' + (go ? ' data-go="' + go + '"' : '') + ' role="button" tabindex="0">' +
      '<i class="amk-ic amk-k-' + m.c + '" aria-hidden="true">' + m.i + '</i><div class="amk-tx"><b>' + hx(n.title) + '</b><span>' + hx(n.body) + '</span>' + leaveHtml(n) +
      '<small>' + hx(ago(n)) + ' · ' + hx(m.l) + (go ? ' · <span class="amk-go">Open ›</span>' : '') + '</small></div><em class="amk-dot" aria-label="Unread"></em></div>';
  }
  function paintSheet() {
    if (!S.sheet) return;
    var all = S.notices, f = S.filter || 'all', un = all.filter(function (n) { return !n.readAt; }).length, imp = all.filter(isImp).length;
    var list = all.filter(function (n) { return f === 'unread' ? !n.readAt : f === 'imp' ? isImp(n) : true; });
    var body = '', last = '';
    list.forEach(function (n) { var g = groupOf(n); if (g !== last) { body += '<div class="amk-day">' + g + '</div>'; last = g; } body += itemHtml(n); });
    if (!body) body = '<div class="amk-empty">' + (f === 'unread' ? 'You are all caught up. 🎉' : f === 'imp' ? 'Nothing important right now.' : 'No messages yet. Reminders will show up here.') + '</div>';
    var chip = function (k, t) { return '<button type="button" class="amk-chip' + (f === k ? ' on' : '') + '" data-f="' + k + '">' + t + '</button>'; };
    var keep = 0, old = S.sheet.querySelector('.amk-list'); if (old) keep = old.scrollTop;
    S.sheet.innerHTML = '<div class="amk-panel" role="dialog" aria-modal="true" aria-label="Messages"><div class="amk-head"><b>Messages' + (un ? '<small>' + un + ' unread</small>' : '') + '</b><span>' +
      '<button class="amk-btn" data-act="all"' + (un ? '' : ' disabled') + '>Mark all read</button> <button class="amk-btn" data-act="close" aria-label="Close">✕</button></span></div>' +
      '<div class="amk-chips">' + chip('all', 'All') + chip('unread', 'Unread' + (un ? ' (' + un + ')' : '')) + chip('imp', 'Important' + (imp ? ' (' + imp + ')' : '')) + '</div><div class="amk-list">' + body + '</div></div>';
    var ls2 = S.sheet.querySelector('.amk-list'); if (ls2 && keep) ls2.scrollTop = keep;
    S.sheet.querySelector('[data-act=close]').onclick = closeSheet;
    S.sheet.querySelector('[data-act=all]').onclick = function () { markRead(null); };
    Array.prototype.forEach.call(S.sheet.querySelectorAll('.amk-chip'), function (b) { b.onclick = function () { S.filter = b.getAttribute('data-f'); paintSheet(); }; });
    Array.prototype.forEach.call(S.sheet.querySelectorAll('.amk-item'), function (el) {
      var act = function () {
        var id = el.getAttribute('data-id'), go = el.getAttribute('data-go') || '';
        if (el.classList.contains('unread')) markRead([id]);
        if (go) { closeSheet(); try { if (window.AmkApp) window.AmkApp.route(go); } catch (e) { } }
      };
      el.onclick = act;
      el.onkeydown = function (ev) { if (ev.key === 'Enter' || ev.key === ' ') { ev.preventDefault(); act(); } };
    });
    Array.prototype.forEach.call(S.sheet.querySelectorAll('[data-leave]'), function (b) { b.onclick = function (ev) { ev.stopPropagation(); leaveAnswer(b.getAttribute('data-id'), b.getAttribute('data-leave') === 'yes'); }; });
  }
  /* "You seem to have left at 06:42 PM": two buttons while the prompt is today's and still open */
  function isToday(n) { var t = ''; try { t = typeof todayStr === 'function' ? todayStr() : ''; } catch (e) { } return !t || (String(n.id || '').split('|')[2] || String(n.createdAt || '').slice(0, 10)) === t; }
  function leaveOpen(n) { return n.kind === 'leftOffice' && !n.state && isToday(n); }
  function leaveHtml(n) {
    if (n.kind !== 'leftOffice') return '';
    var st = String(n.state || ''), err = n._err ? '<div class="amk-note err">' + hx(n._err) + '</div>' : '';
    if (st === 'done') return '<div class="amk-note">✓ Punched out at ' + hx(n.ref) + '</div>';
    if (/^held/.test(st)) return '<div class="amk-note">Noted: you are still working</div>';
    if (!leaveOpen(n)) return '';
    return '<div class="amk-act"><button type="button" class="amk-btn pri" data-leave="yes" data-id="' + hx(n.id) + '">Yes, punch out at ' + hx(n.ref) + '</button><button type="button" class="amk-btn" data-leave="no" data-id="' + hx(n.id) + '">Still working</button></div>' + err;
  }
  /* the Android app also keeps these messages in the phone's notification list: answered or read here, they go from there too */
  function clearNative(ids) {
    try { var T = window.AndroidTracker; if (isNative() && T && typeof T.clearNotice === 'function') ids.forEach(function (id) { T.clearNotice(String(id)); }); } catch (x) { }
  }
  function leaveAnswer(id, yes) {
    var e = emp(), n = S.notices.filter(function (x) { return x.id === id; })[0]; if (!e || !n) return;
    n._err = '';
    call(yes ? 'confirmLeft' : 'stillWorking', { empId: e.id, date: String(n.id || '').split('|')[2] || String(n.createdAt || '').slice(0, 10) }).then(function () {
      n.state = yes ? 'done' : 'held|now'; if (!n.readAt) n.readAt = 'now'; clearNative([n.id]);
      S.unread = S.notices.filter(function (x) { return !x.readAt; }).length; paintBell(); paintSheet();
      var p = document.getElementById('amkPop'); if (p) p.remove();
      if (yes) { try { if (typeof loadHome === 'function') loadHome(); } catch (x) { } }
      refresh();
    }).catch(function (err) { n._err = (err && err.message) || 'Could not save. Try again.'; paintSheet(); });
  }
  function markRead(ids) {
    var e = emp(); if (!e) return;
    var marked = [];
    S.notices.forEach(function (n) { if (!n.readAt && (!ids || ids.indexOf(n.id) >= 0)) { n.readAt = 'now'; marked.push(n.id); } });
    clearNative(marked);
    S.unread = S.notices.filter(function (n) { return !n.readAt; }).length; paintBell(); paintSheet();
    var p = { empId: e.id }; if (ids) p.ids = ids;
    call('markNoticesRead', p).catch(function () { });
  }

  /* ── pop-up for new notices ── */
  function alertedKey() { return 'amk_alerted_' + S.empId; }
  function alertedSet() { try { return JSON.parse(ls(alertedKey()) || '[]'); } catch (e) { return []; } }
  function popup(n, more) {
    css(); more = more || 0;
    var old = document.getElementById('amkPop'); if (old) old.remove();
    var ask = leaveOpen(n), m = meta(n);
    var el = $('div', 'amk-pop amk-k-' + m.c, '<i class="amk-ic amk-k-' + m.c + '" aria-hidden="true">' + m.i + '</i><div class="tx"><b>' + hx(n.title) + '</b><span>' + hx(n.body) + '</span>' +
      (more > 0 ? '<small>+' + more + ' more new message' + (more > 1 ? 's' : '') + '</small>' : '') + '<div class="row">' + (ask
        ? '<button class="amk-btn pri" data-a="yes">Yes, punch out at ' + hx(n.ref) + '</button><button class="amk-btn" data-a="no">Still working</button>'
        : '<button class="amk-btn pri" data-a="open">' + (more > 0 ? 'Open messages' : 'Open messages') + '</button><button class="amk-btn" data-a="ok">OK</button>') + '</div></div>');
    el.id = 'amkPop'; el.setAttribute('role', 'alert');
    var q = function (a) { return el.querySelector('[data-a=' + a + ']'); };
    if (q('ok')) q('ok').onclick = function () { el.remove(); };
    if (q('open')) q('open').onclick = function () { el.remove(); openSheet(); };
    if (q('yes')) q('yes').onclick = function () { leaveAnswer(n.id, true); };
    if (q('no')) q('no').onclick = function () { leaveAnswer(n.id, false); };
    document.body.appendChild(el);
    if (!ask && !isImp(n)) setTimeout(function () { if (el.parentNode && !el.matches(':hover')) el.remove(); }, 12000);   // an ordinary message fades by itself; questions and important messages wait for a tap
  }
  /* "Your meeting / site job / task was ended because you moved away": the page redraws the screen once for each such message, so it stops showing the job as running */
  function endedHook(list) {
    try {
      var k = 'amk_ended_' + S.empId, seen = []; try { seen = JSON.parse(ls(k) || '[]'); } catch (x) { }
      var fresh = (list || []).filter(function (n) { return (n.kind === 'meetingEnded' || n.kind === 'meetingPaused' || n.kind === 'meetingResumed' || n.kind === 'siteStopped' || n.kind === 'workStopped' || n.kind === 'workPaused' || n.kind === 'workResumed' || n.kind === 'dayModeAuto') && seen.indexOf(n.id) < 0; });
      if (!fresh.length) return;
      ls(k, JSON.stringify(seen.concat(fresh.map(function (n) { return n.id; })).slice(-50)));
      if (typeof window.amkServerEnded === 'function') window.amkServerEnded(fresh.map(function (n) { return n.kind; }));
    } catch (x) { }
  }
  function refresh() {
    var e = emp(); if (!e || S.fetching) return Promise.resolve();
    S.fetching = true; var id = e.id;
    return call('getNotices', { empId: id }).then(function (d) {
      if (S.empId !== id) return;
      S.notices = d.notices || []; S.unread = d.unread || 0; paintBell(); paintSheet();
      clearNative(S.notices.filter(function (n) { return n.readAt && !S.cleared[n.id] && (S.cleared[n.id] = 1); }).map(function (n) { return n.id; }));   // answered or read on another device: its notification here goes too
      var seen = alertedSet(), fresh = S.notices.filter(function (n) {
        return !n.readAt && seen.indexOf(n.id) < 0 && n.kind !== 'locationCheck' && n.kind !== 'punchInDone' && n.kind !== 'punchOutDone' && n.kind !== 'daySummary' && n.kind !== 'weekly' && (Date.now() - Date.parse(String(n.createdAt).replace(' ', 'T') + '+05:30') < 6 * 3600 * 1000);
      });
      var all = seen.concat(S.notices.map(function (n) { return n.id; })).slice(-200);
      ls(alertedKey(), JSON.stringify(all));
      endedHook(S.notices);
      var pov = document.getElementById('polOv'); if (pov && pov.classList.contains('on')) fresh = fresh.filter(function (n) { return n.kind !== 'policy'; });   // the accept screen is already up: no pop-up asking for the same thing on top of it
      if (fresh.length) { chime(); if (!(S.sheet && S.sheet.classList.contains('open'))) popup(fresh[0], fresh.length - 1); }   // the list is already open and has just redrawn: no pop-up on top of it
    }).catch(function () { }).then(function () { S.fetching = false; });
  }

  /* ── called from the existing 60-second poll ── */
  function onPoll(d) {
    var e = emp(); if (!e) return;
    if (S.empId !== e.id) { S.empId = e.id; S.seenAt = null; S.notices = []; S.unread = 0; S.cleared = {}; paintBell(); }
    var at = (d && d.noticeAt) || '';
    if (S.seenAt === null || at !== S.seenAt) { S.seenAt = at; if (at || S.notices.length) refresh(); }
    if (!S.pushTried) { S.pushTried = true; setTimeout(pushSetup, 3000); }
    else if (isNative() && Date.now() - S.nativeAt > 600000) nativeSetup();
  }
  /* keepAuth: the same person is still signed in on this phone (they only opened the admin panel), so the app keeps answering and showing messages for them */
  function reset(keepAuth) {
    alarmStop(); S.empId = ''; S.seenAt = null; S.notices = []; S.unread = 0; S.pushTried = false; S.cleared = {};
    if (!keepAuth) { try { var T = window.AndroidTracker; if (isNative() && T && typeof T.setAuth === 'function') T.setAuth(''); } catch (x) { } }   // the app stops answering for, and showing messages to, the person who left
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
  /* the Android app receives push itself (its own Firebase token); the page only tells the server which phone it is, and gives the app what it needs to answer buttons on a notification */
  function nativeSetup() {
    var T = window.AndroidTracker, e = emp(); if (!isNative() || !T || !e) return;
    S.nativeAt = Date.now();
    var tries = 0, id = e.id;
    (function go() {
      if (!emp() || emp().id !== id) return;
      var tok = ''; try { if (typeof T.pushToken === 'function') tok = T.pushToken() || ''; } catch (x) { }
      var key = id + '|' + tok;   // one key for the phone: signing in as someone else and back must register again
      if (tok) { if (ls('amk_native_tok') !== key) call('registerPushToken', { empId: id, pushToken: tok, platform: 'android' }).then(function () { ls('amk_native_tok', key); }).catch(function () { }); }
      else if (++tries < 8 && typeof T.pushToken === 'function') setTimeout(go, 5000);
    })();
    try { if (typeof T.setAuth === 'function' && typeof trackerTok === 'function') T.setAuth(JSON.stringify({ empId: id, token: trackerTok(id), scriptUrl: SCRIPT_URL, secret: API_SECRET })); } catch (x) { }
  }
  function pushSetup() {
    var cfg = self.AMK_FIREBASE;
    if (isNative()) { nativeSetup(); return; }
    if (!cfg || !cfg.apiKey || !cfg.vapidKey || !emp()) return;
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

  /* ── "new version" pop-up ── checked on load, every 5 minutes, and whenever the app comes back to the front */
  var lastVer = 0;
  function verCss() {
    if (document.getElementById('amkVerCss')) return;
    var s = document.createElement('style'); s.id = 'amkVerCss';
    s.textContent = '.amk-ver{position:fixed;left:12px;right:12px;top:calc(env(safe-area-inset-top,0px) + 10px);z-index:9800;max-width:460px;margin:0 auto;padding:14px;border-radius:18px;background:rgba(255,255,255,.96);color:var(--ink,#16302F);border:1px solid rgba(255,255,255,.8);box-shadow:0 14px 40px rgba(20,40,40,.28);-webkit-backdrop-filter:blur(16px) saturate(1.2);backdrop-filter:blur(16px) saturate(1.2);font:14px/1.35 system-ui,sans-serif;animation:amkVerIn .25s ease-out}' +
      '@keyframes amkVerIn{from{transform:translateY(-14px);opacity:0}to{transform:none;opacity:1}}@media(prefers-reduced-motion:reduce){.amk-ver{animation:none}}' +
      '.amk-ver b{display:block;font-size:15px;margin-bottom:2px}.amk-ver small{display:block;color:var(--soft,#5b6b66);margin-top:2px}' +
      '.amk-ver div{display:flex;gap:8px;margin-top:10px}.amk-ver button{flex:1;min-height:42px;border-radius:12px;border:1px solid var(--border,#c9d4d0);background:#fff;font:600 14px system-ui,sans-serif;cursor:pointer;color:inherit}' +
      '.amk-ver button.go{background:var(--accent,#1f7a6b);color:#fff;border-color:transparent}';
    document.head.appendChild(s);
  }
  function applyUpdate(ver) {
    var go = function () { location.replace(location.pathname + '?v=' + encodeURIComponent(ver) + location.hash); };
    try { if (window.caches && caches.keys) { caches.keys().then(function (ks) { return Promise.all(ks.map(function (k) { return caches.delete(k); })); }).catch(function () { }).then(go); return; } } catch (e) { }
    go();
  }
  function showUpdate(v) {
    verCss(); var old = document.getElementById('amkVerBar'); if (old) return;
    var el = $('div', 'amk-ver', '<b>🆕 A new version is ready</b>' + (v.notes ? '<span>' + hx(v.notes) + '</span>' : '<span>Update now to get the latest fixes and features.</span>') + '<small>Your sign-in stays. It takes a few seconds.</small><div></div>');
    el.id = 'amkVerBar'; el.setAttribute('role', 'alertdialog');
    var go = $('button', 'go', 'Update now'); go.type = 'button'; go.onclick = function () { go.disabled = true; go.textContent = 'Updating…'; applyUpdate(v.web); };
    el.lastChild.appendChild(go);
    if (!v.force) { var later = $('button', '', 'Later'); later.type = 'button'; later.onclick = function () { el.remove(); ls('amk_ver_snooze', String(Date.now() + 3600000)); }; el.lastChild.appendChild(later); }
    document.body.appendChild(el); try { chime(); } catch (e) { }
  }
  /* manual = the person tapped "Check for update": always answer, ignore the snooze */
  function checkVersion(manual) {
    if (!manual && Date.now() - lastVer < 120000) return Promise.resolve(); lastVer = Date.now();
    return fetch('version.json?t=' + Date.now(), { cache: 'no-store' }).then(function (r) { return r.ok ? r.json() : null; }).then(function (v) {
      if (!v || !v.web) return manual ? 'error' : undefined;
      if (v.web === PAGE_VERSION) return manual ? 'current' : undefined;
      if (!manual && !v.force && Number(ls('amk_ver_snooze') || 0) > Date.now()) return;
      showUpdate(v); return 'new';
    }).catch(function () { return manual ? 'error' : undefined; });
  }
  document.addEventListener('visibilitychange', function () { if (document.visibilityState === 'visible') checkVersion(); });
  setInterval(function () { if (document.visibilityState === 'visible') checkVersion(); }, 300000);
  setTimeout(checkVersion, 4000);

  window.AmkNotify = { open: openSheet, version: function () { return PAGE_VERSION; }, checkNow: function () { return checkVersion(true); }, onPoll: onPoll, mount: mount, reset: reset, alarmStart: alarmStart, alarmStop: alarmStop, chime: chime, refresh: refresh, _state: S };
})();
