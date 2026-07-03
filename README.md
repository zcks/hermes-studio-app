# Hermes Studio - Android Client

Hermes Studio 官方有桌面版和网页版，但没有安卓客户端。这个项目用 WebView 把 Hermes Studio Web UI 包装成一个安卓 App，让你在手机上也能像用原生应用一样使用你的 AI 助手。

> Hermes Studio 是 [Hermes Agent](https://github.com/nousresearch/hermes-agent) 的 Web UI，用于管理和使用你的 AI 助手。

## ✨ 功能亮点

- 🌐 **原生体验** — 比浏览器更流畅，全屏沉浸式使用
- 📱 **多服务器** — 局域网和公网地址并存，自动切换可达的服务器
- 📋 **剪贴板同步** — 手机复制，电脑粘贴（需服务端配合）
- 🔔 **消息推送** — 后台定期检查，不错过任何回复
- 🔄 **自动更新** — 有新版本时自动提示
- 📱 **横屏锁定** — 平板用户可锁定横屏
- 📡 **连接状态** — 实时显示服务器连接状态

## 📥 下载

前往 [Releases](https://github.com/zcks/hermes-studio-app/releases) 下载最新 APK。

- **最低系统版本**：Android 7.0 (API 24)
- **目标版本**：Android 14 (API 34)

## 🚀 快速开始

**前提条件**：你需要一个可访问的 Hermes Studio 服务器（自部署或他人提供）。

1. 安装 APK
2. 打开侧边栏 → 设置 → 添加服务器地址
3. 返回主界面，自动加载

> 💡 可添加多个地址（如局域网 + 公网），App 会自动选择当前网络下可达的服务器。

## ⚙️ 设置说明

| 功能 | 说明 |
|------|------|
| 服务器地址 | 支持添加多个，自动检测可用性 |
| 自动选择 | 开启后自动切换到可达的服务器 |
| 剪贴板同步 | 手机端复制内容同步到服务端 |
| 通知推送 | 后台每15分钟检查一次消息 |
| 自动更新 | 检查 GitHub Releases 获取新版本 |
| 横屏锁定 | 强制横屏显示 |
| 清除数据 | 清除缓存、Cookies 或所有数据 |

## 🛠️ 技术栈

- Kotlin
- AndroidX WebView + Material Design
- WorkManager（后台任务）

## 📄 许可证

[MIT License](LICENSE)

## 免责声明

- 本应用仅为 WebView 包装器，不包含 Hermes Studio 的任何代码
- 请确保你有权限访问目标 Hermes Studio 服务器
- 使用本应用所产生的任何后果由用户自行承担
