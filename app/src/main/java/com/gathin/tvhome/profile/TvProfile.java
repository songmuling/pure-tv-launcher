package com.gathin.tvhome.profile;

import android.content.Context;
import android.content.Intent;

/**
 * 电视/盒子设备适配策略接口 (Device Profile Strategy)
 * 用于解耦不同品牌厂商（创维、小米、索尼、海信等）在系统设置、WiFi、信号源切换等方面的私有实现。
 */
public interface TvProfile {
    /** 策略名称（如 "Skyworth", "Xiaomi", "Standard"） */
    String getName();

    /** 获取厂商专属的系统设置 Intent，若无特殊实现返回 null */
    Intent getSettingsIntent(Context context);

    /** 获取厂商专属的 WiFi 设置 Intent，若无特殊实现返回 null */
    Intent getWifiSettingsIntent(Context context);

    /** 是否强制或支持显示输入源 (HDMI) 胶囊按键 */
    boolean shouldShowInputsPill(Context context);

    /** 获取厂商专属的直达信号源切换面板 Intent，若无返回 null */
    Intent getCustomInputsPanelIntent(Context context);
}
