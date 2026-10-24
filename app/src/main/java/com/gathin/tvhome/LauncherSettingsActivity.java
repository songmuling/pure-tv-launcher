package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * 外部设置启动中转 Activity：
 * 将外部或系统的启动指令直接转发给主桌面 MainActivity 弹出全屏 LauncherSettingsDialog，
 * 避免单独 Activity 造成的黑屏闪烁与桌面底栏透底问题。
 */
public final class LauncherSettingsActivity extends Activity {
    public static final String MODE_RANDOM = "random";
    public static final String MODE_WEATHER = "random"; // 向下兼容
    public static final String MODE_BING = "bing";
    public static final String MODE_OFF = "off";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(MainActivity.ACTION_LAUNCHER_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}
