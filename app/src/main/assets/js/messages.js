// ── Messages ──────────────────────────────────
// Depends on: state.js

function loadMessages() {
  Nova.state.messages = JSON.parse(native.getMessages());
  renderMessages();
  updateWelcome();
  scrollBottom();
}

function renderMessages() {
  var el = document.getElementById('msgList');
  var msgs = Nova.state.messages;
  // Incremental update: only add new messages since last render
  var existing = el.querySelectorAll('.msg-row');
  var existingCount = existing.length;
  if (msgs.length < existingCount) {
    // Messages were removed (e.g. conversation switch) — full rebuild
    el.innerHTML = msgs.map(function(m) { return buildMessageHTML(m); }).join('');
  } else if (msgs.length > existingCount) {
    // Append only new messages
    for (var i = existingCount; i < msgs.length; i++) {
      var node = buildMessageNode(msgs[i]);
      el.appendChild(node);
    }
  } else if (msgs.length === existingCount && existingCount > 0) {
    // Same count — check if content changed (e.g. stream end markdown re-render)
    var lastBubble = existing[existingCount - 1].querySelector('.msg-bubble');
    if (lastBubble && !lastBubble.querySelector('.stream-cursor')) {
      // Already rendered, skip
    }
  }
  updateWelcome();
}

function updateWelcome() {
  var welcome = document.getElementById('welcomePage');
  if (Nova.state.messages.length === 0) {
    welcome.classList.remove('hidden');
    document.getElementById('msgList').style.display = 'none';
  } else {
    welcome.classList.add('hidden');
    document.getElementById('msgList').style.display = '';
  }
}

function appendMessage(role, content, imgUrl, imgPrompt) {
  Nova.state.messages.push({
    role: role,
    content: content,
    imageUrl: imgUrl || null,
    imagePrompt: imgPrompt || ''
  });
  var node = buildMessageNode(Nova.state.messages[Nova.state.messages.length - 1]);
  document.getElementById('msgList').appendChild(node);
  scrollBottom();
  native.updateMessages(JSON.stringify(Nova.state.messages));
}

// ── Shared bubble HTML builder ──────────────────
// Used by both buildMessageHTML (string) and buildMessageNode (DOM)
function buildBubbleHTML(m) {
  if (m.imageUrl) {
    return '<div>' + escHtml(m.content) + '</div>' +
      '<div class="img-wrap">' +
        '<img class="msg-img" src="' + m.imageUrl + '" onclick="event.stopPropagation()" alt="图片">' +
        '<div class="img-actions">' +
          '<button class="img-btn" onclick="event.stopPropagation();downloadImg(\'' + m.imageUrl + '\', \'AIChat_' + Date.now() + '.png\')" title="保存图片">' +
            '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>' +
          '</button>' +
        '</div>' +
      '</div>' +
      (m.imagePrompt ? '<div class="msg-prompt">提示词: ' + escHtml(m.imagePrompt) + '</div>' : '');
  }
  var content = m.role === 'assistant' ? renderContent(m.content) : escHtml(m.content);
  var copyBtn = '<button class="msg-act-btn" onclick="event.stopPropagation();copyMessage(this)" title="复制">' +
    '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>' +
    '</button>';
  var regenBtn = m.role === 'assistant'
    ? '<button class="msg-act-btn" onclick="event.stopPropagation();regenerateMsg()" title="重新生成"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg></button>'
    : '';
  var actions = '<div class="msg-actions">' + copyBtn + regenBtn + '</div>';
  return content + actions;
}

function buildMessageNode(m) {
  var row = document.createElement('div');
  row.className = 'msg-row ' + m.role;
  var bubble = document.createElement('div');
  bubble.className = 'msg-bubble';
  bubble.innerHTML = buildBubbleHTML(m);
  row.appendChild(bubble);
  return row;
}

function buildMessageHTML(m) {
  return '<div class="msg-row ' + m.role + '">' +
    '<div class="msg-bubble">' + buildBubbleHTML(m) + '</div></div>';
}

function addLoading() {
  var el = document.getElementById('msgList');
  el.insertAdjacentHTML('beforeend',
    '<div class="msg-row assistant"><div class="msg-loading"><span class="dot"></span><span class="dot"></span><span class="dot"></span></div></div>');
  scrollBottom();
}

function removeLoading() {
  var loaders = document.querySelectorAll('.msg-loading');
  for (var i = 0; i < loaders.length; i++) {
    loaders[i].parentElement.remove();
  }
}

function scrollBottom() {
  requestAnimationFrame(function() {
    var el = document.getElementById('msgList');
    el.scrollTop = el.scrollHeight;
  });
}

// ── Image generation pipeline ────────────────
function handleGenImage(prompt) {
  if (Nova.state.attachedB64) {
    if (!native.hasVisionConfig() || !native.hasImageConfig()) {
      appendMessage('assistant', '图生图需要图片识别API和图片API配置');
      return;
    }
    var userText = '[生成图片：' + prompt + ' (附图)]';
    appendMessage('user', userText);
    addLoading();
    native.analyzeImage(Nova.state.attachedB64, Nova.state.attachedMime, prompt);
    clearAttachment();
  } else {
    if (!native.hasImageConfig()) {
      appendMessage('assistant', '请先在设置中配置图片 API');
      return;
    }
    appendMessage('user', '[生成图片：' + prompt + ']');
    addLoading();
    native.optimizePrompt(prompt, '', '');
  }
}

function onImageAnalysisResult(genPrompt) {
  removeLoading();
  native.generateImage(genPrompt);
  addLoading();
}

function onImageAnalysisError(err) {
  removeLoading();
  appendMessage('assistant', '图片分析失败: ' + err);
  setSendEnabled(true);
}

function onImageResult(url, prompt) {
  removeLoading();
  appendMessage('assistant', '已生成图片', url, prompt);
  setSendEnabled(true);
}

function onImageError(err) {
  removeLoading();
  appendMessage('assistant', '图片生成失败: ' + err);
  setSendEnabled(true);
}

// ── Regenerate ────────────────────────────────
function regenerateMsg() {
  if (Nova.state.messages.length < 2) return;
  // Cancel any active stream before regenerating
  native.cancelCurrentStream();
  // Remove last assistant message and re-send
  var last = Nova.state.messages[Nova.state.messages.length - 1];
  if (last.role !== 'assistant') return;
  Nova.state.messages.pop();
  native.updateMessages(JSON.stringify(Nova.state.messages));
  renderMessages();

  var apiMsgs = Nova.state.messages
    .filter(function(m) { return !m.imageUrl; })
    .map(function(m) { return { role: m.role, content: m.content }; });
  Nova.state._lastApiMsgs = apiMsgs;
  addLoading();
  setSendEnabled(false);
  native.sendMessage(JSON.stringify({ messages: apiMsgs }));
}

// ── Copy ─────────────────────────────────────
function copyMessage(btn) {
  var bubble = btn.closest('.msg-bubble');
  if (!bubble) return;
  // Get plain text, excluding action buttons and thinking blocks
  var clone = bubble.cloneNode(true);
  // Remove action buttons and thinking blocks
  var actions = clone.querySelectorAll('.msg-actions, .thinking-block');
  for (var i = 0; i < actions.length; i++) actions[i].remove();
  var text = clone.textContent.trim();
  if (navigator.clipboard) {
    navigator.clipboard.writeText(text).then(function() {
      showCopyToast(btn);
    });
  } else {
    // Fallback for older WebViews
    var ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.left = '-9999px';
    document.body.appendChild(ta);
    ta.select();
    document.execCommand('copy');
    document.body.removeChild(ta);
    showCopyToast(btn);
  }
}

function showCopyToast(btn) {
  var orig = btn.innerHTML;
  btn.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>';
  btn.style.color = '#4CAF50';
  setTimeout(function() {
    btn.innerHTML = orig;
    btn.style.color = '';
  }, 1500);
}

// ── Download ──────────────────────────────────
function downloadImg(url, filename) {
  if (url.startsWith('data:')) {
    var a = document.createElement('a');
    a.href = url;
    a.download = filename || 'image.png';
    a.click();
  } else {
    native.downloadImage(url, filename || 'image.png');
  }
}
