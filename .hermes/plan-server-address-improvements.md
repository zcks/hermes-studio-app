# Hermes Studio App v0.2 修改方案

## 修改目标
三项功能改进，提升用户体验和安全性。

---

## 一、设置页地址遮掩

### 现状
设置页直接显示完整服务器地址（如 `http://192.168.31.99:8648`），隐私性差。

### 方案
- 地址默认显示为遮掩格式：`192.168.***.***:8648`
- 点击地址文字可切换显示/隐藏完整地址
- 服务器列表项需要记录完整URL，UI上只改变显示文本

### 实现
`SettingsActivity.kt` 的 `addAddressItem()` 方法：
- 新增一个 `fullUrl` 变量存储完整地址
- `urlText` 显示遮掩格式
- 给 `urlText` 添加点击监听器，点击时切换显示

遮掩逻辑：
```kotlin
fun maskUrl(url: String): String {
    // http://192.168.31.99:8648 → http://192.168.***.***:8648
    // https://server.lifang.asia:8648 → https://server.***.***:8648
    try {
        val uri = java.net.URI(url)
        val host = uri.host ?: return url
        val parts = host.split(".")
        if (parts.size >= 2) {
            // 掩盖中间部分
            val masked = parts.first() + ".***" + ".***" + parts.drop(2).joinToString("") { ".$it" }
            return url.replace(host, masked)
        }
    } catch (_: Exception) {}
    return url
}
```

---

## 二、添加地址自动检测

### 现状
输入地址后直接加入列表，虽然后台会自动测试，但对话框已关闭，用户看不到检测过程。

### 方案
像 Moonlight 一样：输入地址后，在对话框内实时显示检测状态，确认可达后再加入列表。

### 实现
修改 `showAddAddressDialog()` 方法：
1. 输入地址后，点击"添加"不直接加入列表
2. 显示一个带状态的对话框（或在原对话框内更新UI）
3. 后台线程测试连接（HEAD请求，超时3秒）
4. 测试结果显示：
   - ⏳ 正在检测...
   - ✅ 连接成功 → 自动加入列表
   - ❌ 连接失败 → 提示用户是否仍要添加

对话框布局：
```
标题：添加服务器地址
输入框：[  http://192.168.31.99:8648  ]
状态：⏳ 正在检测...
[添加] [取消]
```

测试完成后状态更新为 ✅ 或 ❌，"添加"按钮在检测完成前禁用。

---

## 三、网络切换实时检测

### 现状
`networkCallback` 只监听网络的 onAvailable/onLost，当 WiFi 断开但移动网络在线时，
`isOnline` 不会变 false，导致不会重新调用 `getBaseUrl()` 重新选择服务器。

### 方案
注册监听"活跃网络变化"（`onCapabilitiesChanged` + 检测网络类型变化），
WiFi→移动 切换时自动重新检测并加载可达的服务器。

### 实现
修改 `MainActivity.kt`：

1. **改进 NetworkCallback**：
   - 添加 `onCapabilitiesChanged` 回调
   - 检测网络类型变化（WiFi↔移动）
   - 变化时重新调用 `getBaseUrl()` 并加载

2. **网络类型检测**：
   ```kotlin
   private fun isWifiNetwork(network: Network): Boolean {
       val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
       val caps = cm.getNetworkCapabilities(network) ?: return false
       return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
   }
   ```

3. **切换逻辑**：
   - 记录当前使用的网络类型（WiFi/移动）
   - `onCapabilitiesChanged` 时检查网络类型是否变化
   - 变化时：重新测试所有服务器地址，加载第一个可达的
   - 避免重复加载相同URL

4. **防抖**：网络切换后等待2秒再检测，避免频繁切换

---

## 四、在线状态以服务器可达性为准

### 现状
`isOnline` 只表示设备有网络连接（WiFi/移动数据），不关心服务器是否可达。
设备有网但服务器挂了，仍显示"在线"，不符合直觉。

### 方案
在线/离线状态应以服务器地址是否可达为准：
- 服务器可达 → 在线
- 服务器不可达（无论设备有没有网） → 离线/连接失败

### 实现
修改 `MainActivity.kt`：

1. **移除 `isOnline` 字段**，改用 `isServerReachable` 作为唯一状态源

2. **修改 `networkCallback`**：
   - `onAvailable` / `onLost` 不再直接设在线/离线
   - 而是触发重新检测服务器可达性

3. **新增定期检测**：
   - 网络变化时检测
   - WebView 加载成功/失败时更新
   - 可选：每隔30秒后台检测一次服务器可达性

4. **状态显示逻辑**：
   - 🟢 在线 — 服务器可达（页面加载成功）
   - 🟡 正在连接 — 正在检测或加载中
   - 🔴 离线 — 服务器不可达（超时/拒绝连接）
   - 🔴 连接失败 — 明确的错误（404/500等）

5. **网络切换联动**：
   - 网络类型变化 → 重新检测所有服务器 → 更新状态

---

## 版本号
v0.1 → v0.2（versionCode: 200, versionName: "0.2.0"）

## 修改文件
- `app/src/main/java/com/hermes/studio/SettingsActivity.kt`
- `app/src/main/java/com/hermes/studio/MainActivity.kt`
- `app/build.gradle.kts`（版本号）
