package com.gathin.tvhome.profile;

import android.content.Context;
import android.os.Build;
import android.util.Log;

/**
 * 电视设备策略管理与自动探测工厂 (TvProfileManager)
 * 启动时根据当前设备的 Brand, Manufacturer 及特征组件自动匹配最佳策略。
 * 遵循策略模式 (Strategy Pattern)，后续新增品牌（索尼、海信等）只需新建 TvProfile 实现类并在此注册。
 */
public class TvProfileManager {
    private static final String TAG = "TvProfileManager";
    private static TvProfile currentProfile = null;

    public static synchronized TvProfile getProfile(Context context) {
        if (currentProfile == null) {
            currentProfile = detect(context.getApplicationContext());
            Log.i(TAG, "Initialized TV profile: " + currentProfile.getName());
        }
        return currentProfile;
    }

    /**
     * 获取当前机型最佳的系统设置 Intent（优先厂商私有设置，若无法 resolve 则回退到标准 AOSP 设置）
     */
    public static Intent getSettingsIntent(Context context) {
        TvProfile profile = getProfile(context);
        Intent custom = profile.getSettingsIntent(context);
        if (custom != null && custom.resolveActivity(context.getPackageManager()) != null) {
            return custom;
        }
        Intent standard = new Intent(android.provider.Settings.ACTION_SETTINGS);
        if (standard.resolveActivity(context.getPackageManager()) != null) {
            return standard;
        }
        return custom != null ? custom : standard;
    }

    /**
     * 获取当前机型最佳的 WiFi 设置 Intent
     */
    public static Intent getWifiSettingsIntent(Context context) {
        TvProfile profile = getProfile(context);
        Intent custom = profile.getWifiSettingsIntent(context);
        if (custom != null && custom.resolveActivity(context.getPackageManager()) != null) {
            return custom;
        }
        Intent standard = new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS);
        if (standard.resolveActivity(context.getPackageManager()) != null) {
            return standard;
        }
        return getSettingsIntent(context);
    }

    private static TvProfile detect(Context context) {
        String brand = Build.BRAND != null ? Build.BRAND.toLowerCase() : "";
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase() : "";
        String model = Build.MODEL != null ? Build.MODEL.toLowerCase() : "";

        // 1. 优先检测创维特征（创维盒子通常为 Skyworth 或存在专属设置包）
        if (brand.contains("skyworth") || manufacturer.contains("skyworth")
                || isPackageInstalled(context, SkyworthProfile.PACKAGE_SETTINGS)) {
            return new SkyworthProfile();
        }

        // 2. 检测小米/红米特征（Xiaomi / Redmi / MIUI TV 或存在小米设置）
        if (brand.contains("xiaomi") || brand.contains("redmi")
                || manufacturer.contains("xiaomi") || model.contains("mitv")
                || isPackageInstalled(context, XiaomiProfile.PACKAGE_SETTINGS)) {
            return new XiaomiProfile();
        }

        // 3. 兜底通用策略
        return new DefaultProfile();
    }

    private static boolean isPackageInstalled(Context context, String packageName) {
        try {
            return context.getPackageManager().getPackageInfo(packageName, 0) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
