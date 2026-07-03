# Hermes Studio App

一个轻量级的 Android WebView 包装器，用于访问 [Hermes Studio](https://github.com/nousresearch/hermes-agent) Web UI。

## 功能特性

### 核心功能
- 🌐 **WebView 包装** - 原生 Android 应用体验
- 📱 **多服务器支持** - 可配置多个服务器地址，自动切换
- 🔄 **智能连接** - WiFi/移动网络自动选择可达的服务器

### 侧边栏导航
- 🔄 刷新页面
- ⚙️ 设置
- ℹ️ 关于

### 设置选项
- 📋 剪贴板同步
- 🔔 通知推送
- 🔄 自动检查更新
- 📱 横屏锁定
- 🗑️ 清除缓存/Cookies/数据

### 其他特性
- 📥 文件上传支持
- 🔒 Cookie 持久化
- 📊 崩溃日志记录
- 📡 连接状态指示

## 下载

前往 [Releases](https://github.com/zcks/hermes-studio-app/releases) 下载最新 APK。

## 使用说明

1. 安装 APK 到 Android 设备
2. 打开 App，首次会请求网络权限
3. 进入设置 → 添加你的 Hermes Studio 服务器地址
4. 支持添加多个地址（如局域网地址和公网地址）
5. 开启"自动选择"后，App 会自动检测网络并选择可达的服务器

## 配置

### 添加服务器地址
1. 打开侧边栏（左滑或点击左上角菜单）
2. 点击"设置"
3. 点击"+ 添加地址"，输入你的服务器地址
4. 可添加多个地址，支持测试连接
5. 返回主界面，会自动加载服务器

## 技术栈

- **语言**: Kotlin
- **最低 SDK**: Android 7.0 (API 24)
- **目标 SDK**: Android 14 (API 34)
- **主要依赖**:
  - AndroidX WebView
  - Material Design Components
  - WorkManager (后台任务)

## 贡献

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 许可证

本项目采用 [MIT 许可证](LICENSE) - 详见 LICENSE 文件

## 免责声明

- 本应用仅为 WebView 包装器，不包含 Hermes Studio 的任何代码
- 请确保你有权限访问目标 Hermes Studio 服务器
- 使用本应用所产生的任何后果由用户自行承担
