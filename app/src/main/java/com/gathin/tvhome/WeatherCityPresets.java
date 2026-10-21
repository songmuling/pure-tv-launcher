package com.gathin.tvhome;

/**
 * 常用国内主要城市气象经纬度预设
 * 避免因软路由翻墙/全局 VPN 导致的 IP 定位漂移至国外
 */
public final class WeatherCityPresets {

    public static final class City {
        public final String name;
        public final double lat;
        public final double lon;

        public City(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    public static final City[] PRESET_CITIES = {
            new City("北京", 39.9042, 116.4074),
            new City("上海", 31.2304, 121.4737),
            new City("广州", 23.1291, 113.2644),
            new City("深圳", 22.5431, 114.0579),
            new City("杭州", 30.2741, 120.1551),
            new City("成都", 30.5728, 104.0668),
            new City("重庆", 29.5630, 106.5516),
            new City("武汉", 30.5928, 114.3055),
            new City("西安", 34.3416, 108.9398),
            new City("南京", 32.0603, 118.7969),
            new City("长沙", 28.2282, 112.9388),
            new City("郑州", 34.7466, 113.6253),
            new City("天津", 39.0842, 117.2009),
            new City("苏州", 31.2990, 120.5853),
            new City("青岛", 36.0671, 120.3826),
            new City("厦门", 24.4798, 118.0894),
            new City("合肥", 31.8206, 117.2272),
            new City("昆明", 25.0406, 102.7123),
            new City("福州", 26.0745, 119.2965),
            new City("济南", 36.6512, 117.1201),
            new City("大连", 38.9140, 121.6147),
            new City("沈阳", 41.8057, 123.4315),
            new City("长春", 43.8868, 125.3245),
            new City("哈尔滨", 45.8038, 126.5350),
            new City("南昌", 28.6820, 115.8579),
            new City("贵阳", 26.6470, 106.6302),
            new City("南宁", 22.8170, 108.3665),
            new City("海口", 20.0440, 110.3510),
            new City("兰州", 36.0611, 103.8343),
            new City("乌鲁木齐", 43.8256, 87.6168),
            new City("呼和浩特", 40.8426, 111.7492),
            new City("香港", 22.3193, 114.1694),
            new City("台北", 25.0330, 121.5654)
    };

    private WeatherCityPresets() {}
}
