# Nova 项目优化方案

> 版本：v1.0 | 日期：2026-07-17 | 作者：TDCOKERMOR × DeepSeek V4 Pro

---

## 目录

1. [现状评估](#1-现状评估)
2. [架构优化](#2-架构优化)
3. [功能增强](#3-功能增强)
4. [性能优化](#4-性能优化)
5. [安全加固](#5-安全加固)
6. [用户体验](#6-用户体验)
7. [工程化与 DevOps](#7-工程化与-devops)
8. [代码质量](#8-代码质量)
9. [实施路线图](#9-实施路线图)

---

## 1. 现状评估

### 1.1 整体评价

Nova 是一个设计清晰、功能完备的 Android AI 聊天应用。WebView + JsBridge 的混合架构选择务实，用最小的 Android 原生代码量实现了灵活的 UI 热更新能力。三个独立 API 配置的 fallback 机制设计合理，流式输出、异步图片轮询、Markdown 渲染等核心功能扎实。

当前处于"功能可用但工程化待提升"的阶段，有明确的优化空间。

### 1.2 优势

| 维度 | 评价 |
|------|------|
| 架构选择 | WebView 混合架构适合快速迭代 UI，不依赖 Android 原生 UI 框架更新 |
| API 设计 | 三路独立配置 + fallback，适配多种部署场景（幻梦 API、Ollama 等） |
| 流式输出 | 50ms/128 字符批处理机制均衡了 UI 刷新频率与响应流畅度 |
| 安全存储 | EncryptedSharedPreferences + 明文 SharedPreferences 降级，兼顾安全与兼容 |
| 图片管线 | 支持直接 URL 返回和异步 job 轮询，覆盖 OpenAI 和幻梦 API |
| 对话管理 | 置顶/搜索/重命名/长按菜单，功能完整 |

### 1.3 短板

| 维度 | 问题 |
|------|------|
| 前端架构 | 单文件 400+ 行 JS，全局状态散落，无模块化 |
| 错误处理 | 无重试机制，网络异常时流式消息丢失 |
| 功能深度 | 无消息编辑/重新生成、无停止按钮、无 System Prompt 配置 |
| 测试覆盖 | 零自动化测试 |
| 性能 | 长对话全量渲染、图片处理未完全异步化 |
| 安全 | 无证书固定、无 API 调用频率限制 |

---

## 2. 架构优化

### 2.1 Kotlin 层的职责拆分（优先级：中）

**现状**：`ApiClient.kt` 和 `JsBridge.kt` 各承担过多职责。

**方案**：按领域拆分为独立模块：

```
com.tdc.aichat/
├── api/
│   ├── ChatApi.kt          # 聊天相关（流式 + 非流式）
│   ├── ImageApi.kt          # 图片生成 + 异步轮询
│   └── VisionApi.kt         # 多模态分析
├── bridge/
│   ├── JsBridge.kt          # 仅负责 JS ↔ Native 桥接
│   ├── JsEscaper.kt         # escapeJs 等工具函数
│   └── ImageHandler.kt      # 图片选择/压缩/下载独立处理
├── config/
│   └── ConfigManager.kt     # 保持
├── conversation/
│   ├── Conversation.kt      # 数据模型
│   └── ConversationManager.kt
├── model/
│   └── Message.kt           # 所有数据类集中管理
├── ui/
│   ├── MainActivity.kt
│   ├── SettingsActivity.kt
│   └── PromptOptimizeDialog.kt
```

**收益**：各文件职责单一，单文件行数控制在 200 行以内，便于维护和测试。

### 2.2 前端 JS 模块化（优先级：高）

**现状**：`chat.js` 约 410 行，混合了状态管理、UI 渲染、网络回调、侧边栏逻辑。

**方案**：

```
assets/
├── chat.html
├── css/
│   └── chat.css
├── js/
│   ├── app.js            # 入口，初始化
│   ├── state.js           # 全局状态管理（messages, convId, config）
│   ├── sidebar.js         # 侧边栏渲染、搜索、上下文菜单
│   ├── messages.js        # 消息渲染、Markdown、图片气泡
│   ├── stream.js          # 流式输出处理
│   ├── input.js           # 输入框、附件、发送逻辑
│   ├── dialog.js          # 提示词优化弹窗
│   └── utils.js           # escHtml、renderContent 等
```

**实现**：使用 IIFE 模式或 ES Module（WebView 支持度取决于 Android 版本，建议 IIFE 保持兼容）。也可以引入一个极轻量的构建步骤（见 7.2）。

### 2.3 引入 Repository 模式（优先级：低）

**方案**：在 Kotlin 层抽取 `ChatRepository` 和 `ImageRepository`，作为 `JsBridge` 和 `ApiClient` 之间的中间层，负责：

- 请求去重（短时间内相同请求合并）
- 本地缓存策略（最近对话缓存在内存）
- 统一的错误转换（网络错误 → 用户可读消息）

---

## 3. 功能增强

### 3.1 消息操作增强（优先级：高）

| 功能 | 说明 | 工作量 |
|------|------|--------|
| 停止生成 | 发送中显示停止按钮，调用 `cancelCurrentStream()` | 小 |
| 重新生成 | 每条 AI 回复旁显示重新生成按钮，用同一段 messages 重发请求 | 中 |
| 编辑消息 | 长按用户消息可编辑，编辑后重发该位置及之后的消息 | 中 |
| 复制消息 | 长按消息气泡弹出复制按钮 | 小 |
| 消息反馈 | 👍👎 按钮（可选，需要 API 支持） | 中 |

**实现要点**：

- 停止按钮：在流式输出期间，发送按钮变为停止按钮（红色方块图标），点击调用 `native.cancelCurrentStream()`
- 重新生成：在 `messages` 数组中移除最后一条 assistant 消息后重新调用 `sendMessage`
- 编辑消息：`chat.js` 中增加 `editMode` 状态，编辑后将 `messages` 截断至编辑位置并重发

### 3.2 System Prompt 配置（优先级：高）

**方案**：在设置页增加"系统提示词"配置项，存储在 `EncryptedSharedPreferences`（或普通 `SharedPreferences`，因为 System Prompt 通常不敏感）。发送消息时自动插入为 `messages[0]`。

**数据模型**：`AppConfig` 增加 `systemPrompt: String` 字段。

**UI**：设置页底部增加多行文本输入框，带字数统计。

### 3.3 模型参数配置（优先级：中）

**方案**：支持配置 `temperature`、`top_p`、`max_tokens` 三个常用参数。

**数据模型**：`ChatRequest` 增加可选字段：

```kotlin
data class ChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val stream: Boolean = false,
    val temperature: Float? = null,
    val top_p: Float? = null,
    val max_tokens: Int? = null
)
```

**UI**：设置页增加"高级参数"可折叠区域，用 Slider 控件选择 temperature/top_p，输入框设置 max_tokens。

### 3.4 对话导入导出（优先级：中）

**方案**：

- **导出**：将当前对话序列化为 JSON（兼容 OpenAI Chat Completions 格式），通过 `ShareSheet` 分享或保存到文件
- **导入**：从文件或剪贴板读取 JSON，解析后创建新对话

**格式**：直接使用 OpenAI 标准 messages 数组格式，方便迁移和外部编辑。

### 3.5 本地模型支持（优先级：低）

**方案**：增加对 Ollama API 的支持。Ollama 的 `/api/chat` 接口与 OpenAI Chat Completions 结构不同，需要适配：

- URL 格式：`http://host:11434/api/chat`
- 请求体格式差异
- 流式响应格式差异（Ollama 返回的 SSE 结构不同）

### 3.6 Markdown 增强（优先级：低）

- 支持 LaTeX 数学公式（引入 KaTeX 或 MathJax 轻量版）
- 支持 Mermaid 图表渲染
- 表格横向滚动（长表格在小屏幕上溢出）

---

## 4. 性能优化

### 4.1 消息列表虚拟化（优先级：中）

**现状**：所有消息全量渲染到 DOM，长对话（100+ 轮）时滚动性能下降。

**方案**：实现简化版虚拟列表。对于 50 条以内消息保持全量渲染，超过时仅渲染可视区域 ± 5 条。或者更务实的方式：

- 消息超过 100 条时，给 `msgList` 设置 `content-visibility: auto` 和 `contain: strict`
- 对离开视口的消息气泡使用 `content-visibility: hidden` 降低渲染成本

**CSS 优化**：

```css
.msg-row {
  content-visibility: auto;
  contain-intrinsic-size: auto 60px;
}
```

### 4.2 图片处理管线优化（优先级：低）

**现状**：选择图片后在 `onImagePicked` 中同步压缩，虽然是协程但仍占用内存。

**方案**：

- 压缩参数可配置（质量、最大尺寸）
- 超大图片先显示缩略图，上传时用原图
- 图片缓存到磁盘（使用 Glide 或 Coil 管理 WebView 外部图片）

### 4.3 对话数据懒加载（优先级：低）

**现状**：`ConversationManager.load()` 一次性加载所有对话到内存。

**方案**：保持现状，对话数量通常在百条以内，JSON 序列化的 `MutableList<Conversation>` 足够。如果未来对话数量增长，改用 Room 数据库。

### 4.4 WebView 预加载（优先级：低）

**方案**：在 `Application.onCreate()` 中预热 WebView 引擎，减少首次打开白屏时间。

```kotlin
// Application 中
WebView(this).destroy() // 触发引擎初始化
```

---

## 5. 安全加固

### 5.1 SSL 证书固定（Certificate Pinning）（优先级：中）

**方案**：对于自部署的 API 服务器，可选的证书固定。使用 OkHttp 的 `CertificatePinner`：

```kotlin
val client = OkHttpClient.Builder()
    .certificatePinner(
        CertificatePinner.Builder()
            .add("your-api.example.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAA=")
            .build()
    )
    .build()
```

**建议**：作为可选功能，在设置中提供"证书指纹"配置项，留空则不启用固定。

### 5.2 API 密钥可见性（优先级：中）

**现状**：设置页中 API Key 字段使用 `inputType="textPassword"`，但可以复制。

**方案**：

- 增加"显示/隐藏"切换按钮（已通过 `app:endIconMode="password_toggle"` 实现 ✓）
- 已保存的 Key 在加载时仅显示前 4 位和后 4 位，中间用 `*` 替代（如 `sk-a***b1c2`）

### 5.3 请求频率限制（优先级：低）

**方案**：在 `ApiClient` 中增加简单的频率限制，避免意外快速连续发送：

```kotlin
private var lastSendTime = 0L
private val MIN_SEND_INTERVAL = 500L // ms

fun canSend(): Boolean = System.currentTimeMillis() - lastSendTime >= MIN_SEND_INTERVAL
```

### 5.4 WebView 安全配置审查（优先级：低）

**确认清单**：

- ✅ `allowFileAccess = false`
- ✅ `networkSecurityConfig` 限制明文流量仅 localhost
- ⚠️ `domStorageEnabled = true` — 可接受（marked.js 不依赖 DOM Storage）
- 建议增加：`setAllowContentAccess(false)` 防止 WebView 访问 ContentProvider

---

## 6. 用户体验

### 6.1 动画与过渡（优先级：中）

| 场景 | 方案 |
|------|------|
| 消息出现 | CSS `@keyframes slideUp`，从底部平移 + 淡入，duration 200ms |
| 侧边栏 | 当前 transform 动画良好，可增加 backdrop 模糊效果 |
| 发送按钮 | 有内容时放大 + 变色，当前已实现 ✅ |
| 流式光标 | 闪烁动画，当前已实现 ✅ |
| 页面切换 | 对话切换时短暂 fade，避免内容闪烁 |

### 6.2 手势支持（优先级：低）

- 对话项左滑删除
- 双击消息气泡复制
- 下拉关闭图片预览（如有全屏图片查看）

### 6.3 空状态设计（优先级：低）

**现状**：新对话时消息列表为空，仅显示顶部栏。

**方案**：增加欢迎页面，根据配置状态显示不同内容：

- 未配置：引导去设置页
- 已配置：显示 Nova logo + "开始新对话" + 快捷示例（如"写一首诗""解释量子力学"）

### 6.4 无障碍（优先级：低）

- 为按钮增加 `contentDescription`
- 确保 TalkBack 可正确朗读消息内容
- 颜色对比度满足 WCAG AA 标准

### 6.5 平板适配（优先级：低）

- 横屏模式下侧边栏常驻（当前已通过媒体查询实现 ✅）
- 消息气泡最大宽度在平板上调小（当前 80% 在平板上可能过宽）
- 支持分屏模式

---

## 7. 工程化与 DevOps

### 7.1 自动化测试（优先级：高）

**方案**：

- **单元测试**（JUnit 5）：`ConfigManager`、`ConversationManager`、`escapeJs()`、流式解析逻辑
- **Instrumentation 测试**：`SettingsActivity` 保存/加载、`MainActivity` WebView 加载
- **JS 测试**（可选）：用 Jest 测试 `escHtml`、`renderContent` 等纯函数

**目标覆盖率**：Kotlin 层 ≥ 60%，JS 层 ≥ 30%。

### 7.2 前端构建流程（优先级：中）

**现状**：JS/CSS 直接手写，无构建步骤。

**方案**（可选，不改变架构）：引入轻量构建工具链，仅在开发阶段用，产物仍为纯 JS/CSS。

```
chat.js ──[concat]──→ assets/chat.bundle.js
chat.css ─[minify]──→ assets/chat.min.css
```

使用 Node.js 脚本 + `terser` + `csso`，输出到 `app/src/main/assets/`。

### 7.3 CI/CD 增强（优先级：中）

**方案**：在现有 GitHub Actions 基础上增加：

```yaml
# .github/workflows/ci.yml
jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: ./gradlew ktlintCheck
      - run: ./gradlew lint

  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: ./gradlew test

  build:
    needs: [lint, test]
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/
```

### 7.4 版本管理自动化（优先级：低）

**方案**：用 Git tag 驱动版本号，CI 中自动更新 `versionCode` 和 `versionName`。

---

## 8. 代码质量

### 8.1 Kotlin 代码

| 问题 | 位置 | 修复 |
|------|------|------|
| `!!` 强制解包 | `PromptOptimizeDialog.kt:17` `_binding!!` | 用 `requireNotNull(_binding)` 或 `?: return` |
| 大文件 | `ApiClient.kt` (300+ 行) | 按 2.1 拆分 |
| 大文件 | `JsBridge.kt` (340+ 行) | 按 2.1 拆分 |
| 硬编码字符串 | 多处 | 提取到常量或 `strings.xml` |
| 魔术数字 | `JsBridge.kt` 中 `maxDim=2048`、`compress=80` | 提取为命名常量 |

### 8.2 JavaScript 代码

| 问题 | 位置 | 修复 |
|------|------|------|
| 全局变量 | `chat.js` 顶部 9 个 `let` | 收拢到 `AppState` 对象 |
| 重复代码 | `buildMessageHTML` 与 `buildMessageNode` | 合并为单一渲染函数 |
| `innerHTML` 拼接 | 多处 | 对用户输入始终用 `escHtml`（已做 ✅） |
| 魔法字符串 | 中文 UI 文本散落各处 | 提取到 `UI_TEXT` 常量对象 |

### 8.3 ProGuard 规则

**现状**：规则完整，覆盖 Gson 数据类、JsBridge 接口、OkHttp。✅

**建议**：增加 `-keep class com.tdc.aichat.** { *; }` 作为 Release 保险，或确认所有反射使用的类已列出。

---

## 9. 实施路线图

### Phase 1 — 快速获胜（1-2 周，低风险高收益）

| # | 任务 | 工作量 |
|---|------|--------|
| P1.1 | 增加"停止生成"按钮 | 2h |
| P1.2 | 增加"重新生成"功能 | 3h |
| P1.3 | System Prompt 配置 | 3h |
| P1.4 | JS 代码模块化（拆分为 6-7 个文件） | 4h |
| P1.5 | 消息列表虚拟化 CSS 优化 | 1h |
| P1.6 | 欢迎页 / 空状态设计 | 2h |
| P1.7 | API Key 脱敏显示 | 1h |

### Phase 2 — 功能深化（2-4 周）

| # | 任务 | 工作量 |
|---|------|--------|
| P2.1 | Kotlin 层模块拆分（API/桥/模型分离） | 6h |
| P2.2 | 模型参数配置（temperature/top_p/max_tokens） | 3h |
| P2.3 | 消息编辑功能 | 4h |
| P2.4 | 对话导出（JSON 格式） | 3h |
| P2.5 | 单元测试覆盖 | 5h |
| P2.6 | CI Pipeline 增强 | 3h |
| P2.7 | 消息动画（slideUp 入场） | 2h |

### Phase 3 — 体验提升（4-8 周）

| # | 任务 | 工作量 |
|---|------|--------|
| P3.1 | 对话导入 + 格式兼容 | 3h |
| P3.2 | LaTeX 数学公式渲染 | 4h |
| P3.3 | 平板横屏布局优化 | 3h |
| P3.4 | 前端构建流程（压缩/合并） | 4h |
| P3.5 | SSL 证书固定 | 2h |
| P3.6 | 完整 Instrumentation 测试 | 6h |
| P3.7 | 代码清理：消除 `!!`、提取常量 | 2h |

### Phase 4 — 愿景功能（8 周+）

| # | 任务 | 工作量 |
|---|------|--------|
| P4.1 | Ollama 本地模型适配 | 8h |
| P4.2 | Mermaid 图表渲染 | 3h |
| P4.3 | 全屏图片查看器 | 4h |
| P4.4 | 消息搜索（全文搜索对话内容） | 4h |
| P4.5 | 国际化（i18n，英文支持） | 6h |
| P4.6 | 多设备同步（可选，基于 WebDAV/自定义服务） | 16h+ |

---

## 附录 A：架构决策记录（ADR）

### A.1 为什么保持 WebView 混合架构而非迁移到 Compose？

- WebView UI 可热更新（修改 HTML/CSS/JS 无需重装 APK）
- marked.js + highlight.js 的 Markdown 渲染效果远超 Compose 的 `AnnotatedString`
- 迁移成本高，当前架构运行良好
- 开发迭代速度：修改 CSS 即改即刷 vs Compose Preview

### A.2 为什么用 SharedPreferences 而非 Room 存储对话？

- 对话数据是文档型（一个 JSON 对象），非关系型
- 对话量通常在百条以内，JSON 序列化性能足够
- Room 增加依赖和复杂度，ROI 低

### A.3 为什么不引入前端框架（React/Vue/Svelte）？

- 引入构建步骤增加工程复杂度
- 当前 pure JS 约 410 行，框架开销比业务逻辑还大
- 模块化后 6-7 个文件每文件 50-80 行，可维护性足够

---

## 附录 B：参考实现

| 场景 | 参考项目 | 可借鉴点 |
|------|----------|----------|
| WebView + JS Bridge | [NextChat](https://github.com/ChatGPTNextWeb/ChatGPT-Next-Web) | Markdown 渲染、流式 UI |
| Android 混合架构 | [OpenCat](https://github.com/Panl/OpenCat)（已归档） | 对话管理 UI |
| 提示词工程 | [f/awesome-chatgpt-prompts](https://github.com/f/awesome-chatgpt-prompts) | System Prompt 设计思路 |

---

> 一只一无是处的皮卡丘呀，Nova 从一个基础聊天应用成长为现在的 v5.0，每一步都扎实。这份方案不是"推倒重来"的手术清单，而是沿着你已经铺设的道路，把路肩拓宽、路面压实的工程计划。挑你感兴趣的先做，剩下的放着也没关系，软件是一步步长出来的。
