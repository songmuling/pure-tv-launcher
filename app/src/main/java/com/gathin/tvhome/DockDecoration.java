package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Draws the Apple TV (tvOS) style frosted glass shelf behind the first (dock) row and adds a gap below it. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF shadowRect = new RectF();
    private float lastTop = -1f, lastBottom = -1f, lastLeft = -1f, lastRight = -1f;

    DockDecoration(int dockSize, float density) {
        this.dockSize = dockSize;
        this.density = density;
        shadowPaint.setColor(0x28000000); // Soft ambient bottom shadow
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1.2f * density);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (4 * density);
            outRect.bottom = (int) (6 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) continue;
            RecyclerView.ViewHolder vh = parent.getChildViewHolder(child);
            View card = vh instanceof AppAdapter.Holder ? ((AppAdapter.Holder) vh).card : child.findViewById(R.id.card);
            if (card == null) continue;
            // Use static resting bounds without child translation so the shelf stays firmly anchored
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        float pad = 14 * density;
        rect.inset(-pad, -pad);
        float radius = 28 * density;

        // Update shaders only when shelf bounds change to avoid GC allocations during render frames
        if (rect.left != lastLeft || rect.top != lastTop || rect.right != lastRight || rect.bottom != lastBottom) {
            lastLeft = rect.left;
            lastTop = rect.top;
            lastRight = rect.right;
            lastBottom = rect.bottom;
            // Apple TV translucent glass gradient fill (luminous top to deeper bottom)
            band.setShader(new LinearGradient(rect.left, rect.top, rect.left, rect.bottom,
                    0x2AFFFFFF, 0x12FFFFFF, Shader.TileMode.CLAMP));
            // Specular rim stroke with top light catcher
            edge.setShader(new LinearGradient(rect.left, rect.top, rect.left, rect.bottom,
                    0x55FFFFFF, 0x18FFFFFF, Shader.TileMode.CLAMP));
        }

        // 1. Ambient drop shadow under shelf
        shadowRect.set(rect.left, rect.top + 4 * density, rect.right, rect.bottom + 6 * density);
        c.drawRoundRect(shadowRect, radius + 2 * density, radius + 2 * density, shadowPaint);

        // 2. Frosted glass body fill
        c.drawRoundRect(rect, radius, radius, band);

        // 3. Specular highlight rim
        c.drawRoundRect(rect, radius, radius, edge);
    }
}
