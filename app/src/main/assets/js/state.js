// ── Nova State & Utilities ──────────────────
// Must load first — defines window.Nova.state and shared helpers.

window.Nova = window.Nova || {};

Nova.state = {
  currentConvId: '',
  messages: [],
  attachedB64: '',
  attachedMime: '',
  generatedTitle: false,
  _optResult: '',
  _sidebarDirty: false,
  _sidebarTimer: 0,
  _streamMsgId: '',
  _ctxConvId: '',
  _longPressTimer: null,
  _lastApiMsgs: []     // stored API messages for stream retry
};

// ── Marked.js init ────────────────────────────
function initMarked() {
  if (typeof marked === 'undefined') return;
  marked.setOptions({ breaks: true, gfm: true });
  if (typeof hljs !== 'undefined') {
    marked.setOptions({
      highlight: function(code, lang) {
        if (lang && hljs.getLanguage(lang)) {
          try { return hljs.highlight(code, { language: lang }).value; }
          catch(e) {}
        }
        return code;
      }
    });
  }
}

// ── Shared utilities ─────────────────────────
function renderContent(text) {
  if (typeof marked !== 'undefined' && text) {
    try { return marked.parse(text); }
    catch(e) { return escHtml(text); }
  }
  return escHtml(text);
}

function escHtml(s) {
  return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function setSendEnabled(v) {
  var btn = document.getElementById('btnSend');
  btn.disabled = !v;
  btn.style.opacity = v ? '1' : '.4';
}

function showConfigHint() {
  var h = document.getElementById('configHint');
  h.style.display = 'block';
  setTimeout(function() { h.style.display = 'none'; }, 3000);
}

function checkConfig() {
  var hint = document.getElementById('configHint');
  if (!native.hasConfig()) {
    hint.style.display = 'block';
  } else {
    hint.style.display = 'none';
  }
}
