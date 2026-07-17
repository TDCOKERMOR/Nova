// ── Stream handlers ──────────────────────────
// Depends on: state.js

function onChatStreamStart(msgId) {
  Nova.state._streamMsgId = msgId;
  Nova.state._reasoningText = '';
  var wrap = document.createElement('div');
  wrap.className = 'msg-row assistant';
  wrap.id = msgId;
  wrap.innerHTML = '<div class="msg-bubble"><span class="stream-cursor">|</span></div>';
  document.getElementById('msgList').appendChild(wrap);
  setSendEnabled(false);
  showStopButton();
  scrollBottom();
}

function onChatStreamBatch(msgId, batch) {
  var wrap = document.getElementById(msgId);
  if (!wrap) return;
  var bubble = wrap.querySelector('.msg-bubble');
  if (!bubble) return;

  // Detect reasoning markers
  if (batch === '\u0000RSTART\u0000') {
    // Start of reasoning block — create a collapsible thinking section
    Nova.state._reasoningText = '';
    // Insert thinking block before the cursor
    var thinkingHtml = '<div class="thinking-block"><details open><summary class="thinking-summary">💭 思考中...</summary><div class="thinking-content" id="thinking-' + msgId + '"></div></details></div>';
    bubble.innerHTML = bubble.innerHTML.replace('<span class="stream-cursor">|</span>', thinkingHtml + '<span class="stream-cursor">|</span>');
    return;
  }

  if (batch === '\u0000REND\u0000') {
    // End of reasoning — close details so subsequent content goes to normal flow
    var thinkingEl = document.getElementById('thinking-' + msgId);
    if (thinkingEl) {
      var details = thinkingEl.closest('details');
      if (details) {
        details.open = false;
        var summary = details.querySelector('.thinking-summary');
        if (summary) summary.textContent = '🧠 已深度思考';
      }
    }
    return;
  }

  // Check if we're in reasoning mode (thinking block exists and is open)
  var thinkingEl = document.getElementById('thinking-' + msgId);
  if (thinkingEl && thinkingEl.closest('details').open) {
    Nova.state._reasoningText += batch;
    thinkingEl.textContent += batch;
    scrollBottom();
    return;
  }

  // Normal content streaming
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

  // Close reasoning block if still open
  var details = bubble.querySelector('details[open]');
  if (details) details.open = false;

  // Extract main content (excluding thinking block) for message storage
  var mainText = extractMainText(bubble);
  Nova.state.messages.push({ role: 'assistant', content: mainText });
  native.updateMessages(JSON.stringify(Nova.state.messages));
  wrap.id = '';

  // Re-render markdown on the final content, preserving thinking block
  var thinkingBlock = bubble.querySelector('.thinking-block');
  if (thinkingBlock) {
    // Remove all siblings after thinking block, then append markdown-rendered content
    while (thinkingBlock.nextSibling) {
      thinkingBlock.nextSibling.remove();
    }
    if (mainText) {
      var mdDiv = document.createElement('div');
      mdDiv.innerHTML = renderContent(mainText);
      bubble.appendChild(mdDiv);
    }
  } else {
    bubble.innerHTML = renderContent(mainText);
  }
}

/** Extract plain text from a bubble, skipping thinking-block content */
function extractMainText(bubble) {
  var clone = bubble.cloneNode(true);
  var thinking = clone.querySelector('.thinking-block');
  if (thinking) thinking.remove();
  return clone.textContent.trim();
}

function onChatStreamError(msgId, err) {
  Nova.state._streamMsgId = '';
  setSendEnabled(true);
  hideStopButton();
  var wrap = document.getElementById(msgId);
  if (!wrap) return;
  var bubble = wrap.querySelector('.msg-bubble');
  if (!bubble) { wrap.remove(); return; }
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
