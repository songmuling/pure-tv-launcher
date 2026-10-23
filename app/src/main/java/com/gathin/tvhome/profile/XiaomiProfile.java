package com.gathin.tvhome.profile;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * 小米电视 / 红米电视 / 峰米投影适配策略 (MIUI TV / PatchWall)
 * 核心特性：
 * 1. MIUI TV 原生系统设置：com.xiaomi.mitv.settings.SETTINGS 或 entry.MainActivity
 * 2. MIUI TV 原生 WiFi 设置：com.xiaomi.mitv.settings.WIFI_SETTINGS
 * 3. 状态栏强制显示输入源按键：直达小米官方信号源面板与电视播放器
 */
public class XiaomiProfile extends DefaultProfile {
    public static final String PACKAGE_SETTINGS = "com.xiaomi.mitv.settings";
    public static final String CLASS_SETTINGS_MAIN = "com.xiaomi.mitv.settings.entry.MainActivity";
    public static final String ACTION_SETTINGS = "com.xiaomi.mitv.settings.SETTINGS";
    public static final String ACTION_WIFI = "com.xiaomi.mitv.settings.WIFI_SETTINGS";

    public static final String PACKAGE_TVPLAYER = "com.xiaomi.mitv.tvplayer";
    public static final String CLASS_EXT_SOURCE = "com.xiaomi.mitv.tvplayer.ExternalSourceActivity";

    @Override
    public String getName() {
        return "Xiaomi (小米/红米)";
    }

    @Override
    public Intent getSettingsIntent(Context context) {
        Intent i = new Intent(ACTION_SETTINGS);
        if (i.resolveActivity(context.getPackageManager()) != null) return i;

        i = new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(PACKAGE_SETTINGS, CLASS_SETTINGS_MAIN));
        if (i.resolveActivity(context.getPackageManager()) != null) return i;

        return super.getSettingsIntent(context);
    }

    @Override
    public Intent getWifiSettingsIntent(Context context) {
        Intent i = new Intent(ACTION_WIFI);
        if (i.resolveActivity(context.getPackageManager()) != null) return i;

        return super.getWifiSettingsIntent(context);
    }

    @Override
    public boolean shouldShowInputsPill(Context context) {
        // 小米电视：避免多此一举进入电视设置，不强制显示输入源按键
        return false;
    }

    @Override
    public Intent getCustomInputsPanelIntent(Context context) {
        return null;
    }
}
