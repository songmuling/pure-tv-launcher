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
 */
final class BingWallpaper {
    private static final String TAG = "BingWallpaper";
    private static final String BING_API = "https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1";

    interface Callback {
        void onReady(File file);
    }

    private BingWallpaper() { }

    static void fetch(Context context, Callback callback) {
        new Thread(() -> {
            try {
                String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
                File cacheDir = new File(context.getFilesDir(), "bing_wallpapers");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                File targetFile = new File(cacheDir, "bing_" + today + ".jpg");

                // 如果今天已经下载过且文件大小正常，直接回调
                if (targetFile.exists() && targetFile.length() > 30000) {
                    if (callback != null) {
                        new Handler(Looper.getMainLooper()).post(() -> callback.onReady(targetFile));
                    }
                    return;
                }

                // 1. 获取必应每日壁纸元数据
                URL url = new URL(BING_API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
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
                    primaryUrl = (urlBase.startsWith("http") ? urlBase : "https://cn.bing.com" + urlBase) + "_UHD.jpg";
                }
                String fallbackUrlPart = imgObj.getString("url");
                String fallbackUrl = fallbackUrlPart.startsWith("http") ? fallbackUrlPart : "https://cn.bing.com" + fallbackUrlPart;

                // 2. 下载高清壁纸图片（优先 4K 原图，降级 1080P）
                File tempFile = new File(cacheDir, "downloading.tmp");
                boolean success = false;
                if (primaryUrl != null) {
                    success = downloadToFile(primaryUrl, tempFile);
                }
                if (!success) {
                    success = downloadToFile(fallbackUrl, tempFile);
                }

                if (success && tempFile.length() > 30000) {
                    if (targetFile.exists()) targetFile.delete();
                    tempFile.renameTo(targetFile);

                    // 清理旧缓存（只保留今天的文件）
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
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
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
