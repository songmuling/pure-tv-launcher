package com.gathin.tvhome.profile;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * 创维小湃盒子 / 创维电视适配策略 (πOS)
 * 核心特性：
 * 1. 创维系统设置：com.skyworthdigital.settings/.activity.MainActivity
 * 2. 白名单包名（通过 Gradle flavor 设定为 com.ktcp.launcher）
 */
public class SkyworthProfile extends DefaultProfile {
    public static final String PACKAGE_SETTINGS = "com.skyworthdigital.settings";
    public static final String CLASS_SETTINGS_MAIN = "com.skyworthdigital.settings.activity.MainActivity";

    @Override
    public String getName() {
        return "Skyworth (创维)";
    }

    @Override
    public Intent getSettingsIntent(Context context) {
        Intent skyworth = new Intent().setComponent(new ComponentName(PACKAGE_SETTINGS, CLASS_SETTINGS_MAIN));
        if (skyworth.resolveActivity(context.getPackageManager()) != null) {
            return skyworth;
        }
        return super.getSettingsIntent(context);
    }
}
