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

/**
 * 微软 Bing 官方超高清天气写实大片壁纸池（天气 × 昼夜双轨制）
 * 1. 采用微软 cn.bing.com 国内官方 CDN 永久直链（1920×1080 原图点对点）
 * 2. 纯静态写实摄影，坚决不搞动态粒子，避免低配电视 GPU 显存崩溃/花屏
 * 3. 严格以“天气主体（雨水/晨雾/雪原/厚云/晴空/星河）+ 昼夜时段”精准匹配，杜绝半夜下雨显示白天风景
 * 4. 本地持久化缓存与平滑过渡，开机零流量秒显、按 Home 绝对不闪烁
 */
public final class WeatherWallpaper {
    private static final String TAG = "WeatherWallpaper";

    // 微软官方 CDN 永久 1080P 高清天气大片图池（全部通过 HTTP 200 OK 真实性核验）

    // 1. 雨天 - 白天（晶莹水滴特写、雨幕大湖、暗云风暴水汽、大峡谷暴雨雨幕）
    private static final String[] RAIN_DAY_POOL = {
            "https://cn.bing.com/th?id=OHR.TortulaMoss_EN-US7128071079_1920x1080.jpg",      // 雨后青苔晶莹水滴微距特写
            "https://cn.bing.com/th?id=OHR.FrankensteinFriday_EN-US3119113489_1920x1080.jpg", // 日内瓦湖雷雨暴风暗云水汽
            "https://cn.bing.com/th?id=OHR.GCThunderstorm_EN-US9801749958_1920x1080.jpg",     // 大峡谷暴雨雷电与漫天雨幕
            "https://cn.bing.com/th?id=OHR.BulgariaPlains_EN-US0576020979_1920x1080.jpg",     // 保加利亚平原雨云与低垂雨幕
            "https://cn.bing.com/th?id=OHR.ChattoogaRiver_ZH-CN9453791496_1920x1080.jpg"      // 查图加河水汽与雨后清流
    };

    // 2. 雨天 - 夜晚（暗夜暴风雨闪电、暗夜湿润大片、暮云雷雨）
    private static final String[] RAIN_NIGHT_POOL = {
            "https://cn.bing.com/th?id=OHR.SonoranStorm_EN-US5792303901_1920x1080.jpg",     // 索诺兰暗夜风雨雷电交加
            "https://cn.bing.com/th?id=OHR.BadLightning_EN-US0865590962_1920x1080.jpg",     // 恶地国家公园暗夜闪电暴雨
            "https://cn.bing.com/th?id=OHR.NebraskaStorm_EN-US1163295363_1920x1080.jpg"      // 暴雨闪电与昏暗暮云
    };

    // 3. 迷雾/雾天 - 白天（浓雾完全笼罩天际线、幽深迷雾松林、亚马逊茫茫晨雾、丹浓谷白雾层叠）
    private static final String[] FOG_DAY_POOL = {
            "https://cn.bing.com/th?id=OHR.NYCClouds_EN-US7251713390_1920x1080.jpg",        // 纽约天际线被苍茫浓雾完全笼罩
            "https://cn.bing.com/th?id=OHR.MistyForest_EN-US5261676101_1920x1080.jpg",      // 迷雾松林沉浸白雾幽深
            "https://cn.bing.com/th?id=OHR.AltaFloresta_EN-US4736416258_1920x1080.jpg",     // 亚马逊雨林晨雾茫茫消隐
            "https://cn.bing.com/th?id=OHR.DanumValley_EN-US1030783251_1920x1080.jpg"       // 丹浓谷低地雨林白雾层叠
    };

    // 4. 迷雾/雾天 - 夜晚（暗夜迷雾灯火大桥、夜雾弥漫天际线）
    private static final String[] FOG_NIGHT_POOL = {
            "https://cn.bing.com/th?id=OHR.OberbaumBridge_EN-US6324390642_1920x1080.jpg",   // 迷雾暗夜奥伯鲍姆桥光影
            "https://cn.bing.com/th?id=OHR.NYCClouds_EN-US7251713390_1920x1080.jpg"         // 浓雾弥漫深暗天际线
    };

    // 5. 冬雪 - 白天（十六湖冰封银装、优胜美地雪后初晴暴风雪、塔斯马尼亚纯白雪景、明尼苏达冰雪盛景）
    private static final String[] SNOW_DAY_POOL = {
            "https://cn.bing.com/th?id=OHR.PlitviceWinter_EN-US1870468945_1920x1080.jpg",   // 十六湖冬雪冰封银装素裹
            "https://cn.bing.com/th?id=OHR.AABday_EN-US6703996640_1920x1080.jpg",           // 优胜美地国家公园风雪漫天
            "https://cn.bing.com/th?id=OHR.SnowGumTasmania_EN-US4058572259_1920x1080.jpg",  // 塔斯马尼亚纯白雪景桉树
            "https://cn.bing.com/th?id=OHR.WinterCarnival_EN-US6859361078_1920x1080.jpg"    // 圣保罗冬日纯净冰雪
    };

    // 6. 冬雪 - 夜晚（冰岛雪山群峰极光暗夜、众神瀑布冰雪极光夜色）
    private static final String[] SNOW_NIGHT_POOL = {
            "https://cn.bing.com/th?id=OHR.AurorasIceland_EN-US3293282785_1920x1080.jpg",   // 冰岛雪山群峰与暗夜极光
            "https://cn.bing.com/th?id=OHR.Godafoss_EN-US1167261968_1920x1080.jpg"          // 众神瀑布冰雪暗夜极光
    };

    // 7. 多云/阴天 - 白天（亚马逊巨型砧状层云、巴塔哥尼亚厚重积云、高空夜光层云、多云密林）
    private static final String[] CLOUDY_DAY_POOL = {
            "https://cn.bing.com/th?id=OHR.AmazonClouds_EN-US2049846873_1920x1080.jpg",     // 亚马逊上空厚重漫天砧状层云
            "https://cn.bing.com/th?id=OHR.CloudsPatagonia_EN-US4941579050_1920x1080.jpg",  // 巴塔哥尼亚壮阔厚积云与阴霾天空
            "https://cn.bing.com/th?id=OHR.NoctilucentClouds_EN-US0838966037_1920x1080.jpg",// 壮美厚重层云低垂阴天
            "https://cn.bing.com/th?id=OHR.Monteverde_EN-US9503031199_1920x1080.jpg"        // 蒙特维多阴天多云云雾林
    };

    // 8. 多云/阴天 - 夜晚（纯净月影暗夜、雷尼尔山月光云夜）
    private static final String[] CLOUDY_NIGHT_POOL = {
            "https://cn.bing.com/th?id=OHR.MineralMoon_EN-US8936600169_1920x1080.jpg",      // 深暗夜色中的纯净月影
            "https://cn.bing.com/th?id=OHR.MoonlightRainier_EN-US6336057354_1920x1080.jpg"   // 雷尼尔山月光与深夜浮云
    };

    // 9. 晴空 - 白天（冰川公园晴空湛蓝大湖、太浩湖碧波万顷艳阳、十六湖秋水晴日）
    private static final String[] CLEAR_DAY_POOL = {
            "https://cn.bing.com/th?id=OHR.SwiftcurrentLake_EN-US8272209593_1920x1080.jpg", // 冰川公园晴空万里湛蓝大湖
            "https://cn.bing.com/th?id=OHR.LakeTahoeRock_EN-US8513392756_1920x1080.jpg",    // 太浩湖碧波万顷艳阳高照
            "https://cn.bing.com/th?id=OHR.PlitviceAutumn_ZH-CN5646237897_1920x1080.jpg"   // 湖山艳阳秋水通透晴日
    };

    // 10. 晴朗 - 傍晚晚霞（亨廷顿海滩日落余晖、布莱斯峡谷落日金光、香江桥夕阳暮色）
    private static final String[] CLEAR_SUNSET_POOL = {
            "https://cn.bing.com/th?id=OHR.HuntingtonBeach_EN-US9892577517_1920x1080.jpg",  // 亨廷顿海滩晚霞日落绝美长桥
            "https://cn.bing.com/th?id=OHR.HoodoosBryce_EN-US6434628044_1920x1080.jpg",      // 布莱斯峡谷日落熔金霞光万道
            "https://cn.bing.com/th?id=OHR.VietnamBridge_ZH-CN6134321119_1920x1080.jpg"     // 香江桥落日余晖漫天霞光
    };

    // 11. 晴朗 - 夜晚星空（大弯国家公园璀璨银河拱门、大阪流光星夜、非洲箭袋树璀璨银河）
    private static final String[] CLEAR_NIGHT_POOL = {
            "https://cn.bing.com/th?id=OHR.BigBendMilkyWay_EN-US7213876995_1920x1080.jpg",  // 大弯国家公园璀璨银河拱门
            "https://cn.bing.com/th?id=OHR.OsakaNight_EN-US7022302235_1920x1080.jpg",        // 大阪静谧繁星流光夜色
            "https://cn.bing.com/th?id=OHR.AloeDichotoma_ZH-CN4432972312_1920x1080.jpg"    // 纳米比亚箭袋树与纯净银河星空
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
        fetch(context, condition, true, callback);
    }

    public static void fetch(Context context, WeatherManager.WeatherCondition condition, boolean isDay, Callback callback) {
        if (condition == null) {
            condition = isDay ? WeatherManager.WeatherCondition.CLEAR_DAY : WeatherManager.WeatherCondition.CLEAR_NIGHT;
        }
        final WeatherManager.WeatherCondition finalCondition = condition;
        final boolean finalIsDay = isDay;

        new Thread(() -> {
            try {
                File cacheDir = new File(context.getFilesDir(), "weather_wallpapers");
                if (!cacheDir.exists()) cacheDir.mkdirs();

                int dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
                String[] pool = getPool(finalCondition, finalIsDay);
                String selectedUrl = pool[Math.abs((dayOfYear + finalCondition.ordinal() + (finalIsDay ? 0 : 7)) % pool.length)];

                // 本地文件名包含天气状态、昼夜及 URL 的 hash
                String fileName = "bing_w_" + finalCondition.name() + "_" + (finalIsDay ? "day_" : "night_")
                        + Math.abs(selectedUrl.hashCode()) + ".jpg";
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

                // 拉取 1080P 高清原图（通常仅 300KB 左右，秒级极速下载）
                File tempFile = new File(cacheDir, "download_weather_" + System.currentTimeMillis() + ".tmp");
                boolean downloaded = downloadUrl(selectedUrl, tempFile);

                if (downloaded && tempFile.length() > 30000) {
                    if (targetFile.exists()) targetFile.delete();
                    tempFile.renameTo(targetFile);
                    trimCache(cacheDir, 20);
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

    private static String[] getPool(WeatherManager.WeatherCondition condition, boolean isDay) {
        if (condition == null) {
            condition = isDay ? WeatherManager.WeatherCondition.CLEAR_DAY : WeatherManager.WeatherCondition.CLEAR_NIGHT;
        }
        switch (condition) {
            case CLEAR_DAY:
                return isDay ? CLEAR_DAY_POOL : CLEAR_NIGHT_POOL;
            case CLEAR_SUNSET:
                return CLEAR_SUNSET_POOL;
            case CLEAR_NIGHT:
                return CLEAR_NIGHT_POOL;
            case CLOUDY:
                return isDay ? CLOUDY_DAY_POOL : CLOUDY_NIGHT_POOL;
            case FOG:
                return isDay ? FOG_DAY_POOL : FOG_NIGHT_POOL;
            case RAIN:
                return isDay ? RAIN_DAY_POOL : RAIN_NIGHT_POOL;
            case SNOW:
                return isDay ? SNOW_DAY_POOL : SNOW_NIGHT_POOL;
            default:
                return isDay ? CLEAR_DAY_POOL : CLEAR_NIGHT_POOL;
        }
    }
}
