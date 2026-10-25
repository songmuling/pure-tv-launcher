package com.gathin.tvhome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 安装代理 Activity：
 * 拦截来自当贝市场、浏览器、文件管理器等的 APK 安装请求，
 * 绕过被酷开限制的 PackageInstallerActivity（应用圈弹窗拦截），
 * 直接调用创维系统底层的 AutoInstallerActivityV2 (coocaa.intent.action.AUTOINSTALLER) 实现无阻断安装。
 */
public class InstallProxyActivity extends Activity {
    private static final String TAG = "InstallProxy";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            finish();
            return;
        }

        Uri uri = intent.getData();
        if (uri == null && intent.getExtras() != null) {
            Object extraStream = intent.getExtras().get(Intent.EXTRA_STREAM);
            if (extraStream instanceof Uri) {
                uri = (Uri) extraStream;
            }
        }

        if (uri == null) {
            Log.e(TAG, "No URI found in intent: " + intent);
            Toast.makeText(this, "未找到安装包数据", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Log.i(TAG, "Received install intent with URI: " + uri);
        String targetPath = resolveOrCopyApk(uri);

        if (targetPath != null && new File(targetPath).exists()) {
            Log.i(TAG, "Launching Skyworth AutoInstaller for: " + targetPath);
            try {
                Intent autoIntent = new Intent("coocaa.intent.action.AUTOINSTALLER");
                autoIntent.setComponent(new ComponentName("com.tianci.autoinstaller", "com.tianci.autoinstaller.v2.AutoInstallerActivityV2"));
                autoIntent.putExtra("apkfilepath", targetPath);
                autoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(autoIntent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch AutoInstallerActivityV2, fallback to action only", e);
                try {
                    Intent autoIntent = new Intent("coocaa.intent.action.AUTOINSTALLER");
                    autoIntent.putExtra("apkfilepath", targetPath);
                    autoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(autoIntent);
                } catch (Exception e2) {
                    Log.e(TAG, "All autoinstaller launches failed", e2);
                    Toast.makeText(this, "调起创维安装器失败: " + e2.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        } else {
            Toast.makeText(this, "解析安装包路径失败", Toast.LENGTH_SHORT).show();
        }

        finish();
    }

    private String resolveOrCopyApk(Uri uri) {
        String scheme = uri.getScheme();
        if ("file".equalsIgnoreCase(scheme)) {
            String path = uri.getPath();
            if (path != null && new File(path).exists()) {
                return path;
            }
        }

        // Try to query real path from content provider
        if ("content".equalsIgnoreCase(scheme)) {
            try {
                Cursor cursor = getContentResolver().query(uri, new String[]{"_data"}, null, null, null);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        int idx = cursor.getColumnIndex("_data");
                        if (idx != -1) {
                            String path = cursor.getString(idx);
                            if (path != null && new File(path).exists()) {
                                cursor.close();
                                return path;
                            }
                        }
                    }
                    cursor.close();
                }
            } catch (Throwable t) {
                Log.w(TAG, "Failed to query _data from content URI: " + t.getMessage());
            }
        }

        // If direct path cannot be obtained, copy stream to public /sdcard/Download/ directory
        try {
            File destDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!destDir.exists()) {
                destDir.mkdirs();
            }
            File outFile = new File(destDir, ".puretv_install_cache.apk");
            if (outFile.exists()) {
                outFile.delete();
            }

            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) {
                Log.e(TAG, "Cannot open stream for URI: " + uri);
                return null;
            }

            OutputStream out = new FileOutputStream(outFile);
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            out.close();
            in.close();

            outFile.setReadable(true, false);
            return outFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy APK stream", e);
            return null;
        }
    }
}
