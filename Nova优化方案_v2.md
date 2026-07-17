# Nova 项目优化方案 v2.0

> 版本：v2.0 | 日期：2026-07-17 | 基于 v1.0 进度复审与迭代

---

## 0. 进度同步

v1.0 方案的 Phase 1 已基本收尾。以下是完成情况：

| # | 任务 | 状态 |
|---|------|------|
| P1.1 | 停止生成按钮 | ✅ 已实现（`stream.js` 中 `showStopButton`/`stopGeneration`） |
| P1.2 | 重新生成功能 | ✅ 已实现（`messages.js` 中 `regenerateMsg`） |
| P1.3 | System Prompt 配置 | ✅ 已实现（`ConfigManager` + `SettingsActivity` + `JsBridge`） |
| P1.4 | JS 模块化拆分 | ✅ 已拆分为 7 个文件 |
| P1.5 | 消息列表虚拟化 CSS | ❌ 未实施 |
| P1.6 | 欢迎页设计 | ✅ 已实现（`chat.html` 中 `#welcomePage`） |
| P1.7 | API Key 脱敏显示 | ✅ 已实现（`SettingsActivity` 中 `setMaskedKey`） |

**Phase 1 完成度：6/7**

---

## 1. 本次复审发现的问题

逐文件审查过程中发现的「应该尽快修」的问题，按严重程度排列：

### 🔴 高优先级 Bug

#### 1.1 `onImagePicked` 存在 JS 注入风险

**位置**：`JsBridge.kt` 的 `onImagePicked` 方法

```kotlin
// 当前代码（有风险）
activity.webView.evaluateJavascript(
    "onImagePicked('$b64','image/jpeg',${bytes.size})", null
)
```

**问题**：`$b64` 是 Base64 编码，可能包含 `'`（单引号），直接在 JS 字符串中拼接会破坏语法。类比：把没消毒的食材直接扔进锅里。

**修复**：对 `b64` 使用已有的 `escapeJs()` 方法：

```kotlin
activity.webView.evaluateJavascript(
    "onImagePicked('${escapeJs(b64)}','image/jpeg',${bytes.size})", null
)
```

#### 1.2 `InputStream` 未导入

**位置**：`JsBridge.kt` 中 `onImagePicked` 方法

```kotlin
val input: InputStream = activity.contentResolver.openInputStream(uri)
```

`InputStream` 没有显式 import，当前能编译大概率是因为 Kotlin 的自动推断或跨文件可见性。应该显式添加 `import java.io.InputStream`。

#### 1.3 `onChatStreamCancel` 状态恢复不完整

**位置**：`stream.js`

当前 `onChatStreamCancel` 只移除 DOM 元素，但若此前 `setSendEnabled(false)` 已被调用，send 按钮仍处于 disabled 状态。

```javascript
function onChatStreamCancel(msgId) {
    Nova.state._streamMsgId = '';
    var wrap = document.getElementById(msgId);
    if (wrap) wrap.remove();
    // 缺少：setSendEnabled(true);
    // 缺少：hideStopButton();
}
```

### 🟡 中优先级代码质量

#### 1.4 `buildMessageHTML` 与 `buildMessageNode` 重复

两个函数各有 ~30 行，结构几乎相同。一个用 `innerHTML`（全量渲染），一个用 `appendChild`（增量）。可以抽出一个公共的「构建 HTML 字符串」函数，两个渲染路径共用。

#### 1.5 `PromptOptimizeDialog.kt` 的 `!!` 仍存在

```kotlin
private val binding get() = _binding!!
```

v1.0 方案已标注，尚未修复。建议改为：

```kotlin
private val binding get() = requireNotNull(_binding) { "Binding not initialized" }
```

#### 1.6 `regenerateMsg` 未取消运行中的流

如果用户在流式输出期间点重新生成，`regenerateMsg()` 直接发送新请求而不先调用 `native.cancelCurrentStream()`。

#### 1.7 `ChatRequest` 缺少温度等参数字段

当前 `ChatRequest` 只有 `model`、`messages`、`stream`。`temperature`、`top_p`、`max_tokens` 在 v1.0 方案中已设计好，但未落地到代码中。

### 🟢 低优先级

#### 1.8 没有 GitHub Actions CI

仓库根目录不存在 `.github/workflows/`。虽然 `build.gradle` 配置了 ProGuard 优化，但没有任何自动化验证。

#### 1.9 README 项目结构图过期

README 中的结构图仍显示 `chat.js` 为单文件，实际已拆分为 7 个 JS 模块。

---

## 2. 更新后的优先级路线图

### Phase 1.1 — Bug 修复（本周，预估 3h）

| # | 任务 | 工作量 |
|---|------|--------|
| B1 | 修复 `onImagePicked` 的 JS 注入风险 | 10min |
| B2 | 添加 `InputStream` import | 2min |
| B3 | 修复 `onChatStreamCancel` 缺少状态恢复 | 5min |
| B4 | 修复 `regenerateMsg` 缺少流取消 | 5min |
| B5 | 消除 `PromptOptimizeDialog` 的 `!!` | 5min |
| B6 | 合并 `buildMessageHTML`/`buildMessageNode` 的重复逻辑 | 1h |
| B7 | 添加 `ChatRequest` 的 temperature/top_p/max_tokens 字段 | 30min |
| B8 | 补充 GitHub Actions CI（lint + build） | 30min |
| B9 | 更新 README 项目结构 | 10min |

### Phase 2 — 功能增强（2-4 周）

这些是 v1.0 Phase 2 中尚未完成的项，按用户可感知价值排序：

| # | 任务 | 工作量 | 说明 |
|---|------|--------|------|
| P2.1 | 模型参数 UI（temperature/top_p/max_tokens） | 3h | 在设置页增加可折叠的"高级参数"区域 |
| P2.2 | 消息编辑功能 | 4h | 长按用户消息可编辑，截断后重发 |
| P2.3 | 流式输出失败重试 | 2h | SSE 中断时保留已接收内容 + 显示重试按钮 |
| P2.4 | 对话导出（JSON） | 3h | 通过 ShareSheet 分享对话 |
| P2.5 | Kotlin 层模块拆分 | 4h | 无需等，已有 9 个文件，职责尚清晰；等 ApiClient 膨胀再做 |
| P2.6 | 消息列表虚拟化 CSS | 1h | `content-visibility: auto` + `contain-intrinsic-size` |
| P2.7 | 单元测试（ConfigManager + ConversationManager） | 4h | 用 JUnit 5 |

### Phase 3 — 体验打磨（4-8 周）

| # | 任务 |
|---|------|
| P3.1 | LaTeX 数学公式渲染（KaTeX 轻量引入） |
| P3.2 | 前端构建压缩（terser + csso，或跳过，当前体积已经很小） |
| P3.3 | 对话导入 |
| P3.4 | 完整 Instrumentation 测试 |
| P3.5 | SSL 证书固定（可选配置） |
| P3.6 | 国际化（英文 + 中文切换） |

### Phase 4 — 愿景

| # | 任务 |
|---|------|
| P4.1 | Ollama 本地模型适配 |
| P4.2 | Mermaid 图表渲染 |
| P4.3 | 全文搜索对话内容 |
| P4.4 | 多设备同步 |

---

## 3. 架构健康检查

逐层评估当前架构的「健康度」：

| 层 | 文件 | 行数 | 健康度 | 评价 |
|----|------|------|--------|------|
| Kotlin-API | `ApiClient.kt` | 302 | 🟡 | 功能密集但职责清晰。流式/非流式/图片/多模态全在一个 object 里，下个迭代可拆 |
| Kotlin-Bridge | `JsBridge.kt` | 296 | 🟡 | 同样偏大。`escapeJs`、`writeImageToDownloads` 等可抽离 |
| Kotlin-Config | `ConfigManager.kt` | 78 | 🟢 | 整洁，职责单一 |
| Kotlin-UI | `SettingsActivity.kt` | 135 | 🟢 | 整洁。`setMaskedKey` 的实现巧妙 |
| Kotlin-UI | `MainActivity.kt` | 67 | 🟢 | 极简，好 |
| Kotlin-Model | `Message.kt` | 121 | 🟢 | 数据类集中管理，清晰 |
| JS-State | `state.js` | 66 | 🟢 | 全局状态收拢到 `Nova.state`，好 |
| JS-Stream | `stream.js` | 81 | 🟢 | 流式 + 停止逻辑内聚 |
| JS-Messages | `messages.js` | 176 | 🟡 | 最大模块。`handleGenImage` 的图生图管线混在此处，可考虑独立为 `image.js` |
| JS-Sidebar | `sidebar.js` | 172 | 🟡 | 也偏大。增量更新和全量替换的两种路径有少量重复 |
| JS-Input | `input.js` | 97 | 🟢 | 整洁 |
| JS-Dialog | `dialog.js` | 91 | 🟢 | 整洁，路由逻辑清晰 |
| CSS | `chat.css` | 219 | 🟢 | 结构清晰，暗色主题通过媒体查询实现，简洁 |

---

## 4. 建议的下一步行动

当前最务实的做法：

1. **先把 Bug 修复（Phase 1.1）全部清掉**，总计约 3h。尤其是 `onImagePicked` 的 JS 注入问题——虽然触发条件苛刻（需要 Base64 恰好包含单引号），但修起来成本极低。
2. **模型参数配置（P2.1）** 是用户最有感知的功能——让聊天更可控。配合已实现的 System Prompt，用户就能完整控制生成行为。
3. **流式重试（P2.3）** 是刚需——当前网络抖动导致整段消息丢失，体验很差。实现思路：onChatStreamError 时保留 `_streamMsgId` 对应的 DOM 元素中已显示的内容，提供"重试"按钮而非直接移除。

v1.0 方案说了「软件是一步步长出来的」，这个定位没变。v2.0 的核心增量是：补上 v1.0 遗漏的稳定性问题，然后按价值密度推进 v1.0 中剩余的 Phase 2 项。

---

> 相较于 v1.0，这次复审更像是「体检报告」而非「规划蓝图」——项目骨架已经长好了，重点从「要建什么」转向「哪些螺丝需要拧紧」。
