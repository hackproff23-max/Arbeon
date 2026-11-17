package com.arbeon.browser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.TypedValue;
import android.view.View;

/** Ícone de pasta com seta de download, do mesmo tamanho dos outros botões da barra.
 *  Fica azul com uma bolinha quando há download ativo. */
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
        float box = 22f * getResources().getDisplayMetrics().density; // mesmo porte dos ícones de 22sp
        float hx = box * 0.5f, hy = box * 0.42f;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float x1 = cx - hx, x2 = cx + hx;
        float yTop = cy - hy, y1 = cy - hy * 0.55f, y2 = cy + hy;

        p.setStrokeWidth(box * 0.085f);
        p.setColor(active ? 0xFF1A73E8 : 0xFF5F6368);

        // pasta
        path.reset();
        path.moveTo(x1, y2);
        path.lineTo(x1, yTop);
        path.lineTo(cx - hx * 0.2f, yTop);
        path.lineTo(cx + hx * 0.05f, y1);
        path.lineTo(x2, y1);
        path.lineTo(x2, y2);
        path.close();
        c.drawPath(path, p);

        // seta de download dentro da pasta
        float a0 = y1 + (y2 - y1) * 0.22f, a1 = y2 - (y2 - y1) * 0.22f, k = hx * 0.3f;
        path.reset();
        path.moveTo(cx, a0);
        path.lineTo(cx, a1);
        path.moveTo(cx - k, a1 - k);
        path.lineTo(cx, a1);
        path.lineTo(cx + k, a1 - k);
        c.drawPath(path, p);

        if (active) c.drawCircle(x2, yTop, box * 0.13f, dot);
        postInvalidateDelayed(800);
    }
}
