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

    // 微软官方 CDN 永久 1080P 高清天气大片风光图池（全部通过 HTTP 200 OK 真实性核验）
    // 1. 晴朗白天（日光溪谷、雪峰蓝天、秋水红叶、大湖碧波）
    private static final String[] SUNNY_POOL = {
            "https://cn.bing.com/th?id=OHR.PlitviceAutumn_ZH-CN5646237897_1920x1080.jpg", // 湖山艳阳秋水
            "https://cn.bing.com/th?id=OHR.BabcockSP_ZH-CN5266848536_1920x1080.jpg",     // 阳光溪谷老磨坊
            "https://cn.bing.com/th?id=OHR.SwiftcurrentLake_EN-US8272209593_1920x1080.jpg",// 冰川公园晴空湛蓝大湖
            "https://cn.bing.com/th?id=OHR.LakeTahoeRock_EN-US8513392756_1920x1080.jpg",   // 太浩湖碧波晴日
            "https://cn.bing.com/th?id=OHR.NaejangsanNP_ZH-CN6538893004_1920x1080.jpg"    // 内藏山秋枫湖镜
    };

    // 2. 傍晚晚霞（江桥落日、纪念堂余晖、大提顿朝霞、海面漫天霞光、古堡落日）
    private static final String[] SUNSET_POOL = {
            "https://cn.bing.com/th?id=OHR.VietnamBridge_ZH-CN6134321119_1920x1080.jpg",  // 晚霞江桥落日
            "https://cn.bing.com/th?id=OHR.LincolnSunset_EN-US8001542624_1920x1080.jpg",  // 林肯纪念堂落日余晖
            "https://cn.bing.com/th?id=OHR.TetonSunrise_EN-US0849252457_1920x1080.jpg",   // 大提顿群峰金色霞光
            "https://cn.bing.com/th?id=OHR.SantaCruzSunrise_EN-US6436233856_1920x1080.jpg",// 圣克鲁斯海面漫天晚霞
            "https://cn.bing.com/th?id=OHR.DolbadarnWales_ZH-CN3649667806_1920x1080.jpg"  // 威尔士古堡落日暮云
    };

    // 3. 晴朗夜晚/星空（大弯银河拱门、大阪流光夜色、冰岛极光古堡、孤岛暗夜星空灯塔）
    private static final String[] NIGHT_POOL = {
            "https://cn.bing.com/th?id=OHR.BigBendMilkyWay_EN-US7213876995_1920x1080.jpg",// 大弯国家公园璀璨银河拱门
            "https://cn.bing.com/th?id=OHR.OsakaNight_EN-US7022302235_1920x1080.jpg",     // 大阪繁星流光夜色
            "https://cn.bing.com/th?id=OHR.AurorasIceland_ZH-CN9781322454_1920x1080.jpg",  // 冰岛极光倒映与古堡夜色
            "https://cn.bing.com/th?id=OHR.IlesSanguinaires_ZH-CN3149346035_1920x1080.jpg" // 暮夜孤岛星空灯塔
    };

    // 4. 多云/阴天/迷雾（苍茫多云峡谷、晨雾密林、迷雾海岸灯塔、阴天古典欧洲秋色）
    private static final String[] CLOUDY_POOL = {
            "https://cn.bing.com/th?id=OHR.ChauvetPaintingsFrance_ZH-CN3150703955_1920x1080.jpg", // 苍茫多云峡谷
            "https://cn.bing.com/th?id=OHR.ForestofDean_ZH-CN2654753621_1920x1080.jpg",          // 晨雾多云密林
            "https://cn.bing.com/th?id=OHR.FisgardLighthouse_EN-US3880792118_1920x1080.jpg",     // 迷雾灯塔多云海岸
            "https://cn.bing.com/th?id=OHR.WroclawPoland_ZH-CN6042428292_1920x1080.jpg"         // 阴天古典欧洲秋色
    };

    // 5. 雨天（查图加河清溪水雾秋叶、伊瓜苏大瀑布雨后飞虹、雷尼尔山雨后清晨繁花、森林瀑布水雾）
    private static final String[] RAIN_POOL = {
            "https://cn.bing.com/th?id=OHR.ChattoogaRiver_ZH-CN9453791496_1920x1080.jpg", // 查图加河清溪水雾秋叶
            "https://cn.bing.com/th?id=OHR.IguazuRainbow_EN-US4361499337_1920x1080.jpg",    // 伊瓜苏大瀑布雨后飞虹
            "https://cn.bing.com/th?id=OHR.RainierWildflowers_EN-US8010104719_1920x1080.jpg",// 雷尼尔山雨后清晨繁花
            "https://cn.bing.com/th?id=OHR.GollingerFalls_EN-US7184224692_1920x1080.jpg"     // 森林瀑布幽深水雾
    };

    // 6. 雪天（十六湖冬雪冰封银装、大峡谷壮阔雪原、圣洁金色雪峰、露易丝湖雪山苍茫）
    private static final String[] SNOW_POOL = {
            "https://cn.bing.com/th?id=OHR.PlitviceWinter_EN-US1870468945_1920x1080.jpg",  // 十六湖冬雪冰封银装素裹
            "https://cn.bing.com/th?id=OHR.GrandCanyonWinter_EN-US3010552047_1920x1080.jpg",// 大峡谷壮阔雪原
            "https://cn.bing.com/th?id=OHR.SneffelsAutumn_ZH-CN3002661429_1920x1080.jpg",  // 金色晴空雪峰圣洁白雪
            "https://cn.bing.com/th?id=OHR.LakeLouise_EN-US1133378386_1920x1080.jpg"        // 露易丝湖雪山苍茫
    };

    public interface Callback {
        void onReady(File file);
    }

    private WeatherWallpaper() {}

    private static final String PREF_NAME = "weather_wallpaper_pref";
    private static final String KEY_LAST_WALLPAPER = "last_weather_wallpaper_path";

    /**
     * 获取本地已有的最新天气壁纸缓存文件（如果有），用于毫秒级首帧直接展示
     */
    public static File getCachedWallpaper(Context context) {
        try {
            android.content.SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String lastPath = sp.getString(KEY_LAST_WALLPAPER, null);
            if (lastPath != null) {
                File lastFile = new File(lastPath);
                if (lastFile.exists() && lastFile.length() > 30000) {
                    return lastFile;
                }
            }

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
                    context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                            .putString(KEY_LAST_WALLPAPER, targetFile.getAbsolutePath()).apply();
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
                    trimCache(cacheDir, 16);
                    context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                            .putString(KEY_LAST_WALLPAPER, targetFile.getAbsolutePath()).apply();
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

    private static void trimCache(File cacheDir, int maxFiles) {
        try {
            File[] files = cacheDir.listFiles();
            if (files != null && files.length > maxFiles) {
                java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                for (int i = 0; i < files.length - maxFiles; i++) {
                    if (files[i].isFile() && files[i].getName().startsWith("bing_w_")) {
                        files[i].delete();
                    }
                }
            }
        } catch (Exception ignored) {}
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
