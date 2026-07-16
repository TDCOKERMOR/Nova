// ── State ────────────────────────────────────
let currentConvId = '';
let messages = [];
let attachedB64 = '';
let attachedMime = '';
let generatedTitle = false;
let _optResult = '';
let _sidebarDirty = false;
let _sidebarTimer = 0;

// ── Init ──────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  currentConvId = native.getCurrentId();
  refreshSidebar();
  loadMessages();
  checkConfig();
  autoResizeInput();
});

// ── Sidebar ────────────────────────────────────
function toggleSidebar() {
  const sb = document.getElementById('sidebar');
  const ov = document.getElementById('sidebarOverlay');
  sb.classList.toggle('open');
  ov.classList.toggle('show');
}
function closeSidebar() {
  document.getElementById('sidebar').classList.remove('open');
  document.getElementById('sidebarOverlay').classList.remove('show');
}

function refreshSidebar() {
  _sidebarDirty = true;
  if (_sidebarTimer) return;
  _sidebarTimer = setTimeout(() => {
    _sidebarTimer = 0;
    if (!_sidebarDirty) return;
    _sidebarDirty = false;
    doRefreshSidebar();
  }, 100);
}

function doRefreshSidebar() {
  const list = document.getElementById('convList');
  const convs = JSON.parse(native.getConversations());
  const curId = native.getCurrentId();
  currentConvId = curId;

  // Diff-based update to avoid flicker
  const existing = list.querySelectorAll('.conv-item');
  if (existing.length === convs.length) {
    // Same count: update in-place
    convs.forEach((c, i) => {
      const el = existing[i];
      const wasActive = el.classList.contains('active');
      const wasPinned = el.classList.contains('pinned');
      const isActive = c.id === curId;
      const isPinned = c.pinned;
      if (wasActive !== isActive) el.classList.toggle('active', isActive);
      if (wasPinned !== isPinned) el.classList.toggle('pinned', isPinned);
      const pinMark = el.querySelector('.pin-mark');
      if (pinMark) pinMark.textContent = isPinned ? '📌' : '';
      const titleEl = el.querySelector('.title');
      if (titleEl && titleEl.textContent !== c.title) titleEl.textContent = c.title;
      // Update onclick attributes (they may have stale closure)
      el.onclick = () => selectConv(c.id);
      el.onmousedown = (e) => onConvMouseDown(e, c.id);
      el.ontouchstart = (e) => onConvTouchStart(e, c.id);
    });
  } else {
    // Count changed: full rebuild
    list.innerHTML = convs.map(c => buildConvItem(c, curId)).join('');
  }
}

function buildConvItem(c, curId) {
  return `<div class="conv-item${c.id===curId?' active':''}${c.pinned?' pinned':''}" 
    oncontextmenu="return false"
    onmousedown="onConvMouseDown(event,'${c.id}')"
    ontouchstart="onConvTouchStart(event,'${c.id}')"
    onclick="selectConv('${c.id}')">
    <span class="pin-mark">${c.pinned?'📌':''}</span>
    <span class="title">${escHtml(c.title)}</span>
  </div>`;
}

function onSearch(q) {
  const list = document.getElementById('convList');
  if (!q.trim()) { doRefreshSidebar(); return; }
  const convs = JSON.parse(native.searchConversations(q));
  if (convs.length === 0) {
    list.innerHTML = '<div style="padding:20px;text-align:center;color:var(--sub);font-size:13px">无匹配结果</div>';
  } else {
    list.innerHTML = convs.map(c => buildConvItem(c, currentConvId)).join('');
  }
}

function selectConv(id) {
  closeSidebar();
  if (id === currentConvId) return;
  native.switchConversation(id);
  currentConvId = id;
  loadMessages();
  generatedTitle = true;
  refreshSidebar();
}

function deleteConv(id) {
  native.deleteConversation(id);
  if (currentConvId === id) {
    native.newConversation();
    currentConvId = native.getCurrentId();
    loadMessages();
    generatedTitle = false;
  }
  refreshSidebar();
}

function newChat() {
  closeSidebar();
  native.newConversation();
  currentConvId = native.getCurrentId();
  loadMessages();
  generatedTitle = false;
  refreshSidebar();
}

// ── Messages ───────────────────────────────────
function loadMessages() {
  messages = JSON.parse(native.getMessages());
  renderMessages();
  scrollBottom();
}

function renderMessages() {
  const el = document.getElementById('msgList');
  el.innerHTML = messages.map(m => {
    if (m.imageUrl) {
      return `<div class="msg-row assistant">
        <div class="msg-bubble">
          <div>${escHtml(m.content)}</div>
          <div class="img-wrap">
            <img class="msg-img" src="${m.imageUrl}" onclick="event.stopPropagation()" alt="图片">
            <div class="img-actions">
              <button class="img-btn" onclick="event.stopPropagation();downloadImg('${m.imageUrl}', 'AIChat_${Date.now()}.png')" title="保存图片">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
              </button>
            </div>
          </div>
          ${m.imagePrompt?`<div class="msg-prompt">提示词: ${escHtml(m.imagePrompt)}</div>`:''}
        </div>
      </div>`;
    }
    return `<div class="msg-row ${m.role}">
      <div class="msg-bubble">${escHtml(m.content)}</div>
    </div>`;
  }).join('');
}

function appendMessage(role, content, imgUrl, imgPrompt) {
  messages.push({role, content, imageUrl:imgUrl||null, imagePrompt:imgPrompt||''});
  renderMessages();
  scrollBottom();
  native.updateMessages(JSON.stringify(messages));
}

function addLoading() {
  const el = document.getElementById('msgList');
  el.insertAdjacentHTML('beforeend',
    '<div class="msg-row assistant"><div class="msg-loading"><span class="dot"></span><span class="dot"></span><span class="dot"></span></div></div>');
  scrollBottom();
}
function removeLoading() {
  const loaders = document.querySelectorAll('.msg-loading');
  loaders.forEach(l => l.parentElement.remove());
}

function scrollBottom() {
  requestAnimationFrame(() => {
    const el = document.getElementById('msgList');
    el.scrollTop = el.scrollHeight;
  });
}

// ── Send message ──────────────────────────────
function sendMsg() {
  const input = document.getElementById('msgInput');
  const text = input.value.trim();
  if (!text) return;

  // Check /生图 command
  const m = text.match(/^\/生图\s+(.+)/);
  if (m) {
    handleGenImage(m[1]);
    input.value = '';
    input.style.height = 'auto';
    input.style.height = '20px';
    return;
  }

  if (!native.hasConfig()) {
    showConfigHint();
    return;
  }

  appendMessage('user', text);
  input.value = '';
  setTimeout(() => {
    input.value = '';
    input.style.height = 'auto';
    input.style.height = '20px';
  }, 0);
  setSendEnabled(false);

  const apiMsgs = messages.filter(m => !m.imageUrl).map(m => ({role:m.role,content:m.content}));
  native.sendMessage(JSON.stringify({messages: apiMsgs}));

  if (!generatedTitle && messages.length >= 1) {
    const firstUser = messages.find(m => m.role==='user');
    if (firstUser) {
      generatedTitle = true;
      native.generateTitle(firstUser.content);
    }
  }
}

// Streaming handlers
let _streamMsgId = '';
function onChatStreamStart(msgId) {
  _streamMsgId = msgId;
  const wrap = document.createElement('div');
  wrap.className = 'msg-row assistant';
  wrap.id = msgId;
  wrap.innerHTML = '<div class="msg-bubble"><span class="stream-cursor">|</span></div>';
  document.getElementById('msgList').appendChild(wrap);
  setSendEnabled(false);
  scrollBottom();
}
function onChatStreamToken(msgId, token) {
  const wrap = document.getElementById(msgId);
  if (!wrap) return;
  const bubble = wrap.querySelector('.msg-bubble');
  if (!bubble) return;
  let html = bubble.innerHTML.replace('<span class="stream-cursor">|</span>', '');
  const div = document.createElement('div');
  div.textContent = token;
  html += div.innerHTML;
  html += '<span class="stream-cursor">|</span>';
  bubble.innerHTML = html;
  scrollBottom();
}
function onChatStreamEnd(msgId) {
  _streamMsgId = '';
  setSendEnabled(true);
  const wrap = document.getElementById(msgId);
  if (!wrap) return;
  const bubble = wrap.querySelector('.msg-bubble');
  if (!bubble) return;
  bubble.innerHTML = bubble.innerHTML.replace('<span class="stream-cursor">|</span>', '');
  const text = bubble.textContent;
  messages.push({role:'assistant', content:text});
  native.updateMessages(JSON.stringify(messages));
  wrap.id = '';
}
function onChatStreamError(msgId, err) {
  _streamMsgId = '';
  setSendEnabled(true);
  const wrap = document.getElementById(msgId);
  if (wrap) wrap.remove();
  appendMessage('assistant', '错误: ' + err);
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

// ── Image generation ───────────────────────────
function handleGenImage(prompt) {
  if (attachedB64) {
    if (!native.hasVisionConfig() || !native.hasImageConfig()) {
      appendMessage('assistant', '图生图需要图片识别API和图片API配置');
      return;
    }
    const userText = '[生成图片：' + prompt + ' (附图)]';
    appendMessage('user', userText);
    addLoading();
    native.analyzeImage(attachedB64, attachedMime, prompt);
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

function onOptimizeResult(opt) {
  // Called when optimizePrompt inside handleGenImage returns
  removeLoading();
  native.generateImage(opt);
  addLoading();
}

function onOptimizeError(err) {
  removeLoading();
  appendMessage('assistant', '优化失败: ' + err);
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

// ── Attachment ─────────────────────────────────
function onImagePicked(b64, mime, size) {
  attachedB64 = b64;
  attachedMime = mime;
  const preview = document.getElementById('attachPreview');
  const img = document.getElementById('attachImg');
  img.src = 'data:' + mime + ';base64,' + b64;
  preview.style.display = 'flex';
}

function clearAttachment() {
  attachedB64 = '';
  attachedMime = '';
  document.getElementById('attachPreview').style.display = 'none';
  document.getElementById('attachImg').src = '';
}

// ── Optimize dialog ────────────────────────────
function openOptimize() {
  document.getElementById('optimizeOverlay').classList.add('show');
  document.getElementById('optimizeDialog').classList.add('show');
  document.getElementById('optResult').style.display = 'none';
  document.getElementById('optLoading').style.display = 'none';
  document.getElementById('optPrompt').value = '';
  document.getElementById('optStyle').value = '';
  document.getElementById('optSize').value = '';
  document.getElementById('optBtn').style.display = '';
}

function closeOptimize() {
  document.getElementById('optimizeOverlay').classList.remove('show');
  document.getElementById('optimizeDialog').classList.remove('show');
}

function doOptimize() {
  const prompt = document.getElementById('optPrompt').value.trim();
  if (!prompt) return;
  const style = document.getElementById('optStyle').value.trim();
  const size = document.getElementById('optSize').value.trim();

  document.getElementById('optBtn').style.display = 'none';
  document.getElementById('optLoading').style.display = 'block';
  document.getElementById('optResult').style.display = 'none';

  native.optimizePrompt(prompt, style, size);
}

function onOptimizePromptResult(text) {
  _optResult = text;
  document.getElementById('optLoading').style.display = 'none';
  document.getElementById('optResultText').textContent = text;
  document.getElementById('optResult').style.display = 'block';
}

function onOptimizePromptError(err) {
  document.getElementById('optLoading').style.display = 'none';
  document.getElementById('optBtn').style.display = '';
  alert('优化失败: ' + err);
}

function confirmOptimize() {
  document.getElementById('msgInput').value = '/生图 ' + _optResult;
  autoResizeInput();
  closeOptimize();
}

// Map native callbacks (they come via evaluateJavascript)
function onOptimizeResult(text) {
  if (document.getElementById('optimizeDialog').classList.contains('show')) {
    onOptimizePromptResult(text);
  } else {
    // Called from handleGenImage flow
    removeLoading();
    native.generateImage(text);
    addLoading();
  }
}

// ── Helpers ────────────────────────────────────
function setSendEnabled(v) {
  document.getElementById('btnSend').disabled = !v;
  document.getElementById('btnSend').style.opacity = v ? '1' : '.4';
}

function showConfigHint() {
  const h = document.getElementById('configHint');
  h.style.display = 'block';
  setTimeout(() => h.style.display = 'none', 3000);
}

function checkConfig() {
  if (!native.hasConfig()) {
    document.getElementById('configHint').style.display = 'block';
  } else {
    document.getElementById('configHint').style.display = 'none';
  }
}

function onInputKey(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendMsg();
  }
}

function autoResizeInput() {
  const ta = document.getElementById('msgInput');
  ta.style.height = 'auto';
  ta.style.height = Math.min(ta.scrollHeight, 120) + 'px';
  document.getElementById('btnSend').classList.toggle('active', ta.value.trim().length > 0);
}

function escHtml(s) {
  return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

document.getElementById('msgInput').addEventListener('input', autoResizeInput);
autoResizeInput();  // init send button state

// ── Long-press context menu ────────────────────
let _longPressTimer;
function onConvTouchStart(e, id) {
  _longPressTimer = setTimeout(() => showCtxMenu(e.touches[0].clientX, e.touches[0].clientY, id), 500);
}
function onConvMouseDown(e, id) {
  if (e.button !== 0) { showCtxMenu(e.clientX, e.clientY, id); return; }
  _longPressTimer = setTimeout(() => showCtxMenu(e.clientX, e.clientY, id), 600);
}
document.addEventListener('mouseup', () => clearTimeout(_longPressTimer));
document.addEventListener('touchend', () => clearTimeout(_longPressTimer));
document.addEventListener('touchmove', () => clearTimeout(_longPressTimer));

function showCtxMenu(x, y, id) {
  _ctxConvId = id;
  const menu = document.getElementById('ctxMenu');
  const pinItem = document.getElementById('ctxPin');
  const isPinned = native.isPinned(id);
  pinItem.querySelector('span').textContent = isPinned ? '取消置顶' : '置顶';
  menu.style.display = 'block';
  menu.style.left = Math.min(x, window.innerWidth - 160) + 'px';
  menu.style.top = Math.min(y, window.innerHeight - 160) + 'px';
}

function hideCtxMenu() {
  document.getElementById('ctxMenu').style.display = 'none';
  _ctxConvId = '';
}
document.addEventListener('click', (e) => {
  if (!e.target.closest('.ctx-menu')) hideCtxMenu();
});

function downloadImg(url, filename) {
  if (url.startsWith('data:')) {
    // base64: decode and trigger download via blob
    const a = document.createElement('a');
    a.href = url;
    a.download = filename || 'image.png';
    a.click();
  } else {
    native.downloadImage(url, filename || 'image.png');
  }
}

function ctxPin() {
  native.pinConversation(_ctxConvId);
  refreshSidebar();
  hideCtxMenu();
}

function ctxRename() {
  const newTitle = prompt('输入新标题：');
  if (newTitle !== null && newTitle.trim()) {
    native.renameConversation(_ctxConvId, newTitle.trim());
    refreshSidebar();
  }
  hideCtxMenu();
}

function ctxDelete() {
  if (confirm('确定删除这个对话？')) {
    native.deleteConversation(_ctxConvId);
    if (currentConvId === _ctxConvId) {
      native.newConversation();
      currentConvId = native.getCurrentId();
      loadMessages();
      generatedTitle = false;
    }
    refreshSidebar();
  }
  hideCtxMenu();
}
