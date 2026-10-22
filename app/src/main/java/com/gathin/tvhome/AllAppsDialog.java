package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 全屏“全部应用”抽屉 Dialog
 * 1. 采用 6 列自适应网格展示所有已安装应用
 * 2. 纯粹只负责浏览与启动，点击直接打开，按返回键无缝缩回
 */
final class AllAppsDialog extends Dialog {

    public interface Callback {
        void onDismissed();
    }

    private final Activity activity;
    private final boolean animations;
    private final Callback callback;
    private final List<AppItem> appList = new ArrayList<>();
    private final Set<String> leanbackPackages = new HashSet<>();

    static class AppItem {
        final String label;
        final ComponentName component;
        final Drawable banner;
        final Drawable icon;
        final String pkg;

        AppItem(String label, ComponentName component, Drawable banner, Drawable icon, String pkg) {
            this.label = label;
            this.component = component;
            this.banner = banner;
            this.icon = icon;
            this.pkg = pkg;
        }
    }

    AllAppsDialog(@NonNull Activity activity, boolean animations, Callback callback) {
        super(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.activity = activity;
        this.animations = animations;
        this.callback = callback;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_all_apps);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        loadInstalledApps();

        RecyclerView grid = findViewById(R.id.allAppsGrid);
        if (grid != null) {
            grid.setLayoutManager(new GridLayoutManager(activity, 6));
            grid.setAdapter(new AllAppsAdapter());
            grid.post(() -> {
                if (grid.getChildCount() > 0) {
                    grid.getChildAt(0).requestFocus();
                }
            });
        }

        setOnDismissListener(dialog -> {
            if (callback != null) callback.onDismissed();
        });
    }

    private final android.content.BroadcastReceiver homeReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())) {
                dismiss();
            }
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        try {
            activity.registerReceiver(homeReceiver, new android.content.IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        } catch (Exception ignored) {}
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            activity.unregisterReceiver(homeReceiver);
        } catch (Exception ignored) {}
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_HOME) {
            dismiss();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void loadInstalledApps() {
        PackageManager pm = activity.getPackageManager();
        Intent tvIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> tvFound = pm.queryIntentActivities(tvIntent, 0);
        for (ResolveInfo ri : tvFound) {
            if (ri.activityInfo != null) leanbackPackages.add(ri.activityInfo.packageName);
        }

        Map<String, ResolveInfo> map = new LinkedHashMap<>();
        for (ResolveInfo ri : tvFound) {
            if (ri.activityInfo != null && !ri.activityInfo.packageName.equals(activity.getPackageName())) {
                map.put(ri.activityInfo.packageName, ri);
            }
        }

        Intent mainIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launcherFound = pm.queryIntentActivities(mainIntent, 0);
        for (ResolveInfo ri : launcherFound) {
            if (ri.activityInfo != null && !ri.activityInfo.packageName.equals(activity.getPackageName())) {
                if (!map.containsKey(ri.activityInfo.packageName)) {
                    map.put(ri.activityInfo.packageName, ri);
                }
            }
        }

        appList.clear();
        for (ResolveInfo ri : map.values()) {
            String pkg = ri.activityInfo.packageName;
            String label = Cards.cleanLabel(String.valueOf(ri.loadLabel(pm)), pkg);
            Drawable banner = loadBanner(pm, ri);
            Drawable icon = ri.loadIcon(pm);
            ComponentName cn = new ComponentName(pkg, ri.activityInfo.name);
            appList.add(new AppItem(label, cn, banner, icon, pkg));
        }

        Collator collator = Collator.getInstance();
        appList.sort((a, b) -> {
            boolean aIsTv = a.banner != null;
            boolean bIsTv = b.banner != null;
            if (aIsTv != bIsTv) {
                return aIsTv ? -1 : 1; // 电视原生大图标排在前面，非电视小图标排在后面
            }
            String la = a.label != null ? a.label : "";
            String lb = b.label != null ? b.label : "";
            return collator.compare(la, lb);
        });
    }

    private Drawable loadBanner(PackageManager pm, ResolveInfo ri) {
        int id = ri.activityInfo.banner;
        if (id == 0) id = ri.activityInfo.applicationInfo.banner;
        Drawable banner = null;
        if (id != 0) {
            try {
                Resources resources = pm.getResourcesForApplication(ri.activityInfo.applicationInfo);
                int density = Math.max(DisplayMetrics.DENSITY_XHIGH, activity.getResources().getDisplayMetrics().densityDpi);
                banner = resources.getDrawableForDensity(id, density, null);
            } catch (Exception ignored) {}
        }
        if (banner == null) banner = ri.activityInfo.loadBanner(pm);
        return banner != null && isBannerShaped(banner.getIntrinsicWidth(), banner.getIntrinsicHeight()) ? banner : null;
    }

    private static boolean isBannerShaped(int width, int height) {
        return width <= 0 || height <= 0 || width >= height * 1.4f;
    }

    private class AllAppsAdapter extends RecyclerView.Adapter<AllAppsHolder> {

        @NonNull
        @Override
        public AllAppsHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
            int width = parent.getWidth() > 0 ? parent.getWidth() : parent.getMeasuredWidth();
            int inner = width - parent.getPaddingLeft() - parent.getPaddingRight();
            float density = activity.getResources().getDisplayMetrics().density;
            int w = AppAdapter.tileWidthPx(inner, 6, density);
            int h = AppAdapter.tileHeightPx(w);

            View card = view.findViewById(R.id.card);
            ViewGroup.LayoutParams lp = card.getLayoutParams();
            lp.width = w;
            lp.height = h;
            card.setLayoutParams(lp);

            View name = view.findViewById(R.id.name);
            ViewGroup.MarginLayoutParams nameLp = (ViewGroup.MarginLayoutParams) name.getLayoutParams();
            nameLp.width = w;
            nameLp.topMargin = h + Math.round(6 * density);
            name.setLayoutParams(nameLp);

            return new AllAppsHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AllAppsHolder holder, int position) {
            AppItem item = appList.get(position);
            holder.name.setVisibility(View.GONE);

            if (item.banner != null) {
                holder.banner.setImageDrawable(item.banner);
                holder.banner.setVisibility(View.VISIBLE);
                holder.icon.setVisibility(View.GONE);
                holder.label.setVisibility(View.GONE);
            } else {
                holder.banner.setVisibility(View.GONE);
                holder.icon.setImageDrawable(item.icon);
                holder.icon.setVisibility(View.VISIBLE);
                holder.label.setText(item.label);
                holder.label.setVisibility(View.VISIBLE);
            }

            holder.card.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_MAIN)
                        .setComponent(item.component)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                if (leanbackPackages.contains(item.pkg)) {
                    intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
                }
                try {
                    activity.startActivity(intent);
                    dismiss();
                } catch (Exception ignored) {}
            });
        }

        @Override
        public int getItemCount() {
            return appList.size();
        }
    }

    private class AllAppsHolder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;

        AllAppsHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card);
            banner = itemView.findViewById(R.id.banner);
            icon = itemView.findViewById(R.id.icon);
            label = itemView.findViewById(R.id.label);
            name = itemView.findViewById(R.id.name);

            final float density = itemView.getResources().getDisplayMetrics().density;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline outline) {
                    outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 16f * density);
                }
            });
            card.setClipToOutline(true);

            card.setFocusable(true);
            card.setOnFocusChangeListener((v, hasFocus) -> {
                float scale = hasFocus ? 1.10f : 1.0f;
                if (animations) {
                    card.animate().scaleX(scale).scaleY(scale).translationZ(hasFocus ? 8f : 0f).setDuration(160).start();
                } else {
                    card.setScaleX(scale);
                    card.setScaleY(scale);
                }
                name.animate().alpha(hasFocus ? 1f : 0.6f).setDuration(160).start();
            });
        }
    }
}
