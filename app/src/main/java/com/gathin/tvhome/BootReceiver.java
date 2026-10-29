package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.gathin.tvhome.profile.TvProfileManager;
import com.gathin.tvhome.profile.XiaomiProfile;

/**
 * 针对小米电视等开机自启原生 PatchWall 的设备：
 * 在系统启动完成 (BOOT_COMPLETED) 或快速开机时，自动将 Pure TV 拉回前台。
 * 针对创维盒子 / 创维电视等标准设备不做任何额外干预。
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();
        Log.i(TAG, "Received boot broadcast action: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            if (TvProfileManager.getProfile(context) instanceof XiaomiProfile) {
                try {
                    context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
                } catch (Exception ignored) {}

                Intent home = new Intent(context, MainActivity.class)
                        .addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK 
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP 
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                try {
                    context.startActivity(home);
                } catch (Exception ignored) {}
            }
        }
    }
}

