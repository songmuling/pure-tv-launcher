package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * 全屏简洁桌面设置 Dialog
 * 1. 复用与全部应用抽屉（AllAppsDialog）相同的沉浸式全屏方案
 * 2. 打开时桌面自动隐藏 Dock 栏与应用网格，背景仅呈现干净纯粹的桌面壁纸
 * 3. 关闭或按返回/Home键时零延迟恢复桌面，彻底根除跨 Activity 重建闪黑问题
 * 4. 每次点选“随机壁纸”立即从 Bing 抽取全新壁纸并实时渲染
 */
public final class LauncherSettingsDialog extends Dialog {

    public interface Callback {
        void onDismissed(boolean configChanged);
        void onWallpaperChanged();
    }

    private final Activity activity;
    private final Callback callback;
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView wallpaperModeRandom;
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
    private boolean configChanged = false;

    public LauncherSettingsDialog(@NonNull Activity activity, Callback callback) {
        super(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.activity = activity;
        this.callback = callback;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_launcher_settings);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        choices = activity.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        Config config = Config.load(activity);
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
                configChanged = true;
                refresh();
            });
        }
        if (activity.getResources().getBoolean(R.bool.rtl_app_grid)) {
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
            configChanged = true;
            refresh();
        });

        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });

        if (choices.contains("wallpaper_mode")) {
            wallpaperMode = choices.getString("wallpaper_mode", LauncherSettingsActivity.MODE_RANDOM);
            if ("weather".equals(wallpaperMode)) wallpaperMode = LauncherSettingsActivity.MODE_RANDOM;
        } else if (choices.contains("bing_wallpaper")) {
            wallpaperMode = choices.getBoolean("bing_wallpaper", true)
                    ? LauncherSettingsActivity.MODE_BING : LauncherSettingsActivity.MODE_OFF;
        } else {
            wallpaperMode = LauncherSettingsActivity.MODE_RANDOM;
        }

        wallpaperModeRandom = findViewById(R.id.wallpaperModeRandom);
        wallpaperModeBing = findViewById(R.id.wallpaperModeBing);
        wallpaperModeOff = findViewById(R.id.wallpaperModeOff);

        wallpaperModeRandom.setOnClickListener(v -> {
            wallpaperMode = LauncherSettingsActivity.MODE_RANDOM;
            choices.edit().putString("wallpaper_mode", LauncherSettingsActivity.MODE_RANDOM).apply();
            refresh();

            // 无论之前是否已是随机壁纸，每次点选都强制随机换一张新壁纸！
            Toast.makeText(activity, R.string.wallpaper_random_switching, Toast.LENGTH_SHORT).show();
            RandomWallpaper.pickNextRandom(activity, file -> {
                if (!isShowing()) return;
                if (file != null) {
                    Toast.makeText(activity, R.string.wallpaper_random_switched, Toast.LENGTH_SHORT).show();
                    if (callback != null) callback.onWallpaperChanged();
                } else {
                    Toast.makeText(activity, R.string.wallpaper_random_failed, Toast.LENGTH_SHORT).show();
                }
            });
        });

        wallpaperModeBing.setOnClickListener(v -> {
            setWallpaperMode(LauncherSettingsActivity.MODE_BING);
            if (callback != null) callback.onWallpaperChanged();
        });

        wallpaperModeOff.setOnClickListener(v -> {
            setWallpaperMode(LauncherSettingsActivity.MODE_OFF);
            if (callback != null) callback.onWallpaperChanged();
        });

        weatherCityChoice = findViewById(R.id.weatherCityChoice);
        weatherCityChoice.setOnClickListener(v -> showCityDialog());

        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove("animations").remove("advanced_effects")
                    .remove("bing_wallpaper").remove("wallpaper_mode").remove("weather_particles").commit();
            WeatherManager.setCustomCity(activity, null, 0, 0);
            Config current = Config.load(activity);
            columns = current.columns;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            wallpaperMode = LauncherSettingsActivity.MODE_RANDOM;
            configChanged = true;
            refresh();
            if (callback != null) callback.onWallpaperChanged();
        });

        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(activity, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });

        checkUpdateChoice = findViewById(R.id.checkUpdateChoice);
        checkUpdateStatus = findViewById(R.id.checkUpdateStatus);
        String currentVersionName = UpdateManager.getCurrentVersionName(activity);
        checkUpdateChoice.setText("● " + activity.getString(R.string.check_update, currentVersionName));

        checkUpdateChoice.setOnClickListener(v -> {
            checkUpdateStatus.setText(R.string.checking_update);
            UpdateManager.checkUpdate(activity, info -> {
                if (!isShowing()) return;
                if (info != null && info.hasUpdate) {
                    checkUpdateStatus.setText(activity.getString(R.string.new_version_available, info.tagName));
                    UpdateManager.showUpdateDialog(activity, info);
                } else if (info != null && info.tagName != null && !info.tagName.isEmpty()) {
                    checkUpdateStatus.setText(activity.getString(R.string.already_latest_version, info.tagName));
                    Toast.makeText(activity, activity.getString(R.string.already_latest_version, info.tagName), Toast.LENGTH_SHORT).show();
                } else {
                    checkUpdateStatus.setText(R.string.check_update_failed);
                    Toast.makeText(activity, R.string.check_update_failed, Toast.LENGTH_SHORT).show();
                }
            });
        });

        refresh();
        animationsChoice.requestFocus();

        setOnDismissListener(dialog -> {
            if (callback != null) callback.onDismissed(configChanged);
        });
    }

    private void showCityDialog() {
        String currentCity = WeatherManager.getCustomCity(activity);
        CharSequence[] items = new CharSequence[WeatherCityPresets.PROVINCES.length + 1];
        items[0] = activity.getString(R.string.weather_city_auto);
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

        new AlertDialog.Builder(activity)
                .setTitle(R.string.weather_city_dialog_title)
                .setSingleChoiceItems(items, checkedIndex, (dialog, which) -> {
                    if (which == 0) {
                        WeatherManager.setCustomCity(activity, null, 0, 0);
                        refresh();
                        dialog.dismiss();
                    } else {
                        WeatherCityPresets.Province selectedProvince = WeatherCityPresets.PROVINCES[which - 1];
                        if (selectedProvince.cities.length == 1) {
                            WeatherCityPresets.City selected = selectedProvince.cities[0];
                            WeatherManager.setCustomCity(activity, selected.name, selected.lat, selected.lon);
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

        new AlertDialog.Builder(activity)
                .setTitle(province.name)
                .setSingleChoiceItems(cityNames, checkedCityIndex, (dialog, which) -> {
                    WeatherCityPresets.City selected = province.cities[which];
                    WeatherManager.setCustomCity(activity, selected.name, selected.lat, selected.lon);
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
        configChanged = true;
        refresh();
    }

    private void setWallpaperMode(String mode) {
        if (mode.equals(wallpaperMode)) return;
        wallpaperMode = mode;
        choices.edit()
                .putString("wallpaper_mode", wallpaperMode)
                .putBoolean("bing_wallpaper", LauncherSettingsActivity.MODE_BING.equals(wallpaperMode))
                .apply();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        animationsChoice.setText(activity.getString(R.string.animations_state,
                activity.getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(activity.getString(R.string.advanced_effects_state,
                activity.getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);

        boolean isRandom = LauncherSettingsActivity.MODE_RANDOM.equals(wallpaperMode) || "weather".equals(wallpaperMode);
        boolean isBing = LauncherSettingsActivity.MODE_BING.equals(wallpaperMode);
        boolean isOff = LauncherSettingsActivity.MODE_OFF.equals(wallpaperMode);
        wallpaperModeRandom.setSelected(isRandom);
        wallpaperModeBing.setSelected(isBing);
        wallpaperModeOff.setSelected(isOff);
        wallpaperModeRandom.setText((isRandom ? "● " : "○ ") + activity.getString(R.string.wallpaper_mode_random));
        wallpaperModeBing.setText((isBing ? "● " : "○ ") + activity.getString(R.string.wallpaper_mode_bing));
        wallpaperModeOff.setText((isOff ? "● " : "○ ") + activity.getString(R.string.wallpaper_mode_off));

        weatherCityChoice.setEnabled(true);
        weatherCityChoice.setAlpha(1.0f);

        String currentCity = WeatherManager.getCustomCity(activity);
        String cityDisplay = currentCity.isEmpty() ? activity.getString(R.string.weather_city_auto) : currentCity;
        weatherCityChoice.setText(activity.getString(R.string.weather_city_label, cityDisplay));

        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains("animations")
                || choices.contains("advanced_effects") || choices.contains("bing_wallpaper")
                || choices.contains("wallpaper_mode") || choices.contains("weather_particles")
                || !WeatherManager.getCustomCity(activity).isEmpty();
    }

    private void open(Intent intent) {
        if (intent == null) {
            Toast.makeText(activity, R.string.not_available, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
