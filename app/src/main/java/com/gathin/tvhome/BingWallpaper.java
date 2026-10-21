package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 必应（Bing）每日高清壁纸拉取与本地缓存模块
 * 1. 采用 1080P 高清原图直链（约 300KB），秒级下载，点对点细腻清晰
 * 2. 具备智能多级降级：优先展示本地已有必应历史壁纸，后台下载今天最新壁纸无缝热更新
 * 3. 自动清理过期旧缓存，节约电视存储
 */
final class BingWallpaper {
    private static final String TAG = "BingWallpaper";
    private static final String BING_API = "https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1";

    interface Callback {
        void onReady(File file);
    }

    private BingWallpaper() { }

    /**
     * 获取本地已有的最新必应壁纸（包含今天或昨天的），若有则立即返回供首帧毫秒级直显
     */
    static File getCachedWallpaper(Context context) {
        try {
            File cacheDir = new File(context.getFilesDir(), "bing_wallpapers");
            if (!cacheDir.exists()) return null;
            String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
            File todayFile = new File(cacheDir, "bing_" + today + ".jpg");
            if (todayFile.exists() && todayFile.length() > 30000) {
                return todayFile;
            }
            return findLatestCachedWallpaper(cacheDir);
        } catch (Exception e) {
            return null;
        }
    }

    private static File findLatestCachedWallpaper(File cacheDir) {
        if (!cacheDir.exists()) return null;
        File[] files = cacheDir.listFiles();
        if (files == null || files.length == 0) return null;
        File newest = null;
        for (File f : files) {
            if (f.isFile() && f.getName().startsWith("bing_") && f.getName().endsWith(".jpg") && f.length() > 30000) {
                if (newest == null || f.lastModified() > newest.lastModified()) {
                    newest = f;
                }
            }
        }
        return newest;
    }

    static void fetch(Context context, Callback callback) {
        new Thread(() -> {
            try {
                String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
                File cacheDir = new File(context.getFilesDir(), "bing_wallpapers");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                File targetFile = new File(cacheDir, "bing_" + today + ".jpg");

                // 1. 如果今天已经下载过且文件大小正常，直接回调今天壁纸
                if (targetFile.exists() && targetFile.length() > 30000) {
                    if (callback != null) {
                        new Handler(Looper.getMainLooper()).post(() -> callback.onReady(targetFile));
                    }
                    return;
                }

                // 2. 如果今天还没下完，但有历史缓存（如昨天的壁纸），先立即呈现，绝不让用户对着自带城堡图干等！
                File latestFallback = findLatestCachedWallpaper(cacheDir);
                if (latestFallback != null && callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onReady(latestFallback));
                }

                // 3. 异步拉取微软必应每日壁纸元数据
                URL url = new URL(BING_API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                if (conn.getResponseCode() != 200) {
                    conn.disconnect();
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();

                JSONObject json = new JSONObject(sb.toString());
                JSONArray images = json.getJSONArray("images");
                if (images.length() == 0) return;

                JSONObject imgObj = images.getJSONObject(0);
                String urlBase = imgObj.optString("urlbase", null);
                String primaryUrl = null;
                if (urlBase != null && !urlBase.isEmpty()) {
                    // 直接请求 1080P 高清原图（约 300KB，0.3 秒下载）
                    primaryUrl = (urlBase.startsWith("http") ? urlBase : "https://cn.bing.com" + urlBase) + "_1920x1080.jpg";
                }
                String fallbackUrlPart = imgObj.optString("url", null);
                String fallbackUrl = null;
                if (fallbackUrlPart != null && !fallbackUrlPart.isEmpty()) {
                    fallbackUrl = fallbackUrlPart.startsWith("http") ? fallbackUrlPart : "https://cn.bing.com" + fallbackUrlPart;
                }

                // 4. 下载 1080P 高清壁纸图片并重试
                File tempFile = new File(cacheDir, "downloading.tmp");
                boolean success = false;
                if (primaryUrl != null) {
                    success = downloadToFile(primaryUrl, tempFile);
                }
                if (!success && fallbackUrl != null) {
                    success = downloadToFile(fallbackUrl, tempFile);
                }
                if (!success && primaryUrl != null) {
                    // 重试一次
                    success = downloadToFile(primaryUrl, tempFile);
                }

                if (success && tempFile.length() > 30000) {
                    if (targetFile.exists()) targetFile.delete();
                    tempFile.renameTo(targetFile);

                    // 清理旧缓存（只保留今天的文件，节约空间）
                    File[] oldFiles = cacheDir.listFiles();
                    if (oldFiles != null) {
                        for (File f : oldFiles) {
                            if (!f.getName().equals(targetFile.getName())) {
                                f.delete();
                            }
                        }
                    }

                    if (callback != null) {
                        new Handler(Looper.getMainLooper()).post(() -> callback.onReady(targetFile));
                    }
                    Log.i(TAG, "今日必应 1080P 壁纸拉取成功: " + targetFile.getName());
                } else {
                    if (tempFile.exists()) tempFile.delete();
                }
            } catch (Exception e) {
                Log.w(TAG, "拉取必应壁纸失败: " + e.getMessage());
            }
        }).start();
    }

    private static boolean downloadToFile(String fileUrl, File target) {
        HttpURLConnection conn = null;
        InputStream is = null;
        FileOutputStream fos = null;
        try {
            URL url = new URL(fileUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            if (conn.getResponseCode() != 200) {
                return false;
            }
            is = conn.getInputStream();
            fos = new FileOutputStream(target);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.flush();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "下载壁纸失败: " + fileUrl + " -> " + e.getMessage());
            return false;
        } finally {
            try { if (fos != null) fos.close(); } catch (Exception ignored) {}
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }
}
