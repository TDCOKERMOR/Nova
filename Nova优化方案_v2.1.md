# Nova 项目优化方案 v2.1

> 版本：v2.1 | 日期：2026-07-17 | 迭代：代码质量 + 功能增量

---

## 0. 版本同步

| 文档版本 | 日期 | 状态 |
|----------|------|------|
| v1.0 | 2026-07-17 | 总体规划蓝图 |
| v2.0 | 2026-07-17 | 代码复审体检 |
| **v2.1** | **2026-07-17** | **本次执行：Bug修复 + README更新 + 对话导出 + 版本号升级** |

---

## 1. v2.0 进度终审

在开始新优化前，对 v2.0 列出的所有 Bug 做最终确认：

| # | 任务 | v2.0 标记 | 终审确认 |
|---|------|-----------|----------|
| B1 | 修复 `onImagePicked` JS 注入风险 | 🔴高优 | ⚠️ escapeJs 已加但存在双重 runOnUiThread 嵌套 bug（本次修复） |
| B2 | 添加 `InputStream` import | 🔴高优 | ✅ 已导入 |
| B3 | 修复 `onChatStreamCancel` 状态恢复 | 🔴高优 | ✅ 已含 setSendEnabled + hideStopButton |
| B4 | 修复 `regenerateMsg` 缺少流取消 | 🔴高优 | ✅ 已含 native.cancelCurrentStream() |
| B5 | 消除 `PromptOptimizeDialog` 的 `!!` | 🔴高优 | ✅ 已改为 requireNotNull |
| B6 | 合并 `buildMessageHTML`/`buildMessageNode` | 🟡中优 | ✅ 已抽取 buildBubbleHTML |
| B7 | 添加 ChatRequest 参数字段 | 🟡中优 | ✅ 已含 temperature/top_p/max_tokens |
| B8 | GitHub Actions CI | 🟢低优 | ✅ 已有 ci.yml（lint + build） |
| B9 | 更新 README 项目结构 | 🟢低优 | ❌ 仍过期，本次修复 |

**v2.0 完成度：8/9 → 本次补齐最后一项**

---

## 2. 本次优化清单（v2.1）

### 2.1 🔴 Bug 修复：`onImagePicked` 双重 runOnUiThread

**位置**：`JsBridge.kt` line ~205-218

**现状**：
```kotlin
activity.runOnUiThread {
    val escapedB64 = escapeJs(b64)
activity.runOnUiThread {   // ← 嵌套在第一个 runOnUiThread 内，多余的
    activity.webView.evaluateJavascript(...)
}
}
```

**修复**：合并为单层 runOnUiThread，避免语义歧义和潜在竞态。

### 2.2 📝 README 更新

- 项目结构图更新为 7 个 JS 模块
- 版本历史新增 v5.1
- 补充新增功能：System Prompt、模型参数配置、停止/重试、对话导出

### 2.3 📦 版本号升级

- `versionCode`: 5 → 6
- `versionName`: "5.0" → "5.1"

### 2.4 ✨ 对话导出功能（新增）

- **JS 侧**：在侧边栏长按菜单新增"导出对话"选项
- **Kotlin 侧**：通过 ShareSheet 分享 JSON 格式对话
- **格式**：兼容 OpenAI Chat Completions messages 数组

### 2.5 🔁 流式重试功能（确认已实现）

- 已在 `stream.js` 中实现：`onChatStreamError` 保留已接收内容 + 显示重试按钮
- 已在 `state.js` 中存储 `_lastApiMsgs` 用于重试
- 无需额外工作
