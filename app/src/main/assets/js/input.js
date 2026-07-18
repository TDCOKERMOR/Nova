// ── Input & Send ─────────────────────────────
// Depends on: state.js

function sendMsg() {
  var input = document.getElementById('msgInput');
  var text = input.value.trim();
  if (!text) return;

  // Check /生图 command
  var m = text.match(/^\/生图\s+(.+)/);
  if (m) {
    handleGenImage(m[1]);
    input.value = '';
    autoResizeInput();
    return;
  }

  if (!native.hasConfig()) {
    showConfigHint();
    return;
  }

  appendMessage('user', text);
  input.value = '';
  autoResizeInput();
  setSendEnabled(false);

  var apiMsgs = Nova.state.messages
    .filter(function(m) { return !m.imageUrl; })
    .map(function(m) { return { role: m.role, content: m.content }; });
  // Store for potential stream retry
  Nova.state._lastApiMsgs = apiMsgs;
  native.sendMessage(JSON.stringify({ messages: apiMsgs }));

  if (!Nova.state.generatedTitle && Nova.state.messages.length >= 1) {
    var firstUser = Nova.state.messages.find(function(m) { return m.role === 'user'; });
    if (firstUser) {
      Nova.state.generatedTitle = true;
      native.generateTitle(firstUser.content);
    }
  }
}

function onInputKey(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendMsg();
  }
}

function autoResizeInput() {
  var ta = document.getElementById('msgInput');
  ta.style.height = 'auto';
  ta.style.height = Math.min(ta.scrollHeight, 120) + 'px';
  document.getElementById('btnSend').classList.toggle('active', ta.value.trim().length > 0);
}

// ── Quick input ──────────────────────────────
function setInput(text) {
  document.getElementById('msgInput').value = text;
  autoResizeInput();
  document.getElementById('msgInput').focus();
}

// ── Attachment ────────────────────────────────
function onImagePicked(b64, mime, size) {
  Nova.state.attachedB64 = b64;
  Nova.state.attachedMime = mime;
  var preview = document.getElementById('attachPreview');
  var img = document.getElementById('attachImg');
  img.src = 'data:' + mime + ';base64,' + b64;
  preview.style.display = 'flex';
}

function clearAttachment() {
  Nova.state.attachedB64 = '';
  Nova.state.attachedMime = '';
  document.getElementById('attachPreview').style.display = 'none';
  document.getElementById('attachImg').src = '';
}

// ── Optimize callbacks (from sendMsg & handleGenImage) ──
function onOptimizeResultFromSend(opt) {
  removeLoading();
  native.generateImage(opt);
  addLoading();
}

function onOptimizeErrorFromSend(err) {
  removeLoading();
  appendMessage('assistant', '优化失败: ' + err);
  setSendEnabled(true);
}
