# Nova - AI Chat

一款兼容 OpenAI 接口的 Android AI 聊天应用，支持自定义 API 配置、图片生成、提示词优化等功能。

## 功能特性

- **多 API 配置**：支持独立配置对话 API、图片识别 API、图片生成 API
- **流式输出**：AI 回复实时逐字显示
- **图片生成**：通过 `/生图` 命令生成图片，支持多种图片 API
- **提示词优化**：内置提示词优化对话框，支持风格和尺寸自定义
- **多对话管理**：侧边栏管理多个对话，支持置顶、重命名、删除
- **自动标题**：根据首条消息自动生成对话标题
- **Markdown 渲染**：支持代码块、表格等 Markdown 语法（待完善）
- **WebView 架构**：UI 基于 WebView + HTML/CSS/JS，支持热更新

## 技术栈

- **语言**：Kotlin + JavaScript
- **架构**：WebView + JsBridge
- **网络**：OkHttp
- **存储**：SharedPreferences
- **UI**：HTML/CSS/JS（DeepSeek Chat 风格）

## 项目结构

```
app/
├── src/main/
│   ├── java/com/tdc/aichat/
│   │   ├── MainActivity.kt        # 主活动，WebView 容器
│   │   ├── JsBridge.kt            # JS 与原生通信桥
│   │   ├── ApiClient.kt           # API 请求客户端
│   │   ├── ConfigManager.kt       # 配置管理
│   │   ├── ConversationManager.kt # 对话管理
│   │   └── Message.kt             # 数据模型
│   ├── assets/
│   │   ├── chat.html              # 聊天界面 HTML
│   │   ├── chat.css               # 聊天界面样式
│   │   └── chat.js                # 聊天界面逻辑
│   └── res/                       # 原生资源（图标等）
└── build.gradle
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

## 构建

```bash
# 设置环境变量
export ANDROID_HOME=/path/to/android-sdk
export PATH=$PATH:/path/to/gradle/bin

# 构建 Debug 包
gradle assembleDebug
```

输出路径：`app/build/outputs/apk/debug/app-debug.apk`

## 截图

（待添加）

## License

MIT License

## 作者

TDCOKERMOR

## 版本历史

- **v5.0** (2026-07-16)：流式输出、自动创建新对话、图标更新、Bug 修复
- **v4.0**：侧边栏管理、多对话支持、自动标题
- **v3.x**：提示词优化、图片生成集成
- **v2.x**：图片生成、图生图功能
- **v1.0**：基础聊天功能
