// ── Stream handlers ──────────────────────────
// Depends on: state.js

function onChatStreamStart(msgId) {
  Nova.state._streamMsgId = msgId;
  var wrap = document.createElement('div');
  wrap.className = 'msg-row assistant';
  wrap.id = msgId;
  wrap.innerHTML = '<div class="msg-bubble"><span class="stream-cursor">|</span></div>';
  document.getElementById('msgList').appendChild(wrap);
  setSendEnabled(false);
  // Switch send button to stop button
  showStopButton();
  scrollBottom();
}

function onChatStreamBatch(msgId, batch) {
  var wrap = document.getElementById(msgId);
  if (!wrap) return;
  var bubble = wrap.querySelector('.msg-bubble');
  if (!bubble) return;
  var html = bubble.innerHTML.replace('<span class="stream-cursor">|</span>', '');
  var div = document.createElement('div');
  div.textContent = batch;
  html += div.innerHTML;
  html += '<span class="stream-cursor">|</span>';
  bubble.innerHTML = html;
  scrollBottom();
}

function onChatStreamEnd(msgId) {
  Nova.state._streamMsgId = '';
  setSendEnabled(true);
  hideStopButton();
  var wrap = document.getElementById(msgId);
  if (!wrap) return;
  var bubble = wrap.querySelector('.msg-bubble');
  if (!bubble) return;
  bubble.innerHTML = bubble.innerHTML.replace('<span class="stream-cursor">|</span>', '');
  var text = bubble.textContent;
  Nova.state.messages.push({ role: 'assistant', content: text });
  native.updateMessages(JSON.stringify(Nova.state.messages));
  wrap.id = '';
}

function onChatStreamError(msgId, err) {
  Nova.state._streamMsgId = '';
  setSendEnabled(true);
  hideStopButton();
  var wrap = document.getElementById(msgId);
  if (!wrap) return;
  var bubble = wrap.querySelector('.msg-bubble');
  if (!bubble) { wrap.remove(); return; }
  // Preserve partial content, replace cursor with retry badge
  bubble.innerHTML = bubble.innerHTML.replace('<span class="stream-cursor">|</span>', '');
  var retryHtml = '<div class="stream-retry">' +
    '<span class="stream-retry-msg">连接中断</span>' +
    '<button class="stream-retry-btn" onclick="event.stopPropagation();retryLastStream()" title="重试">' +
      '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>' +
      ' 重试' +
    '</button>' +
  '</div>';
  bubble.innerHTML += retryHtml;
}

// ── Stream retry ─────────────────────────────
function retryLastStream() {
  if (Nova.state._lastApiMsgs.length === 0) return;
  // Remove all failed partial bubbles
  var failed = document.querySelectorAll('.stream-retry');
  for (var i = 0; i < failed.length; i++) {
    var row = failed[i].closest('.msg-row');
    if (row) row.remove();
  }
  native.cancelCurrentStream();
  native.sendMessage(JSON.stringify({ messages: Nova.state._lastApiMsgs }));
}

function onChatStreamCancel(msgId) {
  Nova.state._streamMsgId = '';
  setSendEnabled(true);
  hideStopButton();
  var wrap = document.getElementById(msgId);
  if (wrap) wrap.remove();
}

// ── Stop button control ──────────────────────
function showStopButton() {
  var btn = document.getElementById('btnSend');
  btn.classList.add('stop');
  btn.innerHTML = '<svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="2"/></svg>';
  btn.onclick = stopGeneration;
  btn.disabled = false;
  btn.style.opacity = '1';
}

function hideStopButton() {
  var btn = document.getElementById('btnSend');
  btn.classList.remove('stop');
  btn.innerHTML = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>';
  btn.onclick = sendMsg;
}

function stopGeneration() {
  native.cancelCurrentStream();
  hideStopButton();
  setSendEnabled(true);
}
