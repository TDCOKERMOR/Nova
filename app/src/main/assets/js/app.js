// ── App Init ─────────────────────────────────
// Load this last.

document.addEventListener('DOMContentLoaded', function() {
  initMarked();
  Nova.state.currentConvId = native.getCurrentId();
  refreshSidebar();
  loadMessages();
  checkConfig();
  autoResizeInput();

  // Scroll-bottom button visibility
  document.getElementById('msgList').addEventListener('scroll', onMsgScroll);

  // Input auto-resize
  document.getElementById('msgInput').addEventListener('input', autoResizeInput);
  autoResizeInput();

  // Long-press cleanup
  document.addEventListener('mouseup', function() { clearTimeout(Nova.state._longPressTimer); });
  document.addEventListener('touchend', function() { clearTimeout(Nova.state._longPressTimer); });
  document.addEventListener('touchmove', function() { clearTimeout(Nova.state._longPressTimer); });

  // Context menu cleanup
  document.addEventListener('click', function(e) {
    if (!e.target.closest('.ctx-menu')) hideCtxMenu();
  });
});

function onMsgScroll() {
  var el = document.getElementById('msgList');
  var btn = document.getElementById('btnScrollBottom');
  if (!el || !btn) return;
  var distFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
  btn.classList.toggle('show', distFromBottom > 200);
}
