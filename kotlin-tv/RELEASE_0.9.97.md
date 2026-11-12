# Kotlin 0.9.97：自动更新终于连得上了

<!-- notes -->
这一版修的是「检查更新」。上一版点下去只会说「尚无可用的自动更新版本」——
因为更新地址指向 GitHub，而国内连 github.com 是直接超时。
现在改走免费加速通道，能正常检查和下载了。
<!-- /notes -->

最低 Android 6.0 / API 23，versionCode 9097。单 APK，安装后联网使用，无需额外服务。沿用发布签名，可覆盖安装 0.9.96 及更早版本并保留本机收藏、观看记录和设置。

## 为什么上一版的「检查更新」是坏的

上一版点「版本与更新 → 检查更新」，弹的是 **「尚无可用的自动更新版本」**。这句话在代码里对应 **HTTP 404**，但真实原因有三层，而且互相盖着：

### 一、仓库是私有的

`yilishawk/mangju-tv-selfuse` 当时是 private。私有仓库的 release 资产匿名下载一律 404 —— 客户端手里没有 token，也不该有。已改为 public。

### 二、国内直连 github.com 是超时，不是慢

2026-11-12 在 Redmi K30 5G 上实测：

```
https://www.baidu.com  -> http=200
https://github.com     -> curl: (28) Connection timed out
ping github.com        -> 0% packet loss   ← 假象：DNS 被污染到假 IP
ping 140.82.121.4      -> 100% packet loss ← 真 GitHub 的 IP 根本不通
```

所以「能 ping 通」不能作为可用性证据。这条对任何国内盒子都成立，不是这台手机的特殊情况。

### 三、客户端自己的校验把 minSdk 23 判成非法

`AppUpdate.parse()` 里有一行 `require(update.minSdk in 26..100)` —— 上游假定最低就是 26。我们发的是 `minSdk: 23`，于是清单即使取到了也会被自己判成「更新信息格式不正确」。

## 这一版改了什么

1. **更新地址走加速前缀**（`AppUpdates.kt`）：

   ```kotlin
   const val MIRROR = "https://gh-proxy.com/"
   const val RELEASES = "${MIRROR}https://github.com/$REPOSITORY/releases"
   ```

   加速站只是把同一份 release 资产转发出来，tag 与文件名都不变，`update.json` 与 APK 用的是同一条链路。

2. **下载白名单放行加速站**：原来只允许 `github.com` / `release-assets.githubusercontent.com` / `objects.githubusercontent.com`，加速站会被自己的拦截器拒掉。现在加上 `gh-proxy.com`，重定向链仍然落在原有 CDN 主机上。

3. **`minSdk` 校验下界 26 → 23**，与真正发布的包一致。

4. 仓库从 private 改为 **public**（GPL-3.0 本就要求公开源码）。

## 实测（Redmi K30 5G，Android 11）

手机上逐个测加速站，不是凭猜：

| 加速站 | `update.json` | APK 3 MB |
|---|---|---|
| `gh-proxy.com` | **200** | 200，3,031,360 B，119 KB/s，25.5 s |
| `ghfast.top` | 302 → 200 | 200，3,031,360 B，151 KB/s，20.1 s |
| `ghproxy.net` | 302 → 200 | 未测 |
| `mirror.ghproxy.com` / `ghp.ci` / `ghproxy.cc` | 连接失败 | — |

选 `gh-proxy.com`：唯一直接 200、不依赖重定向的。

## 已知限制

- **加速站是单点**。`gh-proxy.com` 哪天挂了，自动更新会重新变成「无法检查更新」。改 `MIRROR` 一行、重新发版即可；客户端不会自己换站。这是「免费加速」这个选择本身的代价，不是遗漏。
- 下载速度 119–151 KB/s，3 MB 的包约 20–25 秒，盒子上升级会比局域网慢。
- 首次安装仍需手动（允许未知来源），之后才能自动升级。

## 来源

上游源码 `miaoxiaohu/hongguoTV` 0.9.95，GPL-3.0，保留其 `LICENSE` 与 `NOTICE.md` 署名。相对上游的改动：`minSdk` 26 → 23、更新源改指本仓库并走国内加速、若干 Android 6 兼容修复（详见 0.9.96）。
