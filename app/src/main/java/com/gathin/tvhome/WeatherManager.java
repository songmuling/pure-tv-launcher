package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;

/**
 * 零权限、零成本天气管理模块
 * 1. 通过公共 IP 定位当前城市（无需 GPS 权限）
 * 2. 通过 Open-Meteo 免费气象接口拉取天气状态
 * 3. 30分钟智能节流与本地持久化缓存，离线时优雅降级为本地时钟天色
 */
public final class WeatherManager {
    private static final String TAG = "WeatherManager";
    private static final String PREF_NAME = "weather_info";
    private static final long CACHE_VALID_MS = 30 * 60 * 1000L; // 30 分钟缓存

    public enum WeatherCondition {
        CLEAR_DAY,
        CLEAR_SUNSET,
        CLEAR_NIGHT,
        CLOUDY,
        RAIN,
        SNOW,
        FOG
    }

    public static class WeatherInfo {
        public final String city;
        public final int temperature;
        public final WeatherCondition condition;
        public final String description;
        public final boolean isDay;

        public WeatherInfo(String city, int temperature, WeatherCondition condition, String description, boolean isDay) {
            this.city = city;
            this.temperature = temperature;
            this.condition = condition;
            this.description = description;
            this.isDay = isDay;
        }

        public String getSummary() {
            if (city == null || city.isEmpty()) {
                return temperature + "°C " + description;
            }
            return temperature + "°C " + description + " · " + city;
        }
    }

    public interface Callback {
        void onWeatherUpdated(WeatherInfo info);
    }

    private static volatile WeatherInfo cachedInfo;
    private static boolean isFetching = false;

    private WeatherManager() {}

    /** 获取当前最新可用天气（优先从内存/本地缓存，没有则由本地时钟推导） */
    public static WeatherInfo getLatest(Context context) {
        if (cachedInfo != null) return cachedInfo;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String city = sp.getString("city", "");
        int temp = sp.getInt("temp", 20);
        String condStr = sp.getString("condition", "");
        String desc = sp.getString("desc", "");
        boolean isDay = sp.getBoolean("is_day", isDaytime());

        if (!condStr.isEmpty()) {
            try {
                WeatherCondition cond = WeatherCondition.valueOf(condStr);
                cachedInfo = new WeatherInfo(city, temp, cond, desc, isDay);
                return cachedInfo;
            } catch (Exception ignored) {}
        }

        // 默认离线时钟推导
        WeatherCondition cond = deduceFromClock();
        String defaultDesc = conditionToDesc(cond);
        cachedInfo = new WeatherInfo(city, temp, cond, defaultDesc, isDaytime());
        return cachedInfo;
    }

    /** 异步拉取天气，并在主线程回调 */
    public static void fetch(Context context, Callback callback) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long lastTime = sp.getLong("last_update", 0);
        long now = System.currentTimeMillis();

        if (now - lastTime < CACHE_VALID_MS && cachedInfo != null) {
            if (callback != null) callback.onWeatherUpdated(cachedInfo);
            return;
        }

        if (isFetching) {
            if (callback != null && cachedInfo != null) callback.onWeatherUpdated(cachedInfo);
            return;
        }

        isFetching = true;
        new Thread(() -> {
            WeatherInfo result = null;
            try {
                // 1. IP 地理位置检测 (http://ip-api.com/json/?fields=status,city,lat,lon)
                String city = sp.getString("city", "");
                double lat = 31.23, lon = 121.47; // 默认备用经纬度 (上海)
                boolean gotLoc = false;

                try {
                    URL geoUrl = new URL("http://ip-api.com/json/?fields=status,city,lat,lon");
                    HttpURLConnection geoConn = (HttpURLConnection) geoUrl.openConnection();
                    geoConn.setConnectTimeout(4000);
                    geoConn.setReadTimeout(4000);
                    if (geoConn.getResponseCode() == 200) {
                        BufferedReader r = new BufferedReader(new InputStreamReader(geoConn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = r.readLine()) != null) sb.append(line);
                        r.close();
                        JSONObject geoJson = new JSONObject(sb.toString());
                        if ("success".equalsIgnoreCase(geoJson.optString("status"))) {
                            city = geoJson.optString("city", city);
                            lat = geoJson.optDouble("lat", lat);
                            lon = geoJson.optDouble("lon", lon);
                            gotLoc = true;
                        }
                    }
                    geoConn.disconnect();
                } catch (Exception e) {
                    Log.w(TAG, "IP 定位请求失败: " + e.getMessage());
                }

                // 2. Open-Meteo 实时气象查询
                URL weatherUrl = new URL("https://api.open-meteo.com/v1/forecast?latitude="
                        + lat + "&longitude=" + lon + "&current_weather=true");
                HttpURLConnection wConn = (HttpURLConnection) weatherUrl.openConnection();
                wConn.setConnectTimeout(5000);
                wConn.setReadTimeout(5000);
                if (wConn.getResponseCode() == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(wConn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();

                    JSONObject wJson = new JSONObject(sb.toString());
                    JSONObject cur = wJson.getJSONObject("current_weather");
                    double tempD = cur.optDouble("temperature", 20.0);
                    int temp = (int) Math.round(tempD);
                    int code = cur.optInt("weathercode", 0);
                    int isDayInt = cur.optInt("is_day", isDaytime() ? 1 : 0);
                    boolean isDay = isDayInt == 1;

                    WeatherCondition condition = mapWeatherCode(code, isDay);
                    String desc = codeToDescription(code, isDay);

                    result = new WeatherInfo(city, temp, condition, desc, isDay);

                    // 写入持久化缓存
                    sp.edit()
                            .putString("city", city)
                            .putInt("temp", temp)
                            .putString("condition", condition.name())
                            .putString("desc", desc)
                            .putBoolean("is_day", isDay)
                            .putLong("last_update", System.currentTimeMillis())
                            .apply();
                }
                wConn.disconnect();
            } catch (Exception e) {
                Log.w(TAG, "天气数据获取异常: " + e.getMessage());
            } finally {
                isFetching = false;
            }

            if (result == null) {
                result = getLatest(context);
            }
            cachedInfo = result;

            final WeatherInfo finalInfo = result;
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onWeatherUpdated(finalInfo));
            }
        }).start();
    }

    private static boolean isDaytime() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour >= 6 && hour < 19;
    }

    private static WeatherCondition deduceFromClock() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 17 && hour < 19) return WeatherCondition.CLEAR_SUNSET;
        if (hour >= 6 && hour < 17) return WeatherCondition.CLEAR_DAY;
        return WeatherCondition.CLEAR_NIGHT;
    }

    private static WeatherCondition mapWeatherCode(int code, boolean isDay) {
        if (code == 0) {
            if (!isDay) return WeatherCondition.CLEAR_NIGHT;
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (hour >= 17 && hour < 19) return WeatherCondition.CLEAR_SUNSET;
            return WeatherCondition.CLEAR_DAY;
        }
        if (code >= 1 && code <= 3) return WeatherCondition.CLOUDY;
        if (code == 45 || code == 48) return WeatherCondition.FOG;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82) || (code >= 95 && code <= 99)) {
            return WeatherCondition.RAIN;
        }
        if ((code >= 71 && code <= 77) || (code >= 85 && code <= 86)) {
            return WeatherCondition.SNOW;
        }
        return isDay ? WeatherCondition.CLEAR_DAY : WeatherCondition.CLEAR_NIGHT;
    }

    private static String codeToDescription(int code, boolean isDay) {
        if (code == 0) return isDay ? "晴" : "晴朗";
        if (code == 1) return "少云";
        if (code == 2) return "多云";
        if (code == 3) return "阴天";
        if (code == 45 || code == 48) return "雾";
        if (code >= 51 && code <= 55) return "细雨";
        if (code >= 61 && code <= 65) return "小雨";
        if (code >= 66 && code <= 67) return "冻雨";
        if (code >= 71 && code <= 77) return "降雪";
        if (code >= 80 && code <= 82) return "阵雨";
        if (code >= 85 && code <= 86) return "阵雪";
        if (code >= 95) return "雷阵雨";
        return isDay ? "晴" : "夜间晴朗";
    }

    private static String conditionToDesc(WeatherCondition cond) {
        switch (cond) {
            case CLEAR_DAY: return "晴";
            case CLEAR_SUNSET: return "夕阳";
            case CLEAR_NIGHT: return "晴朗";
            case CLOUDY: return "多云";
            case RAIN: return "降雨";
            case SNOW: return "降雪";
            case FOG: return "雾";
            default: return "晴";
        }
    }

    /** 获取对应天气的高清内置壁纸资源 ID */
    public static int getWallpaperResId(WeatherCondition condition) {
        if (condition == null) return R.drawable.bg_weather_sunny;
        switch (condition) {
            case CLEAR_DAY:
                return R.drawable.bg_weather_sunny;
            case CLEAR_SUNSET:
                return R.drawable.bg_weather_sunset;
            case CLEAR_NIGHT:
                return R.drawable.bg_weather_night;
            case CLOUDY:
            case FOG:
                return R.drawable.bg_weather_cloudy;
            case RAIN:
                return R.drawable.bg_weather_rain;
            case SNOW:
                return R.drawable.bg_weather_snow;
            default:
                return R.drawable.bg_weather_sunny;
        }
    }
}
