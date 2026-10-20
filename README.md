# Pure TV Launcher (极简纯净电视桌面)

一款专为 Android TV、智能电视和电视盒子深度定制的超轻量、纯净无广告桌面。

基于 tvOS 极简美学设计，去除所有开机广告、预装推广、强制弹窗，并针对国产电视/盒子（创维、小米等）的底层系统做了专项兼容与白名单穿透。

---

## ✨ 核心特性

1. **多厂商设备专版支持 (Multi-Flavors)**
   - **创维专版 (`skyworth`)**：包名采用 `com.ktcp.launcher`（企鹅极光/云视听极光出厂白名单），具备 `directBootAware="true"`，完美突破创维盒子的防劫持强杀保护，开机秒进，按 Home 键永不跳回原厂桌面。
   - **小米专版 (`xiaomi`)**：针对小米电视 MIUI TV 系统优化，设置菜单直通小米原生设置应用（`com.xiaomi.mitv.settings`）。
   - **通用标准版 (`universal`)**：标准 Android TV 桌面，适用于索尼、TCL、海信及原生 Android TV 设备。

2. **超广安卓版本兼容 (Android 5.0 - Android 14+)**
   - 将 `minSdk` 降至 **21**（支持 Android 5.0+），完美兼容小米电视 4A（Android 6.0 Marshmallow）等老旧电视。
   - 引入核心库反糖化（Core Library Desugaring `desugar_jdk_libs:2.0.4`），让老版本安卓也能平滑运行现代 Java 8+ 日期与集合 API，杜绝低版本闪退。

3. **必应每日 4K 高清壁纸 (Bing Daily Wallpaper)**
   - 每日开机自动后台拉取微软官方必应每日壁纸。
   - 智能本地缓存与自动清理，不占电视存储空间。
   - 独家优雅微暗遮罩算法（Dim Overlay），保证高亮壁纸下时钟、日期与桌面应用图标清晰可辨。
   - 支持在设置菜单中自由一键开启/关闭。

4. **全应用无障碍展示 (Full App Library)**
   - 自动识别所有已安装应用（包括手机版 APK、无 Leanback 属性的竖屏或工具类 App，如亲宝宝、网盘、文件管理器等）。
   - 桌面右上角「设置」长按遥控器 OK 键即可呼出桌面快捷设置（应用重排、常驻底栏 Dock 设定、壁纸开关等）。

---

## 🛠️ 打包构建

本项目配置了完整的 GitHub Actions 持续集成，每次提交代码后自动云端编译：

- `PureTV-Skyworth-debug.apk` —— 创维盒子 / 电视专用版
- `PureTV-Xiaomi-debug.apk` —— 小米电视专用版
- `PureTV-Universal-debug.apk` —— 通用标准版

如需本地编译：
```bash
# 编译所有版本
./gradlew assembleDebug

# 仅编译创维版
./gradlew assembleSkyworthDebug

# 仅编译小米版
./gradlew assembleXiaomiDebug
```
