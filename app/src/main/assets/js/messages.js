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
  el.innerHTML = Nova.state.messages.map(function(m) { return buildMessageHTML(m); }).join('');
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
  var regenBtn = m.role === 'assistant'
    ? '<div class="msg-actions"><button class="msg-act-btn" onclick="event.stopPropagation();regenerateMsg()" title="重新生成"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg></button></div>'
    : '';
  return content + regenBtn;
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

function onChatReply(reply) {
  removeLoading();
  appendMessage('assistant', reply);
  setSendEnabled(true);
}

function onChatError(err) {
  removeLoading();
  appendMessage('assistant', '错误: ' + err);
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
