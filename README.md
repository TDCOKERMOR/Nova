# Nova - AI Chat

一款兼容 OpenAI 接口的 Android AI 聊天应用，支持自定义 API 配置、图片生成、思维链显示、提示词优化等功能。

## 功能特性

- **多 API 配置**：支持独立配置对话 API、图片识别 API、图片生成 API
- **流式输出**：AI 回复实时逐字显示
- **思维链显示**：支持 `reasoning_content`（DeepSeek-R1 等思考模型），折叠式展示
- **图片生成**：通过 `/生图` 命令生成图片，支持多种图片 API
- **提示词优化**：内置提示词优化对话框，支持风格和尺寸自定义
- **多对话管理**：侧边栏管理多个对话，支持置顶、重命名、删除、搜索、导出
- **消息操作**：消息复制按钮，一键复制对话内容
- **自动标题**：根据首条消息自动生成对话标题
- **Markdown 渲染**：基于 marked.js + highlight.js，支持代码高亮、表格、引用（离线可用）
- **暗色主题**：跟随系统自动切换暗色/亮色模式
- **WebView 架构**：UI 基于 WebView + HTML/CSS/JS，支持热更新
- **安全存储**：API Key 使用 Android EncryptedSharedPreferences 加密存储，无明文回退

## 技术栈

- **语言**：Kotlin + JavaScript
- **架构**：WebView + JsBridge
- **网络**：OkHttp
- **存储**：EncryptedSharedPreferences（强制加密，无回退）
- **UI**：HTML/CSS/JS（DeepSeek Chat 风格）
- **加密**：AndroidX Security Crypto
- **序列化**：Gson（带 ProGuard 保留规则，已修复 v5.2 的嵌套类引用错误）

## 项目结构

```
app/
├── src/main/
│   ├── java/com/tdc/aichat/
│   │   ├── MainActivity.kt            # 主活动，WebView 容器
│   │   ├── JsBridge.kt                # JS 桥接门面（delegate 到 handler）
│   │   ├── ChatBridgeHandler.kt       # 聊天 & 对话管理 handler
│   │   ├── ImageBridgeHandler.kt      # 图片生成/分析/下载 handler
│   │   ├── ApiClient.kt               # API 客户端（流式+重试/非流式/图片/多模态）
│   │   ├── ConfigManager.kt           # 加密配置管理（强制加密，含温度/top_p/max_tokens）
│   │   ├── ConversationManager.kt     # 对话管理（标题搜索+全文搜索/排序/持久化）
│   │   ├── Conversation.kt            # 对话数据模型
│   │   ├── Message.kt                 # 消息 + API 数据模型（ChatRequest/ChatStreamChunk/ImageGen 等）
│   │   ├── SettingsActivity.kt        # 设置页（API Key 脱敏显示）
│   │   └── PromptOptimizeDialog.kt    # 提示词优化弹窗
│   ├── assets/
│   │   ├── chat.html                  # 聊天界面 HTML（含欢迎页，本地资源）
│   │   ├── chat.css                   # 聊天界面样式（暗色主题 + 思维链）
│   │   ├── marked.min.js              # Markdown 渲染（本地）
│   │   ├── highlight.min.js           # 代码高亮（本地）
│   │   ├── github.min.css             # 亮色代码主题（本地）
│   │   ├── github-dark.min.css        # 暗色代码主题（本地）
│   │   └── js/
│   │       ├── state.js               # 全局状态 & 工具函数（Nova.state）
│   │       ├── sidebar.js             # 侧边栏 + 对话管理 + 上下文菜单 + 导出
│   │       ├── messages.js            # 消息渲染 + Markdown + 图片管线 + 重新生成 + 复制
│   │       ├── stream.js              # 流式输出 + 停止按钮 + 断线重试 + 思维链渲染
│   │       ├── input.js               # 输入 + 发送 + 附件 + /生图 命令
│   │       ├── dialog.js              # 提示词优化弹窗 + 路由
│   │       └── app.js                 # 入口 & 初始化 & 事件绑定
│   └── res/
│       └── layout/
│           └── activity_settings.xml   # 设置页布局（对话/Vision/Image/System Prompt/模型参数）
├── build.gradle
├── proguard-rules.pro                 # ProGuard 规则（Gson/JsBridge 保留）
└── .github/workflows/ci.yml           # GitHub Actions CI（lint + build + artifact）
```

## 配置说明

应用支持三种独立的 API 配置：

1. **对话 API**：用于聊天对话（必填）
2. **图片识别 API**：用于图片分析（可选，fallback 到对话 API）
3. **图片生成 API**：用于图片生成（可选，fallback 到对话 API）

## 使用说明

1. 在设置中配置 API 地址、Key 和模型名
2. 返回聊天界面开始对话
3. 输入 `/生图 描述` 可生成图片
4. 点击图片右上角按钮可保存图片到下载目录
5. 悬停消息可看到复制按钮，一键复制内容
6. 使用 DeepSeek-R1 等思考模型时，会显示折叠的思维链

## 构建

```bash
# 设置环境变量
export ANDROID_HOME=/path/to/android-sdk
export PATH=$PATH:/path/to/gradle/bin

# 构建 Debug 包
gradle assembleDebug

# 构建 Release 包
gradle assembleRelease
```

输出路径：
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

## 错误信息优化

应用提供友好的中文错误提示，例如：
- `401` → "API Key 无效，请检查设置"
- `404` → "API 地址不存在，请检查 URL 配置"
- `429` → "请求过于频繁，请稍后重试"

## License

MIT License

## 作者

TDCOKERMOR

## 版本历史

- **v5.6** (2026-07-18)：性能优化（JS 流式渲染 textContent 替代 innerHTML 减少重排、图片选取智能跳过无损转换）、存储优化（ConversationManager 添加去抖动保存减少磁盘 I/O、单对话消息 500 条上限防止溢出）、架构精简（ChatBridgeHandler 提取 runStream 公共管道消除 sendMessage/editMessage 重复代码）、WebView 安全加固（SSL 错误拦截、混合内容阻断、文件访问锁定）、编译配置修正（移除无效 buildToolsVersion 37.0.0、compileSdk/targetSdk 升至 35、添加 lint 配置）
- **v5.5** (2026-07-17)：安全增强（对话数据迁移至 EncryptedSharedPreferences 加密存储，旧数据自动迁移）、SettingsActivity API Key 脱敏完善（失焦自动重新脱敏）、ApiClient URL 构建健壮性修复（尾部斜杠容错）、非流式请求补全模型参数（temperature/topP/maxTokens）、ProGuard 规则修正（SendMsgData/JsMessage 跟随 v5.4 架构变更）、JS 端清理遗留死代码 + 流结束 Markdown 渲染重构（避免思维链文本混入消息内容）、send-btn 无障碍光标修正
- **v5.4** (2026-07-17)：架构重构（JsBridge 拆分为 ChatBridgeHandler + ImageBridgeHandler）、ApiClient 流处理优化（BatchFlusher 提取+指数退避重试）、全文对话搜索（搜索消息内容）、DOM 增量更新（消息列表性能优化）、CSS 增强（表格样式/滚动条美化/暗色模式完善）、依赖版本升级
- **v5.3** (2026-07-17)：修复 ProGuard 规则致命 Bug（release 构建数据模型被混淆导致崩溃）、参数输入校验（temperature/topP/maxTokens）、JsBridge Job 竞态修复、ConversationManager 健壮性增强、前端资源加载优化
- **v5.2** (2026-07-17)：思维链显示（DeepSeek-R1 reasoning_content）、消息复制按钮、本地化 CDN 资源、ProGuard 规则完善、ConfigManager 移除明文回退、错误信息中文化优化
- **v5.1** (2026-07-17)：修复双重 UI 线程 Bug、对话 JSON 导出、README 更新
- **v5.0** (2026-07-16)：流式输出、自动创建新对话、图标更新、Bug 修复
- **v4.0**：侧边栏管理、多对话支持、自动标题
- **v3.x**：提示词优化、图片生成集成
- **v2.x**：图片生成、图生图功能
- **v1.0**：基础聊天功能
