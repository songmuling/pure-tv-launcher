package com.gathin.tvhome.profile;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/**
 * 通用标准 AOSP / Google Android TV 策略
 */
public class DefaultProfile implements TvProfile {
    @Override
    public String getName() {
        return "Default (AOSP / Android TV)";
    }

    @Override
    public Intent getSettingsIntent(Context context) {
        return new Intent(Settings.ACTION_SETTINGS);
    }

    @Override
    public Intent getWifiSettingsIntent(Context context) {
        return new Intent(Settings.ACTION_WIFI_SETTINGS);
    }

    @Override
    public boolean shouldShowInputsPill(Context context) {
        return true;
    }

    @Override
    public Intent getCustomInputsPanelIntent(Context context) {
        return null;
    }
}
