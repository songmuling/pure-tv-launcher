package com.gathin.tvhome.profile;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/**
 * 创维智能电视适配策略 (Skyworth TV / CCOS 酷开系统，例如 SKYWORTH 65A3)
 * 注意：本类专用于创维电视（含画质、声音、HDMI信号源切换），
 * 与创维盒子策略 (SkyworthProfile / com.skyworthdigital.settings) 相互独立，互不影响！
 */
public class SkyworthTvProfile extends DefaultProfile {
    public static final String PACKAGE_SOFTSETTINGS = "com.coocaa.os.softsettings";
    public static final String CLASS_SOFTSETTINGS_MAIN = "com.coocaa.os.softsettings.framework.ui.SettingsActivity";

    public static final String PACKAGE_SKY_SETTING = "com.skyworth.setting";
    public static final String CLASS_SKY_SETTING_MAIN = "com.skyworth.setting.module.main.MainActivity";

    public static final String CLASS_SKY_SOURCE = "com.skyworth.setting.module.source.SourceActivity";
    public static final String CLASS_SKY_NETWORK = "com.skyworth.setting.module.network.NetWorkActivity";

    @Override
    public String getName() {
        return "Skyworth TV (创维酷开电视)";
    }

    @Override
    public Intent getSettingsIntent(Context context) {
        // 1. 优先调用创维电视官方原厂完整设置（通用/网络/外设/图像/声音/信号源）
        Intent softSettings = new Intent().setComponent(new ComponentName(PACKAGE_SOFTSETTINGS, CLASS_SOFTSETTINGS_MAIN));
        if (softSettings.resolveActivity(context.getPackageManager()) != null) {
            return softSettings;
        }

        // 2. 备选调用创维电视专用设置模块主界面
        Intent skySetting = new Intent().setComponent(new ComponentName(PACKAGE_SKY_SETTING, CLASS_SKY_SETTING_MAIN));
        if (skySetting.resolveActivity(context.getPackageManager()) != null) {
            return skySetting;
        }

        // 3. 回退系统标准设置
        return super.getSettingsIntent(context);
    }

    @Override
    public Intent getWifiSettingsIntent(Context context) {
        // 创维电视网络设置界面
        Intent netIntent = new Intent().setComponent(new ComponentName(PACKAGE_SKY_SETTING, CLASS_SKY_NETWORK));
        if (netIntent.resolveActivity(context.getPackageManager()) != null) {
            return netIntent;
        }
        return super.getWifiSettingsIntent(context);
    }

    @Override
    public boolean shouldShowInputsPill(Context context) {
        // 创维电视：用户不需要在桌面展示输入源切换胶囊，彻底隐藏
        return false;
    }

    @Override
    public Intent getCustomInputsPanelIntent(Context context) {
        // 创维原厂信号源浮层面板（HDMI1 / HDMI2 / AV 快速切换）
        Intent sourceIntent = new Intent().setComponent(new ComponentName(PACKAGE_SKY_SETTING, CLASS_SKY_SOURCE));
        if (sourceIntent.resolveActivity(context.getPackageManager()) != null) {
            return sourceIntent;
        }
        return null;
    }
}
