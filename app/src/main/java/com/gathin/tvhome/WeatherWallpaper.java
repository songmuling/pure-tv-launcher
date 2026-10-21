package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Random;

/**
 * 微软 Bing 官方 1080P 高清天气风光壁纸池
 * 1. 采用微软 cn.bing.com 国内官方 CDN 永久直链（1920×1080 原图点对点）
 * 2. 按天气状态精准匹配不同 Bing 经典摄影作品，每天自动轮换新鲜感
 * 3. 本地磁盘自动持久化缓存，开机秒显、零流量消耗
 */
public final class WeatherWallpaper {
    private static final String TAG = "WeatherWallpaper";

    // 微软官方 CDN 永久 1080P 高清风光图池（体积小、加载快、点对点清晰）
    private static final String[] SUNNY_POOL = {
            "https://cn.bing.com/th?id=OHR.PlitviceAutumn_ZH-CN5646237897_1920x1080.jpg", // 湖山艳阳
            "https://cn.bing.com/th?id=OHR.BabcockSP_ZH-CN5266848536_1920x1080.jpg",     // 阳光溪谷
            "https://cn.bing.com/th?id=OHR.SneffelsAutumn_ZH-CN3002661429_1920x1080.jpg"  // 金色晴空雪峰
    };

    private static final String[] SUNSET_POOL = {
            "https://cn.bing.com/th?id=OHR.VietnamBridge_ZH-CN6134321119_1920x1080.jpg",  // 晚霞江桥
            "https://cn.bing.com/th?id=OHR.DolbadarnWales_ZH-CN3649667806_1920x1080.jpg"  // 古堡落日暮云
    };

    private static final String[] NIGHT_POOL = {
            "https://cn.bing.com/th?id=OHR.IlesSanguinaires_ZH-CN3149346035_1920x1080.jpg",// 暮夜孤岛星空
            "https://cn.bing.com/th?id=OHR.DolbadarnWales_ZH-CN3649667806_1920x1080.jpg"  // 寂静暗夜暮色
    };

    private static final String[] CLOUDY_POOL = {
            "https://cn.bing.com/th?id=OHR.ChauvetPaintingsFrance_ZH-CN3150703955_1920x1080.jpg", // 苍茫多云峡谷
            "https://cn.bing.com/th?id=OHR.ForestofDean_ZH-CN2654753621_1920x1080.jpg"          // 晨雾多云密林
    };

    private static final String[] RAIN_POOL = {
            "https://cn.bing.com/th?id=OHR.ForestofDean_ZH-CN2654753621_1920x1080.jpg",  // 幽绿雨雾林海
            "https://cn.bing.com/th?id=OHR.BabcockSP_ZH-CN5266848536_1920x1080.jpg"      // 雨后清泉流水
    };

    private static final String[] SNOW_POOL = {
            "https://cn.bing.com/th?id=OHR.SneffelsAutumn_ZH-CN3002661429_1920x1080.jpg"  // 冰雪高山圣洁白雪
    };

    public interface Callback {
        void onReady(File file);
    }

    private WeatherWallpaper() {}

    /**
     * 获取本地已有的最新天气壁纸缓存文件（如果有），用于毫秒级首帧直接展示
     */
    public static File getCachedWallpaper(Context context) {
        try {
            File cacheDir = new File(context.getFilesDir(), "weather_wallpapers");
            if (!cacheDir.exists()) return null;
            File[] files = cacheDir.listFiles();
            if (files == null || files.length == 0) return null;
            File newest = null;
            for (File f : files) {
                if (f.isFile() && f.getName().startsWith("bing_w_") && f.length() > 30000) {
                    if (newest == null || f.lastModified() > newest.lastModified()) {
                        newest = f;
                    }
                }
            }
            return newest;
        } catch (Exception e) {
            return null;
        }
    }

    public static void fetch(Context context, WeatherManager.WeatherCondition condition, Callback callback) {
        if (condition == null) condition = WeatherManager.WeatherCondition.CLEAR_DAY;
        final WeatherManager.WeatherCondition finalCondition = condition;

        new Thread(() -> {
            try {
                File cacheDir = new File(context.getFilesDir(), "weather_wallpapers");
                if (!cacheDir.exists()) cacheDir.mkdirs();

                // 每天不同日期轮换一张同天气的壁纸
                int dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
                String[] pool = getPool(finalCondition);
                String selectedUrl = pool[Math.abs((dayOfYear + finalCondition.ordinal()) % pool.length)];

                // 本地文件名以 URL 的 hash 为准
                String fileName = "bing_w_" + finalCondition.name() + "_" + Math.abs(selectedUrl.hashCode()) + ".jpg";
                File targetFile = new File(cacheDir, fileName);

                // 命中本地缓存且大小大于 30KB 则直接使用
                if (targetFile.exists() && targetFile.length() > 30000) {
                    if (callback != null) {
                        new Handler(Looper.getMainLooper()).post(() -> callback.onReady(targetFile));
                    }
                    return;
                }

                // 拉取 1080P 高清原图（通常仅 300KB 左右，秒级下载）
                File tempFile = new File(cacheDir, "download_weather.tmp");
                boolean downloaded = downloadUrl(selectedUrl, tempFile);

                if (downloaded && tempFile.length() > 30000) {
                    if (targetFile.exists()) targetFile.delete();
                    tempFile.renameTo(targetFile);
                    if (callback != null) {
                        new Handler(Looper.getMainLooper()).post(() -> callback.onReady(targetFile));
                    }
                } else {
                    if (tempFile.exists()) tempFile.delete();
                }
            } catch (Exception e) {
                Log.w(TAG, "拉取 Bing 天气壁纸异常: " + e.getMessage());
            }
        }).start();
    }

    private static boolean downloadUrl(String fileUrl, File target) {
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

    private static String[] getPool(WeatherManager.WeatherCondition condition) {
        switch (condition) {
            case CLEAR_DAY: return SUNNY_POOL;
            case CLEAR_SUNSET: return SUNSET_POOL;
            case CLEAR_NIGHT: return NIGHT_POOL;
            case CLOUDY:
            case FOG: return CLOUDY_POOL;
            case RAIN: return RAIN_POOL;
            case SNOW: return SNOW_POOL;
            default: return SUNNY_POOL;
        }
    }
}
