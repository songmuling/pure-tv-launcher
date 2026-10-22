# Pure TV Launcher 多品牌电视/盒子设备适配体系架构文档 (AI & 开发者指南)

> **文档目的**: 为后续维护本项目的 AI 编程助手与开发者提供清晰的跨品牌电视适配架构指南。所有跨品牌、跨 Android 版本的特异性逻辑必须遵循本文档规范，严禁在 `MainActivity.java` 中堆砌硬编码 `if-else`。

---

## 一、 核心架构原则：策略模式 (Strategy Pattern)

本项目面向各种不同品牌的智能电视与电视盒子（创维 πOS、小米 MIUI TV、索尼 BRAVIA、海信 VIDAA、标准 AOSP Android TV 等）。
各品牌在**系统设置入口**、**WiFi 设置入口**、**HDMI 信号源面板**、**系统按键拦截**上存在严重碎片化。

为此，所有设备差异全部收归在 `com.gathin.tvhome.profile` 包下：

```text
app/src/main/java/com/gathin/tvhome/profile/
├── TvProfile.java          # 统一策略接口 (Strategy Interface)
├── DefaultProfile.java     # 通用标准策略 (AOSP / Google Android TV)
├── SkyworthProfile.java    # 创维小湃盒子 / 创维电视策略 (πOS)
├── XiaomiProfile.java      # 小米电视 / 红米电视 / 峰米投影策略 (MIUI TV)
└── TvProfileManager.java   # 设备自动探测与策略分发工厂 (Factory)
```

---

## 二、 现有各品牌适配细节与防坑指南

### 1. 创维小湃盒子 (Skyworth P3 Pro / πOS)
* **系统版本**: Android 9.0 (API 28)
* **核心防坑与白名单**:
  * 创维在 `system_server` 的 `PackageManagerService` 中植入了出厂白名单 `settings secure enable_launcher_components`，只放行：
    `com.skyworthdigital.sky2dlauncher / com.android.provision / com.ktcp.launcher`
  * **解决方案**: 创维专版必须使用 Gradle Flavor `skyworth`，构建时将 `applicationId` 设定为 **`com.ktcp.launcher`**（腾讯极光桌面伪装包名）。
  * 必须在 `AndroidManifest.xml` 中配置 **`android:directBootAware="true"`**，否则冷开机会掉进 Provision 屏保黑屏。
* **系统设置路由**:
  * 创维原厂完整设置组件为：`com.skyworthdigital.settings/.activity.MainActivity`。
  * `SkyworthProfile` 优先接管并直达该组件。

### 2. 小米电视 / 红米电视 (Xiaomi MiTV 4A 等 / MIUI TV)
* **系统版本**: Android 6.0 (API 23) ~ Android 10+
* **核心防坑与老系统兼容 (重要！)**:
  * **API 23 (Android 6.0) 兼容保护**: Android 6.0 没有 `forceHasOverlappingRendering`、`registerDefaultNetworkCallback` 和 `info.isHidden(Context)`，所有这些调用必须被 `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)` 包裹，并配合 `try-catch`，否则在小米 4A 上会直接闪退。
  * **网络设置置灰原理**: 当电视插着物理网线（`eth0` 处于 UP 状态）时，MIUI TV 系统会自动锁定并置灰无线 WiFi 设置，设置中的“手动设置: 关闭”代表 DHCP 自动获取（非有线被关）。拔掉网线后 WiFi 即可恢复。
* **系统设置路由**:
  * Action: `com.xiaomi.mitv.settings.SETTINGS`
  * WiFi: `com.xiaomi.mitv.settings.WIFI_SETTINGS`
  * 备用 Component: `com.xiaomi.mitv.settings/com.xiaomi.mitv.settings.entry.MainActivity`
* **信号源面板**:
  * 小米电视通常没有完整的标准 Android TV TIF 映射，`XiaomiProfile.shouldShowInputsPill()` 强制常驻显示状态栏 HDMI 胶囊，点击直达小米原生信号源切换面板。

### 3. 通用标准设备 (Default / AOSP / Google TV / 索尼)
* 标准 Android TV 使用系统自带的 `android.provider.Settings.ACTION_SETTINGS` 与 `ACTION_WIFI_SETTINGS`。
* 信号源通过标准 `TvInputManager` 自动检索硬件 HDMI 端口状态。

---

## 三、 如何新增一个品牌适配？（3 步极速拓展）

以新增 **索尼电视 (Sony BRAVIA)** 为例：

### 第一步：在 `profile/` 目录下创建新策略类
```java
package com.gathin.tvhome.profile;

import android.content.Context;
import android.content.Intent;

public class SonyProfile extends DefaultProfile {
    @Override
    public String getName() {
        return "Sony (索尼 BRAVIA)";
    }

    @Override
    public Intent getSettingsIntent(Context context) {
        // 索尼专属快速设置或画质设置 Intent
        Intent i = new Intent("com.sony.dtv.settings.SETTINGS");
        if (i.resolveActivity(context.getPackageManager()) != null) return i;
        return super.getSettingsIntent(context);
    }
}
```

### 第二步：在 `TvProfileManager.detect()` 中注册探测规则
```java
if (brand.contains("sony") || manufacturer.contains("sony")) {
    return new SonyProfile();
}
```

### 第三步：在 `AndroidManifest.xml` 的 `<queries>` 中注册包名
如果该品牌设置涉及私有包名（Android 11+ 包可见性限制），在 `<queries>` 标签中添加该包名即可。

**严禁规则**:
1. 绝对不要在 `MainActivity.java` 中直接写死具体品牌的包名或私有 Intent！
2. 修改任何 Profile 时，绝对不能影响 `DefaultProfile` 和其他品牌的 Profile。
