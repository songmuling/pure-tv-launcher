package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * 6 槽位 Dock 程序坞持久化管理模块
 * 1. 严格固定 6 个槽位，支持独立绑定应用或留空为 +
 * 2. 槽位完全独立，允许同一应用固定到不同槽位
 */
public final class DockManager {
    public static final int DOCK_SIZE = 6;
    private static final String PREF_NAME = "custom_dock";
    private static final String KEY_PREFIX = "dock_slot_";

    private DockManager() {}

    public static List<String> getSlots(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        List<String> list = new ArrayList<>(DOCK_SIZE);
        for (int i = 0; i < DOCK_SIZE; i++) {
            list.add(sp.getString(KEY_PREFIX + i, ""));
        }
        return list;
    }

    public static String getSlot(Context context, int index) {
        if (index < 0 || index >= DOCK_SIZE) return "";
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PREFIX + index, "");
    }

    public static void setSlot(Context context, int index, String componentString) {
        if (index < 0 || index >= DOCK_SIZE) return;
        SharedPreferences.Editor editor = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        if (componentString == null || componentString.isEmpty()) {
            editor.putString(KEY_PREFIX + index, "");
        } else {
            editor.putString(KEY_PREFIX + index, componentString);
        }
        editor.apply();
    }

    public static boolean isInitialized(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).contains(KEY_PREFIX + "0");
    }

    public static void initDefaults(Context context, List<String> defaultApps) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        int count = Math.min(DOCK_SIZE, defaultApps != null ? defaultApps.size() : 0);
        for (int i = 0; i < DOCK_SIZE; i++) {
            if (i < count) {
                editor.putString(KEY_PREFIX + i, defaultApps.get(i));
            } else {
                editor.putString(KEY_PREFIX + i, "");
            }
        }
        editor.apply();
    }
}
