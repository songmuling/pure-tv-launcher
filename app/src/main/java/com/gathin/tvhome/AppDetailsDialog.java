package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AppDetailsDialog {

    /**
     * 智能打开应用详情：
     * 1. 优先尝试系统原生的应用详情页（如创维电视、标准 Android TV，完全不改变现有行为）
     * 2. 若电视系统未内置应用详情 Activity（如小米电视等厂商精简系统），则无缝唤起纯净桌面现代化 TV 详情弹窗
     */
    public static void openAppDetails(Activity activity, String pkg) {
        if (activity == null || activity.isFinishing() || pkg == null) return;

        // 1. 优先尝试原生 Intent
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + pkg));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivity(intent);
                return;
            }
        } catch (Exception ignored) {}

        // 2. 原生系统不支持时，降级展示电视端现代详情弹窗
        show(activity, pkg);
    }

    /**
     * 显示现代化 TV 风格的应用详情卡片
     */
    public static void show(Activity activity, String pkg) {
        if (activity == null || activity.isFinishing() || pkg == null) return;

        PackageManager pm = activity.getPackageManager();
        PackageInfo pi;
        try {
            pi = pm.getPackageInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(activity, activity.getString(R.string.cant_open, pkg), Toast.LENGTH_SHORT).show();
            return;
        }

        ApplicationInfo ai = pi.applicationInfo;
        if (ai == null) return;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_app_details);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        ImageView appDetailIcon = dialog.findViewById(R.id.appDetailIcon);
        TextView appDetailTitle = dialog.findViewById(R.id.appDetailTitle);
        TextView appDetailType = dialog.findViewById(R.id.appDetailType);
        TextView appDetailPackage = dialog.findViewById(R.id.appDetailPackage);
        TextView appDetailVersion = dialog.findViewById(R.id.appDetailVersion);
        TextView appDetailSize = dialog.findViewById(R.id.appDetailSize);
        TextView appDetailTime = dialog.findViewById(R.id.appDetailTime);

        View btnAppOpen = dialog.findViewById(R.id.btnAppOpen);
        View btnAppManage = dialog.findViewById(R.id.btnAppManage);
        View btnAppUninstall = dialog.findViewById(R.id.btnAppUninstall);
        View btnAppClose = dialog.findViewById(R.id.btnAppClose);

        // 基本信息设置
        try {
            appDetailIcon.setImageDrawable(ai.loadIcon(pm));
        } catch (Exception ignored) {}

        CharSequence label = ai.loadLabel(pm);
        appDetailTitle.setText(label != null ? label : pkg);

        boolean isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        appDetailType.setText(isSystem ? R.string.app_details_type_system : R.string.app_details_type_user);
        appDetailType.setTextColor(isSystem ? 0xFF9E9E9E : 0xFF74A987);

        appDetailPackage.setText(pkg);

        String verText = (pi.versionName != null ? pi.versionName : "") + " (" + pi.versionCode + ")";
        appDetailVersion.setText(verText);

        long apkSize = 0;
        if (ai.sourceDir != null) {
            try {
                File f = new File(ai.sourceDir);
                if (f.exists()) apkSize = f.length();
            } catch (Exception ignored) {}
        }
        appDetailSize.setText(apkSize > 0 ? Formatter.formatFileSize(activity, apkSize) : "--");

        long time = pi.lastUpdateTime > 0 ? pi.lastUpdateTime : pi.firstInstallTime;
        String timeStr = time > 0 ? new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(time)) : "--";
        appDetailTime.setText(timeStr);

        // 【打开应用】按钮
        Intent launchIntent = pm.getLaunchIntentForPackage(pkg);
        if (launchIntent != null) {
            btnAppOpen.setVisibility(View.VISIBLE);
            btnAppOpen.setOnClickListener(v -> {
                dialog.dismiss();
                try {
                    activity.startActivity(launchIntent);
                } catch (Exception ex) {
                    Toast.makeText(activity, activity.getString(R.string.cant_open, label), Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            btnAppOpen.setVisibility(View.GONE);
        }

        // 【应用管理】按钮（适配小米电视应用商店管理及系统通用管理）
        Intent manageIntent = new Intent("com.xiaomi.mitv.appstore.appmanager.appmanageractivity");
        manageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (manageIntent.resolveActivity(pm) == null) {
            manageIntent = new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
            manageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        if (manageIntent.resolveActivity(pm) != null) {
            btnAppManage.setVisibility(View.VISIBLE);
            Intent finalManageIntent = manageIntent;
            btnAppManage.setOnClickListener(v -> {
                dialog.dismiss();
                try {
                    activity.startActivity(finalManageIntent);
                } catch (Exception ex) {
                    Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            btnAppManage.setVisibility(View.GONE);
        }

        // 【卸载应用】按钮（系统预装且非更新应用隐藏卸载按钮）
        boolean isUpdatedSystem = (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
        if (!isSystem || isUpdatedSystem) {
            btnAppUninstall.setVisibility(View.VISIBLE);
            btnAppUninstall.setOnClickListener(v -> {
                dialog.dismiss();
                requestUninstall(activity, pkg);
            });
        } else {
            btnAppUninstall.setVisibility(View.GONE);
        }

        // 【关闭】按钮
        btnAppClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        // 遥控器初始焦点
        if (btnAppOpen.getVisibility() == View.VISIBLE) {
            btnAppOpen.requestFocus();
        } else if (btnAppManage.getVisibility() == View.VISIBLE) {
            btnAppManage.requestFocus();
        } else {
            btnAppClose.requestFocus();
        }
    }

    /**
     * 兼容性卸载逻辑
     */
    public static void requestUninstall(Activity activity, String pkg) {
        if (activity == null || pkg == null) return;
        boolean started = false;
        try {
            Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
            intent.setData(Uri.parse("package:" + pkg));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            started = true;
        } catch (Exception ignored) {}

        if (!started) {
            try {
                Intent intent = new Intent(Intent.ACTION_DELETE);
                intent.setData(Uri.parse("package:" + pkg));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
                started = true;
            } catch (Exception ignored) {}
        }

        if (!started) {
            Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }
}
