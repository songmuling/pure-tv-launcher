# Pure TV Launcher (极简纯净电视桌面)

一款专为 Android TV、智能电视和电视盒子深度定制的超轻量、纯净无广告桌面。

基于 tvOS 极简美学设计，去除所有开机广告、预装推广、强制弹窗，并针对国产电视/盒子（创维、小米等）的底层系统做了专项兼容与白名单穿透。

---

## ✨ 核心特性

1. **多厂商设备专版支持 (Multi-Flavors)**
   - **通用标准版 (`universal`)**：适合小米、索尼、海信、TCL、长虹、飞利浦以及外贸 Google TV 电视/盒子等 **99% 的设备**。内置对小米电视 MIUI TV 专有设置（`com.xiaomi.mitv.settings`）的动态直达支持，全兼容 Android 5.0 ~ 14+。
   - **创维专版 (`skyworth`)**：创维电视与创维小湃盒子（P3 Pro 等）专用。包名采用 `com.ktcp.launcher`（企鹅极光/云视听极光出厂白名单），具备 `directBootAware="true"`，完美突破创维盒子的防劫持强杀保护，开机秒进，按 Home 键永不跳回原厂桌面。

2. **Apple TV 纯正极简 Dock 桌面**
   - 桌面极度纯粹，只在下方展示单行 **6 槽位 Dock 程序坞**，卡片严格绝对等宽等高，未填满的槽位优雅显示 `+` 号。
   - **智能槽位移位与互换**：点击 `+` 号或长按任意卡片即可一键换应用；从 `+` 号选择已有应用时原位置自动变 `+`，两已有应用对选时自动智能互换位置。

3. **毛玻璃「全部应用」全屏抽屉**
   - Dock 正下方居中布置 Apple TV 质感「⊞ 全部应用」入口，遥控器按下键直达，点击展开沉浸式 6 列应用中心，按返回键丝滑收起，点击任意应用秒开。

4. **微软官方 4K 天气摄影壁纸与每日壁纸**
   - **动态天气 4K 高清壁纸**：依据实时天气与昼夜晨昏自动换装微软官方 4K 超清大片摄影壁纸，安装包仅 ~2MB，真 4K 细腻震撼。
   - **天气城市手动指定**：支持全国 30+ 重点城市遥控器一键选择，即使家庭网络开启软路由 VPN / 代理，也不会错误定位到国外。
   - **必应每日 4K 超清壁纸**与**默认极简流光主题**自由一键切换。

5. **超广安卓版本兼容 (Android 5.0 - Android 14+)**
   - 将 `minSdk` 定为 **21**（支持 Android 5.0+），完美兼容小米电视 4A（Android 6.0 Marshmallow）等老旧电视。
   - 引入核心库反糖化（Core Library Desugaring `desugar_jdk_libs:2.0.4`），让老版本安卓也能平滑运行现代 Java 8+ 日期与集合 API，杜绝低版本闪退。

---

## 🛠️ 打包构建

本项目配置了完整的 GitHub Actions 持续集成，每次提交代码后自动在云端编译并发布最新的 **Release 正式版**：

- `PureTV-Universal-release.apk` —— 通用标准正式版（适合小米、索尼、海信、TCL等 99% 设备）
- `PureTV-Skyworth-release.apk` —— 创维盒子 / 电视专用正式版

如需本地编译：
```bash
# 编译所有 Release 正式版本
./gradlew assembleRelease

# 仅编译通用版
./gradlew assembleUniversalRelease

# 仅编译创维版
./gradlew assembleSkyworthRelease
```
