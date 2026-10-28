/* Policies: turns the text an admin writes into safe HTML, and fills in the company details.
   Used by the app (index.html) and by the public page (policy.html).  Text format, kept deliberately small:
     ## Heading      ### Smaller heading      - a bullet      **bold**      [link text](https://example.com)      a blank line starts a new paragraph
     {{company}} {{address}} {{email}} {{phone}} {{officer}}  are replaced with the company details (a missing detail shows as a marked blank, so nobody publishes a hole unseen).
   Everything the admin types is escaped first: no HTML or script typed into a policy ever runs. */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.AmkPolicy = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';
  var KEYS = { company: 'company name', address: 'company address', email: 'contact e-mail', phone: 'contact phone', officer: 'grievance officer' };

  function esc(s) {
    return String(s === null || s === undefined ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  // one line of text → HTML
  function inline(line, co) {
    co = co || {};
    var s = String(line).replace(/\{\{\s*([a-z]+)\s*\}\}/g, function (m, k) { return KEYS[k] ? '\u0001' + k + '\u0002' : m; });
    s = esc(s);
    s = s.replace(/\u0001([a-z]+)\u0002/g, function (m, k) {
      var raw = co[k] === undefined || co[k] === null || co[k] === '' ? (k === 'company' ? co.name : '') : co[k];   // the server sends the company name as "name"
      var v = String(raw === undefined || raw === null ? '' : raw).trim();
      return v ? esc(v) : '<span class="pl-blank">[' + KEYS[k] + ']</span>';
    });
    s = s.replace(/\*\*([^*\n]+)\*\*/g, '<b>$1</b>');
    s = s.replace(/\[([^\]\n]+)\]\(((?:https?:\/\/|mailto:)[^)\s]+)\)/g, function (m, t, u) { return '<a href="' + u + '" target="_blank" rel="noopener noreferrer">' + t + '</a>'; });
    return s;
  }

  // the whole text → HTML
  function render(text, co) {
    var out = [], list = false, lines = String(text === null || text === undefined ? '' : text).replace(/\r\n?/g, '\n').split('\n');
    var close = function () { if (list) { out.push('</ul>'); list = false; } };
    lines.forEach(function (raw) {
      var line = raw.replace(/\s+$/, ''), m;
      if (!line.trim()) { close(); return; }
      if ((m = /^###\s+(.*)$/.exec(line))) { close(); out.push('<h4>' + inline(m[1], co) + '</h4>'); return; }
      if ((m = /^##\s+(.*)$/.exec(line))) { close(); out.push('<h3>' + inline(m[1], co) + '</h3>'); return; }
      if ((m = /^\s*[-*]\s+(.*)$/.exec(line))) { if (!list) { out.push('<ul>'); list = true; } out.push('<li>' + inline(m[1], co) + '</li>'); return; }
      close(); out.push('<p>' + inline(line, co) + '</p>');
    });
    close();
    return '<div class="pl-doc">' + out.join('') + '</div>';
  }

  // text with every [bracketed prompt] the author has not replaced yet (links like [text](url) do not count)
  function blanks(text) {
    var found = [], re = /\[([A-Za-z\u0900-\u097F][^\]\n]{0,80})\](?!\()/g, m;
    while ((m = re.exec(String(text || '')))) found.push(m[1]);
    return found;
  }

  // the unknown {{names}} an author typed by mistake
  function unknownKeys(text) {
    var bad = [], re = /\{\{\s*([A-Za-z0-9_]+)\s*\}\}/g, m;
    while ((m = re.exec(String(text || '')))) if (!KEYS[m[1]] && bad.indexOf(m[1]) < 0) bad.push(m[1]);
    return bad;
  }

  return { KEYS: KEYS, esc: esc, inline: inline, render: render, blanks: blanks, unknownKeys: unknownKeys };
});
