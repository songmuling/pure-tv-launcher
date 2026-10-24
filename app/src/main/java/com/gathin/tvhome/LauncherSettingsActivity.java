package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    public static final String MODE_WEATHER = "weather";
    public static final String MODE_BING = "bing";
    public static final String MODE_OFF = "off";

    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView wallpaperModeWeather;
    private TextView wallpaperModeBing;
    private TextView wallpaperModeOff;
    private TextView weatherCityChoice;
    private TextView followThemeChoice;
    private TextView checkUpdateChoice;
    private TextView checkUpdateStatus;
    private int columns;
    private boolean animations;
    private boolean advancedEffects;
    private String wallpaperMode;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        columns = config.columns;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            LinearLayout row = (LinearLayout) columnChoices[0].getParent();
            row.removeAllViews();
            for (int i = columnChoices.length - 1; i >= 0; i--) row.addView(columnChoices[i]);
        }
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            setResult(RESULT_OK);
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        if (choices.contains("wallpaper_mode")) {
            wallpaperMode = choices.getString("wallpaper_mode", MODE_WEATHER);
        } else if (choices.contains("bing_wallpaper")) {
            wallpaperMode = choices.getBoolean("bing_wallpaper", true) ? MODE_BING : MODE_OFF;
        } else {
            wallpaperMode = MODE_WEATHER;
        }


        wallpaperModeWeather = findViewById(R.id.wallpaperModeWeather);
        wallpaperModeBing = findViewById(R.id.wallpaperModeBing);
        wallpaperModeOff = findViewById(R.id.wallpaperModeOff);

        wallpaperModeWeather.setOnClickListener(v -> setWallpaperMode(MODE_WEATHER));
        wallpaperModeBing.setOnClickListener(v -> setWallpaperMode(MODE_BING));
        wallpaperModeOff.setOnClickListener(v -> setWallpaperMode(MODE_OFF));

        weatherCityChoice = findViewById(R.id.weatherCityChoice);
        weatherCityChoice.setOnClickListener(v -> showCityDialog());

        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove("animations").remove("advanced_effects")
                    .remove("bing_wallpaper").remove("wallpaper_mode").remove("weather_particles").commit();
            WeatherManager.setCustomCity(this, null, 0, 0);
            Config current = Config.load(this);
            columns = current.columns;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            wallpaperMode = MODE_WEATHER;
            setResult(RESULT_OK);
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });

        checkUpdateChoice = findViewById(R.id.checkUpdateChoice);
        checkUpdateStatus = findViewById(R.id.checkUpdateStatus);
        String currentVersionName = UpdateManager.getCurrentVersionName(this);
        checkUpdateChoice.setText("● " + getString(R.string.check_update, currentVersionName));
        checkUpdateChoice.setOnClickListener(v -> {
            checkUpdateStatus.setText(R.string.checking_update);
            UpdateManager.checkUpdate(this, info -> {
                if (isFinishing() || isDestroyed()) return;
                if (info != null && info.hasUpdate) {
                    checkUpdateStatus.setText(getString(R.string.new_version_available, info.tagName));
                    UpdateManager.showUpdateDialog(LauncherSettingsActivity.this, info);
                } else if (info != null && info.tagName != null && !info.tagName.isEmpty()) {
                    checkUpdateStatus.setText(getString(R.string.already_latest_version, info.tagName));
                    Toast.makeText(LauncherSettingsActivity.this, getString(R.string.already_latest_version, info.tagName), Toast.LENGTH_SHORT).show();
                } else {
                    checkUpdateStatus.setText(R.string.check_update_failed);
                    Toast.makeText(LauncherSettingsActivity.this, R.string.check_update_failed, Toast.LENGTH_SHORT).show();
                }
            });
        });

        refresh();
        animationsChoice.requestFocus();
    }

    private void showCityDialog() {
        String currentCity = WeatherManager.getCustomCity(this);
        CharSequence[] items = new CharSequence[WeatherCityPresets.PROVINCES.length + 1];
        items[0] = getString(R.string.weather_city_auto);
        int checkedIndex = 0;
        for (int i = 0; i < WeatherCityPresets.PROVINCES.length; i++) {
            WeatherCityPresets.Province p = WeatherCityPresets.PROVINCES[i];
            items[i + 1] = p.name;
            if (checkedIndex == 0 && !currentCity.isEmpty()) {
                for (WeatherCityPresets.City c : p.cities) {
                    if (c.name.equals(currentCity)) {
                        checkedIndex = i + 1;
                        break;
                    }
                }
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.weather_city_dialog_title)
                .setSingleChoiceItems(items, checkedIndex, (dialog, which) -> {
                    if (which == 0) {
                        WeatherManager.setCustomCity(LauncherSettingsActivity.this, null, 0, 0);
                        setResult(RESULT_OK);
                        refresh();
                        dialog.dismiss();
                    } else {
                        WeatherCityPresets.Province selectedProvince = WeatherCityPresets.PROVINCES[which - 1];
                        if (selectedProvince.cities.length == 1) {
                            WeatherCityPresets.City selected = selectedProvince.cities[0];
                            WeatherManager.setCustomCity(LauncherSettingsActivity.this, selected.name, selected.lat, selected.lon);
                            setResult(RESULT_OK);
                            refresh();
                            dialog.dismiss();
                        } else {
                            showSubCityDialog(selectedProvince, currentCity, dialog);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showSubCityDialog(WeatherCityPresets.Province province, String currentCity, android.content.DialogInterface parentDialog) {
        CharSequence[] cityNames = new CharSequence[province.cities.length];
        int checkedCityIndex = -1;
        for (int i = 0; i < province.cities.length; i++) {
            cityNames[i] = province.cities[i].name;
            if (province.cities[i].name.equals(currentCity)) {
                checkedCityIndex = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(province.name)
                .setSingleChoiceItems(cityNames, checkedCityIndex, (dialog, which) -> {
                    WeatherCityPresets.City selected = province.cities[which];
                    WeatherManager.setCustomCity(LauncherSettingsActivity.this, selected.name, selected.lat, selected.lon);
                    setResult(RESULT_OK);
                    refresh();
                    dialog.dismiss();
                    if (parentDialog != null) {
                        parentDialog.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        setResult(RESULT_OK);
        refresh();
    }

    private void setWallpaperMode(String mode) {
        if (mode.equals(wallpaperMode)) return;
        wallpaperMode = mode;
        choices.edit()
                .putString("wallpaper_mode", wallpaperMode)
                .putBoolean("bing_wallpaper", MODE_BING.equals(wallpaperMode))
                .apply();
        setResult(RESULT_OK);
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);

        boolean isWeather = MODE_WEATHER.equals(wallpaperMode);
        boolean isBing = MODE_BING.equals(wallpaperMode);
        boolean isOff = MODE_OFF.equals(wallpaperMode);
        wallpaperModeWeather.setSelected(isWeather);
        wallpaperModeBing.setSelected(isBing);
        wallpaperModeOff.setSelected(isOff);
        wallpaperModeWeather.setText((isWeather ? "● " : "○ ") + getString(R.string.wallpaper_mode_weather));
        wallpaperModeBing.setText((isBing ? "● " : "○ ") + getString(R.string.wallpaper_mode_bing));
        wallpaperModeOff.setText((isOff ? "● " : "○ ") + getString(R.string.wallpaper_mode_off));

        weatherCityChoice.setEnabled(true);
        weatherCityChoice.setAlpha(1.0f);

        String currentCity = WeatherManager.getCustomCity(this);
        String cityDisplay = currentCity.isEmpty() ? getString(R.string.weather_city_auto) : currentCity;
        weatherCityChoice.setText(getString(R.string.weather_city_label, cityDisplay));

        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains("animations")
                || choices.contains("advanced_effects") || choices.contains("bing_wallpaper")
                || choices.contains("wallpaper_mode") || choices.contains("weather_particles")
                || !WeatherManager.getCustomCity(this).isEmpty();
    }

    private void open(Intent intent) {
        if (intent == null) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
