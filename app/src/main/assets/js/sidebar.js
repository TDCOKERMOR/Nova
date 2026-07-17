// ── Sidebar ──────────────────────────────────
// Depends on: state.js

function toggleSidebar() {
  var sb = document.getElementById('sidebar');
  var ov = document.getElementById('sidebarOverlay');
  sb.classList.toggle('open');
  ov.classList.toggle('show');
}

function closeSidebar() {
  document.getElementById('sidebar').classList.remove('open');
  document.getElementById('sidebarOverlay').classList.remove('show');
}

function refreshSidebar() {
  Nova.state._sidebarDirty = true;
  if (Nova.state._sidebarTimer) return;
  Nova.state._sidebarTimer = setTimeout(function() {
    Nova.state._sidebarTimer = 0;
    if (!Nova.state._sidebarDirty) return;
    Nova.state._sidebarDirty = false;
    doRefreshSidebar();
  }, 100);
}

function doRefreshSidebar() {
  var list = document.getElementById('convList');
  var convs = JSON.parse(native.getConversations());
  var curId = native.getCurrentId();
  Nova.state.currentConvId = curId;

  var existing = list.querySelectorAll('.conv-item');
  if (existing.length === convs.length) {
    convs.forEach(function(c, i) {
      var el = existing[i];
      var wasActive = el.classList.contains('active');
      var wasPinned = el.classList.contains('pinned');
      var isActive = c.id === curId;
      var isPinned = c.pinned;
      if (wasActive !== isActive) el.classList.toggle('active', isActive);
      if (wasPinned !== isPinned) el.classList.toggle('pinned', isPinned);
      var pinMark = el.querySelector('.pin-mark');
      if (pinMark) pinMark.textContent = isPinned ? '\uD83D\uDCCC' : '';
      var titleEl = el.querySelector('.title');
      if (titleEl && titleEl.textContent !== c.title) titleEl.textContent = c.title;
      el.onclick = function() { selectConv(c.id); };
      el.onmousedown = function(e) { onConvMouseDown(e, c.id); };
      el.ontouchstart = function(e) { onConvTouchStart(e, c.id); };
    });
  } else {
    list.innerHTML = convs.map(function(c) { return buildConvItem(c, curId); }).join('');
  }
}

function buildConvItem(c, curId) {
  return '<div class="conv-item' + (c.id === curId ? ' active' : '') + (c.pinned ? ' pinned' : '') + '"' +
    ' oncontextmenu="return false"' +
    ' onmousedown="onConvMouseDown(event,\'' + c.id + '\')"' +
    ' ontouchstart="onConvTouchStart(event,\'' + c.id + '\')"' +
    ' onclick="selectConv(\'' + c.id + '\')">' +
    '<span class="pin-mark">' + (c.pinned ? '\uD83D\uDCCC' : '') + '</span>' +
    '<span class="title">' + escHtml(c.title) + '</span>' +
    '</div>';
}

function onSearch(q) {
  var list = document.getElementById('convList');
  if (!q.trim()) { doRefreshSidebar(); return; }
  var convs = JSON.parse(native.searchConversations(q));
  if (convs.length === 0) {
    list.innerHTML = '<div style="padding:20px;text-align:center;color:var(--sub);font-size:13px">无匹配结果</div>';
  } else {
    list.innerHTML = convs.map(function(c) { return buildConvItem(c, Nova.state.currentConvId); }).join('');
  }
}

function selectConv(id) {
  closeSidebar();
  if (id === Nova.state.currentConvId) return;
  native.cancelCurrentStream();
  native.switchConversation(id);
  Nova.state.currentConvId = id;
  loadMessages();
  Nova.state.generatedTitle = true;
  refreshSidebar();
}

function deleteConv(id) {
  native.cancelCurrentStream();
  native.deleteConversation(id);
  if (Nova.state.currentConvId === id) {
    native.newConversation();
    Nova.state.currentConvId = native.getCurrentId();
    loadMessages();
    Nova.state.generatedTitle = false;
  }
  refreshSidebar();
}

function newChat() {
  closeSidebar();
  native.cancelCurrentStream();
  native.newConversation();
  Nova.state.currentConvId = native.getCurrentId();
  loadMessages();
  Nova.state.generatedTitle = false;
  refreshSidebar();
}

// ── Long-press context menu ──────────────────
function onConvTouchStart(e, id) {
  Nova.state._longPressTimer = setTimeout(function() {
    showCtxMenu(e.touches[0].clientX, e.touches[0].clientY, id);
  }, 500);
}

function onConvMouseDown(e, id) {
  if (e.button !== 0) { showCtxMenu(e.clientX, e.clientY, id); return; }
  Nova.state._longPressTimer = setTimeout(function() {
    showCtxMenu(e.clientX, e.clientY, id);
  }, 600);
}

function showCtxMenu(x, y, id) {
  Nova.state._ctxConvId = id;
  var menu = document.getElementById('ctxMenu');
  var pinItem = document.getElementById('ctxPin');
  var isPinned = native.isPinned(id);
  pinItem.querySelector('span').textContent = isPinned ? '取消置顶' : '置顶';
  menu.style.display = 'block';
  menu.style.left = Math.min(x, window.innerWidth - 160) + 'px';
  menu.style.top = Math.min(y, window.innerHeight - 160) + 'px';
}

function hideCtxMenu() {
  document.getElementById('ctxMenu').style.display = 'none';
  Nova.state._ctxConvId = '';
}

function ctxPin() {
  native.pinConversation(Nova.state._ctxConvId);
  refreshSidebar();
  hideCtxMenu();
}

function ctxRename() {
  var newTitle = prompt('输入新标题：');
  if (newTitle !== null && newTitle.trim()) {
    native.renameConversation(Nova.state._ctxConvId, newTitle.trim());
    refreshSidebar();
  }
  hideCtxMenu();
}

function ctxDelete() {
  if (confirm('确定删除这个对话？')) {
    native.cancelCurrentStream();
    native.deleteConversation(Nova.state._ctxConvId);
    if (Nova.state.currentConvId === Nova.state._ctxConvId) {
      native.newConversation();
      Nova.state.currentConvId = native.getCurrentId();
      loadMessages();
      Nova.state.generatedTitle = false;
    }
    refreshSidebar();
  }
  hideCtxMenu();
}
