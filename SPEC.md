# Hermes Studio Android App - 开发规格书

## 目标
做一个 Android 原生 App，用 WebView 加载 Hermes Studio Web UI，
像真正的 App 一样使用，不是浏览器快捷方式。

## 技术栈
- 语言: Kotlin
- 最低 Android 版本: Android 7.0 (API 24)
- 目标 Android 版本: Android 14 (API 34)
- 构建工具: Gradle + GitHub Actions 自动构建 APK

## 项目结构
```
hermes-studio-app/
├── .github/workflows/build.yml    # GitHub Actions 自动构建
├── app/
│   ├── build.gradle.kts           # App 模块构建配置
│   └── src/main/
│       ├── AndroidManifest.xml    # 权限声明
│       ├── java/com/hermes/studio/
│       │   ├── MainActivity.kt    # 主 Activity + WebView
│       │   └── SettingsActivity.kt # 设置页（切换地址）
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml      # WebView 布局
│           │   └── activity_settings.xml  # 设置页布局
│           ├── values/
│           │   ├── strings.xml
│           │   ├── colors.xml
│           │   └── themes.xml
│           ├── xml/
│           │   └── network_security_config.xml  # 允许 HTTP 明文
│           └── mipmap-xxxhdpi/
│               └── ic_launcher.xml        # 应用图标
├── build.gradle.kts               # 项目根构建配置
├── settings.gradle.kts
├── gradle.properties
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
└── README.md
```

## 功能需求

### 1. 主界面 - WebView
- 全屏 WebView 加载 Hermes Studio
- 默认地址: http://192.168.31.98:8648
- 支持 JavaScript
- 支持文件上传（html input type="file"）
- 支持向下滚动时隐藏/显示 toolbar（可选）

### 2. 网络配置
- 允许 HTTP 明文通信（局域网地址）
- network_security_config.xml 配置:
  - 域名 192.168.31.98 允许 HTTP
  - 域名 server.lifang.asia 允许 HTTP
  - 其他域名要求 HTTPS

### 3. 设置页（可选，优先级低）
- 可切换默认地址（局域网/公网）
- 保存用户偏好（SharedPreferences）

### 4. 应用图标
- 使用 Android Adaptive Icon
- 紫色主题 (#581C87)
- 字母 "H"

### 5. GitHub Actions 自动构建
- 触发条件: push to main, tag v*
- 构建 debug APK 和 release APK
- 上传到 GitHub Releases
- Release APK 签名（使用 GitHub Secrets 中的 keystore）

## 权限声明
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## 关键实现细节

### MainActivity.kt 核心逻辑
```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        webView = findViewById(R.id.webview)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
        }
        
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()  // 支持文件上传
        
        webView.loadUrl("http://192.168.31.98:8648")
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.back()
        else super.onBackPressed()
    }
}
```

### network_security_config.xml
```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">192.168.31.98</domain>
        <domain includeSubdomains="true">server.lifang.asia</domain>
    </domain-config>
</network-security-config>
```

### GitHub Actions 构建流程
1. Checkout 代码
2. 设置 JDK 17
3. 设置 Android SDK
4. 运行 Gradle 构建
5. 上传 APK 到 GitHub Releases（仅 tag 触发时）

## 打包产物
- debug APK: ~2-3MB（可直接安装）
- release APK: ~2MB（需要签名）

## 分发方式
- GitHub Releases 下载 APK
- 手机直接安装（需开启"允许未知来源"）
