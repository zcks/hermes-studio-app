# Hermes Studio Android App

用 Android WebView 包装 Hermes Studio Web UI 的原生应用。

## 功能

- 全屏 WebView 加载 Hermes Studio
- 支持 JavaScript、DOM Storage、文件上传
- 允许局域网 HTTP 明文通信
- GitHub Actions 自动构建 APK

## 默认地址

http://192.168.31.98:8648

## 构建

### 自动构建（推荐）

Push 到 `main` 分支或创建 `v*` tag 时，GitHub Actions 自动构建：

```bash
git tag v1.0.0
git push origin v1.0.0
```

APK 将自动上传到 GitHub Releases。

### 本地构建

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease
```

## 安装

1. 从 GitHub Releases 下载 APK
2. 在 Android 设备上安装（需开启「允许未知来源」）
3. 确保设备与 Hermes Studio 服务器在同一网络

## 技术栈

- Kotlin
- AndroidX + Material Design
- WebView
- Gradle 8.5 + AGP 8.2.2
- minSdk 24 (Android 7.0)
- targetSdk 34 (Android 14)

## 项目结构

```
hermes-studio-app/
├── .github/workflows/build.yml
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/hermes/studio/MainActivity.kt
│   └── res/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```
