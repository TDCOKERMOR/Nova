// ── Optimize Dialog ──────────────────────────
// Depends on: state.js

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
  var prompt = document.getElementById('optPrompt').value.trim();
  if (!prompt) return;
  var style = document.getElementById('optStyle').value.trim();
  var size = document.getElementById('optSize').value.trim();

  document.getElementById('optBtn').style.display = 'none';
  document.getElementById('optLoading').style.display = 'block';
  document.getElementById('optResult').style.display = 'none';

  native.optimizePrompt(prompt, style, size);
}

function onOptimizePromptResult(text) {
  Nova.state._optResult = text;
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
  document.getElementById('msgInput').value = '/生图 ' + Nova.state._optResult;
  autoResizeInput();
  closeOptimize();
}

// ── Native callback router ───────────────────
// Called from native via evaluateJavascript.
// Routes to dialog or send-flow depending on whether dialog is open.
function onOptimizeResult(text) {
  if (document.getElementById('optimizeDialog').classList.contains('show')) {
    onOptimizePromptResult(text);
  } else {
    onOptimizeResultFromSend(text);
  }
}

function onOptimizeError(err) {
  if (document.getElementById('optimizeDialog').classList.contains('show')) {
    onOptimizePromptError(err);
  } else {
    onOptimizeErrorFromSend(err);
  }
}
