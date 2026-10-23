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
import java.util.Locale;

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
        String customCity = sp.getString("custom_city", "");
        String city = !customCity.isEmpty() ? customCity : sp.getString("city", "");
        if (city.isEmpty()) city = getDefaultCityName();
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
        fetch(context, false, callback);
    }

    /** 异步拉取天气，支持强制即时刷新 */
    public static void fetch(Context context, boolean force, Callback callback) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long lastTime = sp.getLong("last_update", 0);
        long now = System.currentTimeMillis();

        // 智能感知昼夜与天色时段切换（日间 6~16点、夕阳晚霞 17~18点、晴朗夜空 19~5点）
        boolean dayNow = isDaytime();
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean phaseChanged = false;
        if (cachedInfo != null) {
            if (cachedInfo.isDay != dayNow) {
                phaseChanged = true;
            } else if (cachedInfo.condition == WeatherCondition.CLEAR_DAY && hour >= 17) {
                phaseChanged = true;
            } else if (cachedInfo.condition == WeatherCondition.CLEAR_SUNSET && (hour >= 19 || hour < 17)) {
                phaseChanged = true;
            } else if (cachedInfo.condition == WeatherCondition.CLEAR_NIGHT && (hour >= 6 && hour < 19)) {
                phaseChanged = true;
            }
        }

        if (!force && !phaseChanged && (now - lastTime < CACHE_VALID_MS) && cachedInfo != null) {
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
                // 1. 获取城市经纬度（优先使用用户手动设置的城市；否则自动双通道 IP 检测；最后保底默认城市）
                String customCity = sp.getString("custom_city", "");
                String city;
                double lat = 39.905, lon = 116.4053; // 默认备用经纬度 (北京)

                if (!customCity.isEmpty()) {
                    city = customCity;
                    lat = Double.longBitsToDouble(sp.getLong("custom_lat", Double.doubleToLongBits(39.905)));
                    lon = Double.longBitsToDouble(sp.getLong("custom_lon", Double.doubleToLongBits(116.4053)));
                } else {
                    GeoResult geo = detectLocation();
                    if (geo != null) {
                        city = geo.city;
                        lat = geo.lat;
                        lon = geo.lon;
                    } else {
                        city = sp.getString("city", getDefaultCityName());
                        if (city.isEmpty()) city = getDefaultCityName();
                    }
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

    private static class GeoResult {
        final String city;
        final double lat;
        final double lon;

        GeoResult(String city, double lat, double lon) {
            this.city = city;
            this.lat = lat;
            this.lon = lon;
        }
    }

    private static String getDefaultCityName() {
        return isChineseEnv() ? "北京" : "Beijing";
    }

    private static boolean isChineseEnv() {
        Locale l = Locale.getDefault();
        return l != null && ("zh".equalsIgnoreCase(l.getLanguage()) || "CN".equalsIgnoreCase(l.getCountry()));
    }

    private static String normalizeCityName(String rawCity) {
        if (rawCity == null) return getDefaultCityName();
        String trimmed = rawCity.trim();
        if (trimmed.isEmpty()) return getDefaultCityName();

        boolean hasChinese = false;
        for (char c : trimmed.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                hasChinese = true;
                break;
            }
        }
        if (hasChinese) {
            if (trimmed.endsWith("市") && trimmed.length() > 2) {
                return trimmed.substring(0, trimmed.length() - 1);
            }
            return trimmed;
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("beijing")) return "北京";
        if (lower.contains("shanghai")) return "上海";
        if (lower.contains("guangzhou")) return "广州";
        if (lower.contains("shenzhen")) return "深圳";
        if (lower.contains("chengdu")) return "成都";
        if (lower.contains("hangzhou")) return "杭州";
        if (lower.contains("wuhan")) return "武汉";
        if (lower.contains("chongqing")) return "重庆";
        if (lower.contains("nanjing")) return "南京";
        if (lower.contains("xian") || lower.contains("xi'an")) return "西安";
        if (lower.contains("tianjin")) return "天津";
        if (lower.contains("suzhou")) return "苏州";
        if (lower.contains("zhengzhou")) return "郑州";
        if (lower.contains("changsha")) return "长沙";
        if (lower.contains("dongguan")) return "东莞";
        if (lower.contains("shenyang")) return "沈阳";
        if (lower.contains("qingdao")) return "青岛";
        if (lower.contains("hefei")) return "合肥";
        if (lower.contains("foshan")) return "佛山";
        if (lower.contains("fuzhou")) return "福州";
        if (lower.contains("kunming")) return "昆明";
        if (lower.contains("harbin")) return "哈尔滨";
        if (lower.contains("jinan")) return "济南";
        if (lower.contains("wenzhou")) return "温州";
        if (lower.contains("nanning")) return "南宁";
        if (lower.contains("changchun")) return "长春";
        if (lower.contains("quanzhou")) return "泉州";
        if (lower.contains("shijiazhuang")) return "石家庄";
        if (lower.contains("guiyang")) return "贵阳";
        if (lower.contains("nanchang")) return "南昌";
        if (lower.contains("wuxi")) return "无锡";
        if (lower.contains("taiyuan")) return "太原";
        if (lower.contains("dalian")) return "大连";
        if (lower.contains("xiamen")) return "厦门";
        if (lower.contains("zhongshan")) return "中山";
        if (lower.contains("huizhou")) return "惠州";
        if (lower.contains("weifang")) return "潍坊";
        if (lower.contains("lanzhou")) return "兰州";
        if (lower.contains("haikou")) return "海口";
        if (lower.contains("sanya")) return "三亚";
        if (lower.contains("urumqi")) return "乌鲁木齐";
        if (lower.contains("hohhot")) return "呼和浩特";
        if (lower.contains("yinchuan")) return "银川";
        if (lower.contains("xining")) return "西宁";
        if (lower.contains("lhasa")) return "拉萨";
        if (lower.contains("hong kong")) return "香港";
        if (lower.contains("macau") || lower.contains("macao")) return "澳门";
        if (lower.contains("taipei")) return "台北";

        return trimmed;
    }

    private static GeoResult detectLocation() {
        // 通道 1：api.ip.sb/geoip (高可靠 HTTPS，免 Key 全球极速)
        try {
            URL url = new URL("https://api.ip.sb/geoip");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            if (conn.getResponseCode() == 200) {
                BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();
                JSONObject json = new JSONObject(sb.toString());
                String city = json.optString("city", "");
                double lat = json.optDouble("latitude", Double.NaN);
                double lon = json.optDouble("longitude", Double.NaN);
                conn.disconnect();
                if (!city.isEmpty() && !Double.isNaN(lat) && !Double.isNaN(lon)) {
                    return new GeoResult(normalizeCityName(city), lat, lon);
                }
            }
            conn.disconnect();
        } catch (Exception ignored) {}

        // 通道 2：ip-api.com (备选通道)
        try {
            URL url = new URL("http://ip-api.com/json/?fields=status,city,lat,lon");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            if (conn.getResponseCode() == 200) {
                BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();
                JSONObject json = new JSONObject(sb.toString());
                if ("success".equalsIgnoreCase(json.optString("status"))) {
                    String city = json.optString("city", "");
                    double lat = json.optDouble("lat", Double.NaN);
                    double lon = json.optDouble("lon", Double.NaN);
                    conn.disconnect();
                    if (!city.isEmpty() && !Double.isNaN(lat) && !Double.isNaN(lon)) {
                        return new GeoResult(normalizeCityName(city), lat, lon);
                    }
                }
            }
            conn.disconnect();
        } catch (Exception ignored) {}

        return null;
    }

    private static boolean isDaytime() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour >= 6 && hour < 19;
    }

    static WeatherCondition deduceFromClock() {
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

    /** 设置自定义城市（空或 "auto" 则为自动 IP 定位） */
    public static void setCustomCity(Context context, String city, double lat, double lon) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (city == null || city.isEmpty() || "auto".equalsIgnoreCase(city)) {
            sp.edit().remove("custom_city").remove("custom_lat").remove("custom_lon").remove("last_update").apply();
        } else {
            sp.edit().putString("custom_city", city)
                    .putLong("custom_lat", Double.doubleToLongBits(lat))
                    .putLong("custom_lon", Double.doubleToLongBits(lon))
                    .remove("last_update")
                    .apply();
        }
        cachedInfo = null;
    }

    /** 获取当前设定的城市名称（为空则代表自动定位） */
    public static String getCustomCity(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString("custom_city", "");
    }
}
