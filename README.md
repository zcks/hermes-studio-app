# Hermes Studio App

一个轻量级的 Android WebView 包装器，用于访问 [Hermes Studio](https://github.com/nousresearch/hermes-agent) Web UI。

## 功能特性

### 核心功能
- 🌐 **WebView 包装** - 原生 Android 应用体验
- 🔄 **智能 URL 切换** - WiFi 下使用局域网地址，移动网络下使用公网地址
- 📱 **自定义服务器** - 支持配置自己的 Hermes Studio 地址

### 侧边栏导航
- 🔄 刷新页面
- ⚙️ 设置
- ℹ️ 关于
- 🔍 检查更新

### 设置选项
- 🌙 深色模式
- 📋 剪贴板同步
- 🔔 通知推送
- 🔄 自动检查更新
- 📱 横屏锁定
- 🗑️ 清除缓存/Cookies/数据

### 其他特性
- 📥 文件上传支持
- 🔒 Cookie 持久化
- 📊 崩溃日志记录
- 🔄 下拉刷新
- 📡 网络状态检测

## 下载

### GitHub Releases
前往 [Releases](https://github.com/zcks/hermes-studio-app/releases) 下载最新 APK。

### 自行构建
```bash
# 克隆仓库
git clone https://github.com/zcks/hermes-studio-app.git
cd hermes-studio-app

# 使用 Android Studio 打开项目
# 或命令行构建
./gradlew assembleDebug
```

## 使用说明

1. 安装 APK 到 Android 设备
2. 打开 App，首次会请求网络权限
3. 默认会自动检测网络环境：
   - WiFi 连接：使用 `http://192.168.31.98:8648`
   - 移动网络：使用 `http://server.lifang.asia:8648`
4. 可在设置中自定义服务器地址

## 配置

### 自定义服务器地址
1. 打开侧边栏（左滑或点击左上角菜单）
2. 点击"设置"
3. 在"服务器地址"输入框中填写你的 Hermes Studio 地址
4. 返回主界面，会自动加载新地址

## 技术栈

- **语言**: Kotlin
- **最低 SDK**: Android 7.0 (API 24)
- **目标 SDK**: Android 14 (API 34)
- **主要依赖**:
  - AndroidX WebView
  - Material Design Components
  - WorkManager (后台任务)
  - SwipeRefreshLayout

## 项目结构

```
app/src/main/
├── java/com/hermes/studio/
│   ├── MainActivity.kt          # 主界面
│   ├── SettingsActivity.kt      # 设置页面
│   ├── AboutActivity.kt         # 关于页面
│   ├── UpdateChecker.kt         # 更新检测
│   ├── ClipboardSync.kt         # 剪贴板同步
│   ├── CrashLogger.kt           # 崩溃日志
│   └── MessageCheckWorker.kt    # 通知检查
├── res/
│   ├── layout/                  # 布局文件
│   ├── menu/                    # 菜单文件
│   └── values/                  # 资源文件
└── AndroidManifest.xml          # 应用配置
```

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

## 致谢

- [Hermes Agent](https://github.com/nousresearch/hermes-agent) - AI 助手框架
- [Android WebView](https://developer.android.com/guide/webapps/webview) - 官方文档
- [Material Design](https://material.io/) - UI 设计指南
