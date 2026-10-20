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

                String imgUrlPart = images.getJSONObject(0).getString("url");
                String fullImgUrl = imgUrlPart.startsWith("http") ? imgUrlPart : "https://cn.bing.com" + imgUrlPart;

                // 2. 下载高清壁纸图片
                URL imgUrl = new URL(fullImgUrl);
                HttpURLConnection imgConn = (HttpURLConnection) imgUrl.openConnection();
                imgConn.setConnectTimeout(10000);
                imgConn.setReadTimeout(15000);
                if (imgConn.getResponseCode() != 200) {
                    imgConn.disconnect();
                    return;
                }

                File tempFile = new File(cacheDir, "downloading.tmp");
                InputStream is = imgConn.getInputStream();
                FileOutputStream fos = new FileOutputStream(tempFile);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
                fos.flush();
                fos.close();
                is.close();
                imgConn.disconnect();

                if (tempFile.length() > 30000) {
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
                    tempFile.delete();
                }
            } catch (Exception e) {
                Log.w(TAG, "拉取必应壁纸失败: " + e.getMessage());
            }
        }).start();
    }
}
