# 红果 TV（Android 6.0+ 自用版）

红果短剧的非官方 Android TV 客户端。纯 Kotlin + Android 原生 View + Media3，**不含任何 native 库**（0 个 `.so`），因此与 CPU 架构无关，`armeabi-v7a` 的 32 位电视盒子也能安装。

遥控器操作：方向键选剧、确认键播放、`↑` 呼出选集、`←/→` 快退快进。

---

## 与上游的差异

本仓库是 [miaoxiaohu/hongguoTV](https://github.com/miaoxiaohu/hongguoTV) 的修改版，**唯一实质改动是把 `minSdk` 从 26 降到 23**，让 Android 6.0 的老电视/盒子也能装。

| 项 | 上游 | 本仓库 |
|---|---|---|
| `minSdk` | 26（Android 8.0） | **23（Android 6.0）** |
| `targetSdk` / `compileSdk` | 36 | 36（不变） |
| APK 命名 | `...-android8.apk` | `...-android6.apk` |
| 构建产物 | 2.7 MB | 约 2.7 MB |

降低 `minSdk` 后重新编译是必须的：上游在 `minSdk=26` 下编译时，`java.nio.file`、`java.time` 等按系统 API 处理；改成 23 后 AGP 的 core library desugaring 才会把它们重写为 `j$.` 前缀的实现（已开启 `desugar_jdk_libs_nio`）。直接改 APK 里的 `minSdkVersion` 而不重编译，会在 Android 6 上抛 `NoClassDefFoundError`。

验证方式（本仓库构建产物）：

```
# dex 里 com/hongguotv/ 下零残留 java.nio.file / java.time / java.util.stream 引用
# 残留引用全部位于 j$/ 与 androidx 兼容层，属 desugar 库自身实现
```

---

## 来源与许可

**GPL-3.0-only**，见 [LICENSE](LICENSE)。

本项目是以下 GPL-3.0 源码的衍生作品，版权与署名要求见 [kotlin-tv/NOTICE.md](kotlin-tv/NOTICE.md)：

- 上游客户端：[miaoxiaohu/hongguoTV](https://github.com/miaoxiaohu/hongguoTV)
- 其上游：[N3urda/hongguoTV](https://github.com/N3urda/hongguoTV)（播放签名 Kotlin 实现）
- 设备注册：[tsukiyo-zero/hongguoTV](https://github.com/tsukiyo-zero/hongguoTV)
- 签名算法原始实现：[ch6vip/fqapp](https://github.com/ch6vip/fqapp)（Rust）、[drpys](https://github.com/woshishiq1/drpys)（JS）
- 协议常量对照：[sunnysky123/hongguo-web](https://github.com/sunnysky123/hongguo-web)

播放取址的签名（551 Argus / Ladon / Gorgon）与匿名设备注册均为上述项目的 Kotlin 移植，本仓库未做算法改动。

---

## 构建

需要 JDK 17、Android SDK Platform 36 / Build Tools 36.0.0。

```sh
# 本地构建（debug，可直接安装）
cd kotlin-tv
echo "sdk.dir=/absolute/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

正式包需要签名密钥。创建 `kotlin-tv/keystore.properties`：

```properties
storeFile=/absolute/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

```sh
./scripts/build-kotlin.sh
# 输出：out/hongguotv-kotlin-<versionName>-android6.apk
```

GitHub Actions 发布：推送 `kotlin-vX.Y.Z` 标签即可，见 [.github/workflows/release.yml](.github/workflows/release.yml)。需要配置 4 个 secret：

| Secret | 内容 |
|---|---|
| `HONGGUOTV_KEYSTORE_B64` | 签名密钥的 base64（`base64 -w0 release.jks`） |
| `HONGGUOTV_STORE_PASSWORD` | keystore 口令 |
| `HONGGUOTV_KEY_ALIAS` | key 别名 |
| `HONGGUOTV_KEY_PASSWORD` | key 口令 |

---

## 免责声明

本项目仅供学习与技术研究，非官方客户端，与红果短剧及其运营方无关。不得用于商业用途或任何侵犯他人权益的场景。使用产生的一切后果由使用者自行承担。

内容数据来自公开接口，本项目不托管、不分发任何视频内容。
