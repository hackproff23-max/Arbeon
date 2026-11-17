package com.arbeon.browser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.TypedValue;
import android.view.View;

/** Ícone de download (seta + bandeja) para a barra de endereço. Fica azul com bolinha quando há download ativo. */
final class DownloadIconView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    DownloadIconView(Context c, View.OnClickListener l) {
        super(c);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(0xFF1A73E8);
        setClickable(true);
        setOnClickListener(l);
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        if (tv.resourceId != 0) setBackgroundResource(tv.resourceId);
        setContentDescription("Downloads");
    }

    private boolean anyRunning() {
        try {
            for (DlItem it : DownloadEngine.get(getContext()).items) {
                if (it.state == DlItem.RUNNING) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        boolean active = anyRunning();
        float w = getWidth(), h = getHeight();
        float s = Math.min(w, h) * 0.30f;
        float cx = w / 2f, cy = h / 2f;
        p.setStrokeWidth(s * 0.24f);
        p.setColor(active ? 0xFF1A73E8 : 0xFF5F6368);

        // seta
        path.reset();
        path.moveTo(cx, cy - s * 0.95f);
        path.lineTo(cx, cy + s * 0.35f);
        path.moveTo(cx - s * 0.55f, cy - s * 0.2f);
        path.lineTo(cx, cy + s * 0.35f);
        path.lineTo(cx + s * 0.55f, cy - s * 0.2f);
        // bandeja
        path.moveTo(cx - s * 0.95f, cy + s * 0.3f);
        path.lineTo(cx - s * 0.95f, cy + s * 0.95f);
        path.lineTo(cx + s * 0.95f, cy + s * 0.95f);
        path.lineTo(cx + s * 0.95f, cy + s * 0.3f);
        c.drawPath(path, p);

        if (active) c.drawCircle(cx + s * 0.95f, cy - s * 0.85f, s * 0.3f, dot);
        postInvalidateDelayed(800);
    }
}
