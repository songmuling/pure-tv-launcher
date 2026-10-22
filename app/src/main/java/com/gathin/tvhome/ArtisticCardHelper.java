package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.LruCache;

/**
 * 为没有 16:9 Banner 的手机/非电视应用生成与应用图标主色相呼应的高级深色微渐变背景。
 * 彻底解决“大黑框中间放小图标”的空洞廉价感，使每个应用呈现定制海报般的高质感。
 */
public final class ArtisticCardHelper {

    private static final LruCache<String, Drawable> BG_CACHE = new LruCache<>(128);

    private ArtisticCardHelper() {}

    /**
     * 获取或生成与应用图标契合的高级深色渐变卡片背景
     */
    public static Drawable getCardBackground(Context ctx, String pkg, Drawable icon) {
        if (pkg != null) {
            Drawable cached = BG_CACHE.get(pkg);
            if (cached != null) return cached.getConstantState() != null ? cached.getConstantState().newDrawable() : cached;
        }

        float density = ctx.getResources().getDisplayMetrics().density;
        int dominant = extractDominantColor(icon);

        float[] hsv = new float[3];
        Color.colorToHSV(dominant, hsv);

        int startColor;
        int endColor;

        // 如果图标饱和度极低（黑白灰系图标），使用考究的深石板黑蓝
        if (hsv[1] < 0.12f) {
            startColor = 0xFF2A3442;
            endColor = 0xFF151B22;
        } else {
            // 色彩调谐：明度适度压低形成深沉高雅底色，饱和度适度收敛
            float[] hsvTop = new float[]{hsv[0], Math.min(0.55f, Math.max(0.30f, hsv[1] * 0.85f)), 0.32f};
            float[] hsvBottom = new float[]{hsv[0], Math.min(0.45f, Math.max(0.20f, hsv[1] * 0.65f)), 0.14f};
            startColor = 0xFF000000 | Color.HSVToColor(hsvTop);
            endColor = 0xFF000000 | Color.HSVToColor(hsvBottom);
        }

        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{startColor, endColor});
        gd.setCornerRadius(16f * density);
        gd.setStroke((int) Math.max(1, density), 0x26FFFFFF);

        if (pkg != null) {
            BG_CACHE.put(pkg, gd);
        }
        return gd;
    }

    /**
     * 超轻量快速采样（32x32 采样图），毫秒级提取图标最具代表性的鲜活主色调
     */
    private static int extractDominantColor(Drawable drawable) {
        if (drawable == null) return 0xFF2A3442;

        try {
            int w = 32;
            int h = 32;
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, w, h);
            drawable.draw(canvas);

            int[] pixels = new int[w * h];
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
            bitmap.recycle();

            float maxScore = -1f;
            int bestColor = 0xFF2A3442;

            float[] hsv = new float[3];
            for (int color : pixels) {
                int alpha = (color >>> 24);
                if (alpha < 100) continue; // 忽略高透明度像素

                Color.colorToHSV(color, hsv);
                // 忽略过亮或过暗的极端像素
                if (hsv[2] < 0.15f || hsv[2] > 0.95f) continue;

                // 评分：饱和度越高、透明度越高的颜色权重更大
                float score = hsv[1] * hsv[2] * (alpha / 255f);
                if (score > maxScore) {
                    maxScore = score;
                    bestColor = color;
                }
            }

            return bestColor;
        } catch (Throwable t) {
            return 0xFF2A3442;
        }
    }
}
