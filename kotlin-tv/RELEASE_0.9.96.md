# Kotlin 0.9.96：能装到 Android 6.0 上了

<!-- notes -->
这一版只改了一件事：最低系统从 Android 8.0 降到 Android 6.0。
老电视盒子、Android 6 的投影和平板现在也能装了。功能和 0.9.95 一模一样。
<!-- /notes -->

最低 Android 6.0 / API 23（上一版是 API 26），versionCode 9096。单 APK，安装后联网使用，无需额外服务。沿用发布签名，可覆盖安装 0.9.95 及更早版本并保留本机收藏、观看记录和设置。

## 为什么原来装不上

上一版 `minSdk = 26`。安装包在系统版本低于 8.0 的设备上会被包管理器直接拒绝（`INSTALL_FAILED_OLDER_SDK`），
连"安装"按钮都点不到。Android 6.0 的电视盒子和投影大多还在用，所以这一版把门槛降下来。

## 改了什么

`app/build.gradle.kts` 里一个数字：

```kotlin
minSdk = 23   // 原 26
```

**只改这个数字不够，必须重新编译。** 项目里用了 `java.nio.file`（写日志、落盘记录）和
`java.time`（`PhoneSearchServer.kt`），这些类在 Android 8.0 才进系统。只改 `minSdk` 而不重编译，
在 Android 6 上会抛 `NoClassDefFoundError`，装得上但一跑就崩。

所以同时打开了 AGP 的 core library desugaring：

```kotlin
compileOptions { isCoreLibraryDesugaringEnabled = true }
dependencies { coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5") }
```

它把 `java.nio.file`、`java.time`、`java.util.stream` 在编译期改写成 `j$.*` 兼容实现，
老系统上跑的是随 APK 一起打进去的代码，不依赖系统类库。

## 为什么 32 位设备也能装

整个 APK **不含任何 `.so`**。上一代 `mangju-tv` 依赖字节的 `libsscronet.so`（只有 arm64），
32 位盒子和 Android 6 一律装不上，这也是换掉它的直接原因。

这一版的签名（Argus / Ladon / Gorgon / ttEncrypt 注册）是**纯 Kotlin 实现**，没有 native 依赖，
所以 ABI 无关：arm64、armeabi-v7a、x86 拿到的是同一个 APK。

## 验证

- **编译**：`:app:assembleDebug` BUILD SUCCESSFUL，产物 10.8 MB，0 个 `.so`。
- **清单**：`aapt2 dump badging` 确认 `sdkVersion:'23'`。
- **desugar 生效**：dex 里 `j$/nio/file/` 424 处、`j$/time/` 287 处、`j$/util/stream/` 777 处；
  `com/hongguotv/` 命名空间下**零残留**直接引用（残留都在 `j$/` 与 androidx 兼容层里，JVM 懒解析，安全）。
- **真机**：Redmi K30 5G（Android 11）上端到端跑通 —— 首页海报墙、详情页选集、
  第 1 集 1080P、第 10 / 11 集连续切换，零广告零崩溃。

## 已知限制

- **Android 6.0 实体设备尚未实机验证**。静态检查（dex 无高版本 API 残留）与 Android 11 真机通过，
  不等同于 Android 6 盒子验收。老芯片的硬件解码、音频输出、遥控器按键映射仍需实机确认。
- 若某台设备解码失败，先在设置里降到 720P；仍失败需要该设备的 `logcat`。
- `targetSdk` 保持 36，不随 `minSdk` 一起降。

## 来源

上游源码 `miaoxiaohu/hongguoTV` 0.9.95，GPL-3.0，本仓库保留其 `LICENSE` 与 `NOTICE.md` 署名。
本版相对上游的唯一改动是 `minSdk` 26 → 23 及配套的 desugaring 配置。
