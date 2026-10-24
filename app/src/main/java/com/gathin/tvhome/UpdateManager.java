package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * 负责从 GitHub Releases 检测新版本、下载对应机型 APK 并通过 FileProvider 拉起系统安装。
 * 全面兼容 Android 6.0 (Marshmallow) 到 Android 14+。
 */
public final class UpdateManager {

    private static final String GITHUB_RELEASES_API =
            "https://api.github.com/repos/songmuling/pure-tv-launcher/releases/latest";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Pattern RUN_NUMBER_PATTERN = Pattern.compile("v1\\.0\\.(\\d+)");

    public static class UpdateInfo {
        public final String tagName;
        public final int runNumber;
        public final String changelog;
        public final String downloadUrl;
        public final boolean hasUpdate;

        public UpdateInfo(String tagName, int runNumber, String changelog, String downloadUrl, boolean hasUpdate) {
            this.tagName = tagName;
            this.runNumber = runNumber;
            this.changelog = changelog;
            this.downloadUrl = downloadUrl;
            this.hasUpdate = hasUpdate;
        }
    }

    public interface Callback {
        void onResult(UpdateInfo info);
    }

    public interface DownloadCallback {
        void onProgress(int progress);
        void onSuccess(File file);
        void onError(String error);
    }

    private UpdateManager() {}

    public static int getCurrentVersionCode(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionCode;
        } catch (Exception e) {
            return 41;
        }
    }

    public static String getCurrentVersionName(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName;
        } catch (Exception e) {
            return "1.0.41";
        }
    }

    private static void setupTrustAllSsl(HttpURLConnection conn) {
        // Android 8.0 (API 26) 及以上拥有完整现代 CA 根证书与原生 SNI 扩展，切勿覆盖避免破坏 TLS 握手
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return;
        }
        if (conn instanceof HttpsURLConnection) {
            try {
                TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
                };
                SSLContext sc;
                try {
                    sc = SSLContext.getInstance("TLSv1.2");
                } catch (Exception e) {
                    sc = SSLContext.getInstance("TLS");
                }
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                javax.net.ssl.SSLSocketFactory defaultFactory = sc.getSocketFactory();
                javax.net.ssl.SSLSocketFactory wrappedFactory = new javax.net.ssl.SSLSocketFactory() {
                    @Override
                    public String[] getDefaultCipherSuites() {
                        return defaultFactory.getDefaultCipherSuites();
                    }

                    @Override
                    public String[] getSupportedCipherSuites() {
                        return defaultFactory.getSupportedCipherSuites();
                    }

                    private java.net.Socket enableTls(java.net.Socket socket) {
                        if (socket instanceof javax.net.ssl.SSLSocket) {
                            try {
                                ((javax.net.ssl.SSLSocket) socket).setEnabledProtocols(
                                        ((javax.net.ssl.SSLSocket) socket).getSupportedProtocols());
                            } catch (Exception ignored) {}
                        }
                        return socket;
                    }

                    @Override
                    public java.net.Socket createSocket(java.net.Socket s, String host, int port, boolean autoClose) throws java.io.IOException {
                        return enableTls(defaultFactory.createSocket(s, host, port, autoClose));
                    }

                    @Override
                    public java.net.Socket createSocket(String host, int port) throws java.io.IOException {
                        return enableTls(defaultFactory.createSocket(host, port));
                    }

                    @Override
                    public java.net.Socket createSocket(String host, int port, java.net.InetAddress localHost, int localPort) throws java.io.IOException {
                        return enableTls(defaultFactory.createSocket(host, port, localHost, localPort));
                    }

                    @Override
                    public java.net.Socket createSocket(java.net.InetAddress host, int port) throws java.io.IOException {
                        return enableTls(defaultFactory.createSocket(host, port));
                    }

                    @Override
                    public java.net.Socket createSocket(java.net.InetAddress address, int port, java.net.InetAddress localAddress, int localPort) throws java.io.IOException {
                        return enableTls(defaultFactory.createSocket(address, port, localAddress, localPort));
                    }
                };

                ((HttpsURLConnection) conn).setSSLSocketFactory(wrappedFactory);
                ((HttpsURLConnection) conn).setHostnameVerifier((hostname, session) -> true);
            } catch (Exception ignored) {}
        }
    }

    /**
     * 异步后台检测 GitHub 是否有更新
     */
    public static void checkUpdate(Context context, Callback callback) {
        final Context appCtx = context.getApplicationContext();
        final boolean isSkyworth = appCtx.getPackageName().contains("ktcp");
        new Thread(() -> {
            HttpURLConnection conn = null;
            BufferedReader reader = null;
            try {
                URL url = new URL(GITHUB_RELEASES_API);
                conn = (HttpURLConnection) url.openConnection();
                setupTrustAllSsl(conn);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + "; " + Build.MODEL + ") PureTV-Launcher");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    android.util.Log.w("UpdateManager", "GitHub API response: " + code + ", trying redirect fallback");
                    UpdateInfo fallback = checkUpdateViaRedirect(appCtx, isSkyworth);
                    MAIN_HANDLER.post(() -> callback.onResult(fallback != null ? fallback : new UpdateInfo("", 0, "", "", false)));
                    return;
                }

                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }

                JSONObject json = new JSONObject(sb.toString());
                String tagName = json.optString("tag_name", "");
                String body = json.optString("body", "");
                JSONArray assets = json.optJSONArray("assets");

                int remoteRunNumber = parseRunNumber(tagName);
                String downloadUrl = "";

                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String name = asset.optString("name", "");
                        if (isSkyworth && name.contains("Skyworth")) {
                            downloadUrl = asset.optString("browser_download_url", "");
                            break;
                        } else if (!isSkyworth && name.contains("Universal")) {
                            downloadUrl = asset.optString("browser_download_url", "");
                            break;
                        }
                    }
                }

                int localCode = getCurrentVersionCode(appCtx);
                boolean hasUpdate = (remoteRunNumber > localCode) || (localCode < 42 && remoteRunNumber >= 42);

                UpdateInfo info = new UpdateInfo(tagName, remoteRunNumber, body, downloadUrl, hasUpdate);
                MAIN_HANDLER.post(() -> callback.onResult(info));

            } catch (Exception e) {
                android.util.Log.w("UpdateManager", "GitHub API check error: " + e.getMessage() + ", trying redirect fallback");
                UpdateInfo fallback = checkUpdateViaRedirect(appCtx, isSkyworth);
                MAIN_HANDLER.post(() -> callback.onResult(fallback != null ? fallback : new UpdateInfo("", 0, "", "", false)));
            } finally {
                try { if (reader != null) reader.close(); } catch (Exception ignored) {}
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private static UpdateInfo checkUpdateViaRedirect(Context context, boolean isSkyworth) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://github.com/songmuling/pure-tv-launcher/releases/latest");
            conn = (HttpURLConnection) url.openConnection();
            setupTrustAllSsl(conn);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + "; " + Build.MODEL + ") PureTV-Launcher");
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(12000);

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                if (location != null && location.contains("/releases/tag/")) {
                    String tagName = location.substring(location.lastIndexOf('/') + 1).trim();
                    int remoteRunNumber = parseRunNumber(tagName);
                    String fileName = isSkyworth ? "PureTV-Skyworth-release.apk" : "PureTV-Universal-release.apk";
                    String downloadUrl = "https://github.com/songmuling/pure-tv-launcher/releases/download/" + tagName + "/" + fileName;
                    int localCode = getCurrentVersionCode(context);
                    boolean hasUpdate = (remoteRunNumber > localCode) || (localCode < 42 && remoteRunNumber >= 42);
                    return new UpdateInfo(tagName, remoteRunNumber, "· 修复桌面按Home键壁纸闪烁问题\n· 优化电视端网络更新检测与兼容性", downloadUrl, hasUpdate);
                }
            }
        } catch (Exception e) {
            android.util.Log.w("UpdateManager", "checkUpdateViaRedirect error: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static int parseRunNumber(String tagName) {
        if (tagName == null || tagName.isEmpty()) return 0;
        Matcher m = RUN_NUMBER_PATTERN.matcher(tagName);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception ignored) {}
        }
        return 0;
    }

    /**
     * 弹出现代化电视更新对话框
     */
    public static void showUpdateDialog(Activity activity, UpdateInfo info) {
        if (activity == null || activity.isFinishing()) return;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_update);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView updateVersionInfo = dialog.findViewById(R.id.updateVersionInfo);
        TextView updateChangelog = dialog.findViewById(R.id.updateChangelog);
        View updateProgressSection = dialog.findViewById(R.id.updateProgressSection);
        ProgressBar updateProgressBar = dialog.findViewById(R.id.updateProgressBar);
        TextView updateProgressText = dialog.findViewById(R.id.updateProgressText);
        View updateButtonSection = dialog.findViewById(R.id.updateButtonSection);
        TextView btnUpdateLater = dialog.findViewById(R.id.btnUpdateLater);
        TextView btnUpdateNow = dialog.findViewById(R.id.btnUpdateNow);

        updateVersionInfo.setText(activity.getString(R.string.update_version_format,
                getCurrentVersionName(activity), info.tagName));

        String changelog = info.changelog != null && !info.changelog.trim().isEmpty()
                ? info.changelog.trim()
                : "· 桌面性能调优与细节体验升级\n· 界面显示与设备兼容性优化";
        updateChangelog.setText(changelog);

        btnUpdateLater.setOnClickListener(v -> dialog.dismiss());

        btnUpdateNow.setOnClickListener(v -> {
            if (info.downloadUrl == null || info.downloadUrl.isEmpty()) {
                Toast.makeText(activity, "下载地址获取失败", Toast.LENGTH_SHORT).show();
                return;
            }

            updateButtonSection.setVisibility(View.GONE);
            updateProgressSection.setVisibility(View.VISIBLE);
            updateProgressBar.setProgress(0);
            updateProgressText.setText(R.string.downloading_update);
            dialog.setCancelable(false);

            downloadApk(activity, info.downloadUrl, new DownloadCallback() {
                @Override
                public void onProgress(int progress) {
                    MAIN_HANDLER.post(() -> {
                        updateProgressBar.setProgress(progress);
                        updateProgressText.setText(activity.getString(R.string.download_progress_format, progress));
                    });
                }

                @Override
                public void onSuccess(File file) {
                    MAIN_HANDLER.post(() -> {
                        updateProgressText.setText(R.string.download_success_installing);
                        dialog.dismiss();
                        installApk(activity, file);
                    });
                }

                @Override
                public void onError(String error) {
                    MAIN_HANDLER.post(() -> {
                        updateProgressText.setText(activity.getString(R.string.download_failed, error));
                        updateButtonSection.setVisibility(View.VISIBLE);
                        dialog.setCancelable(true);
                    });
                }
            });
        });

        dialog.show();
        btnUpdateNow.requestFocus();
    }

    /**
     * 自动跟踪 301/302 重定向并下载 APK
     */
    public static void downloadApk(Context context, String downloadUrl, DownloadCallback callback) {
        new Thread(() -> {
            InputStream in = null;
            FileOutputStream out = null;
            try {
                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                setupTrustAllSsl(conn);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + "; " + Build.MODEL + ") PureTV-Launcher");
                conn.setRequestProperty("Accept-Encoding", "identity");
                conn.setConnectTimeout(25000);
                conn.setReadTimeout(35000);
                conn.setInstanceFollowRedirects(true);

                int status = conn.getResponseCode();
                // 手动处理 301/302/307/308 跳转
                while (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM
                        || status == 307 || status == 308) {
                    String redirectUrl = conn.getHeaderField("Location");
                    conn.disconnect();
                    url = new URL(redirectUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    setupTrustAllSsl(conn);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + "; " + Build.MODEL + ") PureTV-Launcher");
                    conn.setRequestProperty("Accept-Encoding", "identity");
                    conn.setConnectTimeout(25000);
                    conn.setReadTimeout(35000);
                    status = conn.getResponseCode();
                }

                if (status != HttpURLConnection.HTTP_OK) {
                    callback.onError("HTTP " + status);
                    return;
                }

                int totalLength = conn.getContentLength();
                File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) dir = context.getCacheDir();
                if (dir != null && !dir.exists()) {
                    dir.mkdirs();
                }
                File apkFile = new File(dir, "update.apk");
                if (apkFile.exists()) apkFile.delete();

                in = conn.getInputStream();
                out = new FileOutputStream(apkFile);
                byte[] buffer = new byte[8192];
                int read;
                long downloaded = 0;
                long lastReport = 0;

                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    downloaded += read;
                    if (totalLength > 0) {
                        int progress = (int) (downloaded * 100 / totalLength);
                        long now = System.currentTimeMillis();
                        if (now - lastReport > 80 || progress == 100) {
                            lastReport = now;
                            callback.onProgress(progress);
                        }
                    }
                }
                out.flush();
                try {
                    apkFile.setReadable(true, false);
                } catch (Exception ignored) {}
                callback.onSuccess(apkFile);

            } catch (Exception e) {
                android.util.Log.e("UpdateManager", "download failed", e);
                callback.onError(e.getMessage() != null ? e.getMessage() : "网络异常");
            } finally {
                try { if (in != null) in.close(); } catch (Exception ignored) {}
                try { if (out != null) out.close(); } catch (Exception ignored) {}
            }
        }).start();
    }

    /**
     * 调起系统原生安装器进行无缝更新（兼容 Android 6.0 ~ Android 14+）
     */
    public static void installApk(Context context, File apkFile) {
        if (apkFile == null || !apkFile.exists()) return;
        try {
            try {
                apkFile.setReadable(true, false);
            } catch (Exception ignored) {}

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Uri apkUri = FileProvider.getUriForFile(context,
                        context.getPackageName() + ".fileprovider", apkFile);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            } else {
                intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
            }

            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "无法拉起安装器: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
