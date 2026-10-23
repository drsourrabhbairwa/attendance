/* The app's own small store, inside the Android app (Me tab → App Store).
   - A new app with the store: shows the installed and the newest version, downloads it and hands it to Android's installer
     (the Android app does the work; this page draws the screen and follows the progress).
   - A forced update (the office raised the lowest allowed build) covers the whole screen until the app is new enough.
   - An older app without the store gets a one-time card: install the new app once by hand; after that updates come from here.
   Everything is skipped in a normal browser. */
(function () {
  'use strict';
  var REL = 'https://api.github.com/repos/drsourrabhbairwa/attendance/releases/latest';
  var APK = 'https://github.com/drsourrabhbairwa/attendance/releases/latest/download/amitek-attendance.apk';
  var OWN = 'https://github.com/drsourrabhbairwa/attendance/releases/download/';
  var TITLE = '📦 App Store';
  var S = { info: null, old: null, perm: false, min: 0 };

  function native() { try { return typeof NATIVE !== 'undefined' && !!NATIVE && !!window.AndroidTracker; } catch (e) { return false; } }
  function bridge() { return window.AndroidTracker; }
  function hasStore() { return native() && typeof bridge().updateInfo === 'function'; }
  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }
  function $(id) { return document.getElementById(id); }
  function ls(k, v) { try { if (v === undefined) return localStorage.getItem(k); localStorage.setItem(k, v); } catch (e) { } return null; }
  function mb(b) { return b > 0 ? (b / 1048576).toFixed(1) + ' MB' : ''; }
  function uaBuild() { var m = /AmitekApp\/1\.0\.(\d+)/.exec(navigator.userAgent || ''); return m ? +m[1] : 0; }

  function css() {
    if ($('amkStoreCss')) return;
    var s = document.createElement('style'); s.id = 'amkStoreCss';
    s.textContent = '.as-card{display:flex;align-items:center;gap:12px;padding:12px;border-radius:14px;background:var(--surface2);border:1px solid var(--border)}' +
      '.as-ic{width:46px;height:46px;border-radius:14px;display:flex;align-items:center;justify-content:center;font-size:24px;background:var(--accentBg);flex-shrink:0}' +
      '.as-card b{display:block;font-size:15px}.as-card span{display:block;font-size:12px;color:var(--soft)}' +
      '.as-chip{margin-left:auto;font-size:11px;font-weight:700;padding:3px 9px;border-radius:99px;background:var(--greenBg);color:var(--green);white-space:nowrap}' +
      '.as-card .as-chip{display:inline-block;font-size:11px;color:var(--green)}.as-chip.new{background:var(--amberBg)!important;color:var(--amber)!important}' +
      '.as-new{margin-top:10px;padding:10px 12px;border-radius:12px;background:var(--accentBg)}.as-new b{font-size:14px}' +
      '.as-new p{white-space:pre-line;font-size:13px;margin-top:6px;color:var(--ink2);line-height:1.45}' +
      '.as-bar{height:10px;border-radius:99px;background:var(--border);overflow:hidden;margin-top:14px}' +
      '.as-bar i{display:block;height:100%;background:linear-gradient(90deg,#27968B,#1B7F6B);border-radius:99px;transition:width .3s}' +
      '.as-note{font-size:12.5px;color:var(--ink2);margin-top:10px;line-height:1.5}.as-note b{font-weight:700}' +
      '.as-steps{margin:8px 0 0 18px;font-size:13px;color:var(--ink2);line-height:1.55}' +
      '#amkGate{position:fixed;inset:0;z-index:10050;display:flex;align-items:center;justify-content:center;padding:20px;background:rgba(14,42,46,.95)}' +
      '#amkGate .as-box{width:100%;max-width:380px;background:#fff;color:var(--ink);border-radius:20px;padding:20px;box-shadow:0 20px 60px rgba(0,0,0,.4)}' +
      '#amkGate h3{font-size:18px;margin-bottom:6px}#amkGate p.lead{font-size:13.5px;color:var(--ink2);line-height:1.5;margin-bottom:12px}' +
      '.as-hot{color:var(--accent2)!important;font-weight:700}';
    document.head.appendChild(s);
  }

  /* ── what the app knows ── */
  function readInfo() {
    if (!hasStore()) return null;
    try { return JSON.parse(bridge().updateInfo() || '{}'); } catch (e) { return null; }
  }
  function refresh() { var i = readInfo(); if (i) S.info = i; paint(); gate(); }
  function onUpdate(i) { if (i && typeof i === 'object') S.info = i; paint(); gate(); }

  /* an older app has no store: the page asks GitHub itself which build is newest */
  function oldCheck(force) {
    var mine = uaBuild(); if (!native() || hasStore() || !mine) return;
    var cached = null; try { cached = JSON.parse(ls('amk_store_old') || 'null'); } catch (e) { }
    if (cached && !force && Date.now() - cached.at < 6 * 3600e3 && cached.at <= Date.now()) { setOld(cached, mine); return; }
    var ctl = window.AbortController ? new AbortController() : null, tm = setTimeout(function () { if (ctl) ctl.abort(); }, 10000);
    fetch(REL, { headers: { Accept: 'application/vnd.github+json' }, signal: ctl ? ctl.signal : undefined }).then(function (r) { return r.ok ? r.json() : null; }).then(function (r) {
      clearTimeout(tm);
      var m = r && !r.draft && !r.prerelease && /^app-v(\d{1,9})(?:\.\d+)?$/.exec(String(r.tag_name || ''));
      var a = m && (r.assets || []).filter(function (x) { return x.name === 'amitek-attendance.apk' && String(x.browser_download_url || '').indexOf(OWN) === 0; })[0];
      if (!a) return;
      var rec = { at: Date.now(), latest: +m[1], size: +a.size || 0, notes: String(r.body || '').replace(/\r/g, '').replace(/^#+\s*/gm, '').replace(/[*`]+/g, '').trim().slice(0, 600) };
      ls('amk_store_old', JSON.stringify(rec)); setOld(rec, mine);
    }).catch(function () { clearTimeout(tm); });
  }
  function setOld(rec, mine) { S.old = { build: mine, latest: rec.latest, newer: rec.latest > mine, size: rec.size, notes: rec.notes }; paint(); gate(); readMin(); }
  function readMin() {
    try { fetch('version.json?t=' + Date.now(), { cache: 'no-store' }).then(function (r) { return r.ok ? r.json() : null; }).then(function (j) { S.min = Math.max(0, +(j && j.minBuild) || 0); gate(); }).catch(function () { }); } catch (e) { }
  }

  /* ── the Me-tab row ── */
  function sub() {
    var i = S.info;
    if (hasStore() && i) {
      if (i.forced && i.newer) return 'Update needed';
      if (i.state === 'downloading') return 'Downloading… ' + (i.pct | 0) + '%';
      if (i.state === 'installing') return 'Installing…';
      if (i.newer) return 'Update ready';
      return 'Up to date';
    }
    if (S.old && S.old.newer) return 'New app ready';
    return '';
  }
  function hot() { var i = S.info; return hasStore() ? !!(i && i.newer) : !!(S.old && S.old.newer); }
  function row() {
    css(); if (!native()) return '';
    setTimeout(nudge, 900);
    return '<button onclick="AmkStore.open()">📦 <span>App Store</span><small id="hmStore" class="' + (hot() ? 'as-hot' : '') + '" style="color:var(--soft)">' + esc(sub()) + '</small></button>';
  }
  function paintRow() { var s = $('hmStore'); if (!s) return; s.textContent = sub(); s.className = hot() ? 'as-hot' : ''; }

  /* ── pieces shared by the sheet and the cover ── */
  function bar(p) { return '<div class="as-bar"><i style="width:' + Math.max(0, Math.min(100, p | 0)) + '%"></i></div>'; }
  function actions(i, cover) {
    var st = i.state, h = '';
    if (st === 'downloading') return bar(i.pct) + '<p class="as-note">Downloading… ' + (i.pct | 0) + '%. Keep the internet on.</p>';
    if (st === 'installing') return bar(100) + '<p class="as-note">Almost done. Android is asking you to confirm: tap <b>Update</b> in the box that appears. Cannot see it? Pull down the notification list.</p>';
    if (st === 'error') h += '<div class="msg msg-error show" style="margin-top:10px">⚠️ ' + esc(i.err || 'The update did not finish.') + '</div>';
    if (i.newer) {
      if (S.perm || i.canInstall === false) h += '<p class="as-note">Android needs your OK first: switch on <b>Allow from this source</b> for this app, press Back, then tap Update again. You only do this once.</p>';
      h += '<button class="btn btn-primary" data-as="update" onclick="AmkStore.update()">' + (st === 'error' ? 'Try again' : '⬇ Update now') + '</button>';
    } else if (!cover) {
      h += '<button class="btn btn-outline" data-as="check" onclick="AmkStore.check()">' + (i.checking ? 'Checking…' : 'Check again') + '</button>';
    }
    return h;
  }
  function oldSteps() {
    return '<p class="as-note"><b>Install it once.</b> After that, updates arrive inside the app with one tap, and notifications reach you even when the app is closed.</p>' +
      '<ol class="as-steps"><li>Tap <b>Download new app</b>.</li><li>When it has downloaded, open the file (from the notification or the Downloads folder).</li><li>Tap <b>Install</b>. Allow it if Android asks. Your sign-in stays.</li></ol>' +
      '<a class="btn btn-primary" data-as="download" href="' + APK + '" style="text-decoration:none;text-align:center">⬇ Download new app</a>';
  }
  function card(build, chip, isNew) {
    return '<div class="as-card"><div class="as-ic">📦</div><div style="min-width:0"><b>Amitek Attendance</b><span>Installed: version 1.0.' + esc(build) + '</span></div><span class="as-chip' + (isNew ? ' new' : '') + '">' + esc(chip) + '</span></div>';
  }

  /* ── the sheet ── */
  function sheetHtml() {
    if (hasStore()) {
      var i = S.info || {};
      var h = card(i.build || '', i.newer ? 'Update ready' : 'Up to date', !!i.newer);
      if (i.newer) h += '<div class="as-new"><b>Version 1.0.' + esc(i.latest) + (i.size ? ' · ' + mb(i.size) : '') + '</b>' + (i.notes ? '<p>' + esc(i.notes) + '</p>' : '') + '</div>';
      else h += '<p class="as-note">You have the newest version. The app also looks for updates by itself and tells you when one is ready.</p>';
      h += '<div data-as="state">' + actions(i, false) + '</div>';
      h += '<p class="as-note" style="color:var(--soft);font-size:11.5px">Web ' + esc(window.AMK_PAGE_VERSION || '') + '</p>';
      return h;
    }
    var o = S.old, mine = uaBuild();
    if (o && o.newer) {
      return card(mine, 'New app ready', true) +
        '<div class="as-new"><b>Version 1.0.' + esc(o.latest) + (o.size ? ' · ' + mb(o.size) : '') + '</b>' + (o.notes ? '<p>' + esc(o.notes) + '</p>' : '') + '</div>' +
        oldSteps();
    }
    return card(mine || '', 'Up to date', false) +
      '<p class="as-note">' + (o ? 'You have the newest app.' : 'Tap below to look for a newer app.') + '</p>' +
      '<button class="btn btn-outline" data-as="check" onclick="AmkStore.check()">Check again</button>';
  }
  function sheetOpen() { var m = $('detailModal'), t = $('modalTitle'); return !!(m && m.classList.contains('open') && t && t.textContent === TITLE); }
  function paint() { paintRow(); if (sheetOpen()) $('modalBody').innerHTML = sheetHtml(); }
  function open() {
    css(); if (!native()) return;
    if (hasStore()) { refresh(); var i = S.info || {}; if (!i.checkedAt || Date.now() - i.checkedAt > 30 * 60e3) check(true); } else oldCheck(false);
    $('modalTitle').textContent = TITLE; $('modalBody').innerHTML = sheetHtml(); $('detailModal').classList.add('open');
  }

  /* ── actions ── */
  function check(quiet) {
    if (hasStore()) { try { bridge().updateCheck(); } catch (e) { } if (S.info && !quiet) { S.info.checking = true; paint(); } }
    else { oldCheck(true); }
  }
  function update() {
    if (!hasStore()) return;
    var r = ''; try { r = bridge().updateStart(); } catch (e) { }
    S.perm = r === 'permission';
    if (r === 'ok' && S.info) { S.info.state = 'downloading'; S.info.pct = 0; }
    if (r === 'none') { check(false); return; }
    paint(); gate();
  }

  /* ── forced update: the whole screen, nothing behind it can be used ── */
  function gate() {
    var g = $('amkGate'), i = S.info, inner = '';
    if (hasStore()) {
      if (i && i.forced && i.newer) inner = '<h3>📦 Update required</h3><p class="lead">This version of the app is too old to keep working. The update takes about a minute; your sign-in and data stay.</p>' +
        '<div class="as-new" style="margin-top:0"><b>Version 1.0.' + esc(i.latest) + (i.size ? ' · ' + mb(i.size) : '') + '</b>' + (i.notes ? '<p>' + esc(i.notes) + '</p>' : '') + '</div>' + actions(i, true);
    } else if (native() && S.old && S.old.newer && uaBuild() > 0 && S.min > uaBuild()) {
      inner = '<h3>📦 New app required</h3><p class="lead">This version of the app is too old to keep working. Installing the new one takes about a minute; your sign-in and data stay.</p>' +
        '<div class="as-new" style="margin-top:0"><b>Version 1.0.' + esc(S.old.latest) + (S.old.size ? ' · ' + mb(S.old.size) : '') + '</b>' + (S.old.notes ? '<p>' + esc(S.old.notes) + '</p>' : '') + '</div>' + oldSteps();
    }
    if (!inner) { if (g) g.remove(); return; }
    css();
    if (!g) { g = document.createElement('div'); g.id = 'amkGate'; g.setAttribute('role', 'alertdialog'); g.setAttribute('aria-modal', 'true'); document.body.appendChild(g); }
    g.innerHTML = '<div class="as-box">' + inner + '</div>';
  }

  /* an older app hears about the new one once per build, right after sign-in */
  function nudge() {
    var o = S.old; if (!o || !o.newer || hasStore()) return;
    try { if (typeof currentEmp === 'undefined' || !currentEmp) return; } catch (e) { return; }
    if (sheetOpen() || ls('amk_store_seen') === String(o.latest)) return;
    ls('amk_store_seen', String(o.latest)); open();
  }

  function init() {
    if (!native()) return;
    css();
    if (hasStore()) refresh(); else oldCheck(false);
    document.addEventListener('visibilitychange', function () { if (document.visibilityState === 'visible') { if (hasStore()) refresh(); else oldCheck(false); } });
  }

  window.AmkStore = { has: native, row: row, open: open, update: update, check: check, refresh: refresh, onUpdate: onUpdate, _state: S };
  if (window.AmkApp) window.AmkApp.onUpdate = onUpdate;
  init();
})();
