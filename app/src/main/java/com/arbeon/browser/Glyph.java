package com.arbeon.browser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Ícones desenhados no código (setas, recarregar, lupa, estrela, download, ⋮, +, X, globo). */
final class Glyph extends View {
    static final int BACK = 0, FWD = 1, RELOAD = 2, SEARCH = 3, STAR = 4, DOWNLOAD = 5,
            DOTS = 6, PLUS = 7, CLOSE = 8, GLOBE = 9, PLUSBOX = 10, TRASH = 11,
            SLIDERS = 12, DLCHECK = 13, SHARE = 14;

    private final int type;
    private int color;
    private boolean on;
    private final float iconDp;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    Glyph(Context c, int type, int color, int iconDp) {
        super(c);
        this.type = type;
        this.color = color;
        this.iconDp = iconDp;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        f.setStyle(Paint.Style.FILL);
        setClickable(true);
        if (type == DOWNLOAD) setContentDescription("Downloads");
    }

    void setColor(int c) {
        if (c != color) {
            color = c;
            invalidate();
        }
    }

    void setOn(boolean v) {
        if (v != on) {
            on = v;
            invalidate();
        }
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

    private void arrowHead(Canvas c, float px, float py, float tx, float ty, float len) {
        // duas linhas saindo da ponta, abertas ±40° para trás
        for (int s = -1; s <= 1; s += 2) {
            double a = Math.toRadians(40 * s);
            float bx = -tx, by = -ty;
            float dx = (float) (bx * Math.cos(a) - by * Math.sin(a));
            float dy = (float) (bx * Math.sin(a) + by * Math.cos(a));
            c.drawLine(px, py, px + dx * len, py + dy * len, p);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float size = iconDp * getResources().getDisplayMetrics().density;
        float u = size / 24f;
        c.save();
        c.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
        boolean running = type == DOWNLOAD && anyRunning();
        int col = running ? Fx.GLOW : color;
        p.setColor(col);
        f.setColor(col);
        p.setStrokeWidth((type == PLUS ? 2.8f : (type == GLOBE ? 1.8f : 2.3f)) * u);

        switch (type) {
            case BACK:
                c.drawLine(19 * u, 12 * u, 5 * u, 12 * u, p);
                path.reset();
                path.moveTo(11 * u, 6 * u);
                path.lineTo(5 * u, 12 * u);
                path.lineTo(11 * u, 18 * u);
                c.drawPath(path, p);
                break;
            case FWD:
                c.drawLine(5 * u, 12 * u, 19 * u, 12 * u, p);
                path.reset();
                path.moveTo(13 * u, 6 * u);
                path.lineTo(19 * u, 12 * u);
                path.lineTo(13 * u, 18 * u);
                c.drawPath(path, p);
                break;
            case RELOAD: {
                rect.set(5 * u, 5 * u, 19 * u, 19 * u);
                c.drawArc(rect, 200, 140, false, p);
                c.drawArc(rect, 20, 140, false, p);
                float r = 7 * u;
                double[] ends = {Math.toRadians(340), Math.toRadians(160)};
                for (double th : ends) {
                    float px = 12 * u + r * (float) Math.cos(th);
                    float py = 12 * u + r * (float) Math.sin(th);
                    float tx = (float) -Math.sin(th), ty = (float) Math.cos(th);
                    arrowHead(c, px, py, tx, ty, 4.4f * u);
                }
                break;
            }
            case SEARCH:
                c.drawCircle(10.5f * u, 10.5f * u, 6.3f * u, p);
                c.drawLine(15.3f * u, 15.3f * u, 20 * u, 20 * u, p);
                break;
            case STAR:
                path.reset();
                for (int i = 0; i < 10; i++) {
                    double ang = -Math.PI / 2 + i * Math.PI / 5;
                    float rr = (i % 2 == 0 ? 9.8f : 4.3f) * u;
                    float x = 12 * u + rr * (float) Math.cos(ang);
                    float y = 12.7f * u + rr * (float) Math.sin(ang);
                    if (i == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                }
                path.close();
                c.drawPath(path, on ? f : p);
                if (on) c.drawPath(path, p);
                break;
            case DOWNLOAD:
                c.drawLine(12 * u, 4 * u, 12 * u, 15 * u, p);
                path.reset();
                path.moveTo(7.5f * u, 10.5f * u);
                path.lineTo(12 * u, 15 * u);
                path.lineTo(16.5f * u, 10.5f * u);
                c.drawPath(path, p);
                path.reset();
                path.moveTo(5 * u, 16 * u);
                path.lineTo(5 * u, 19.5f * u);
                path.lineTo(19 * u, 19.5f * u);
                path.lineTo(19 * u, 16 * u);
                c.drawPath(path, p);
                if (running) c.drawCircle(19.5f * u, 4.5f * u, 3.2f * u, f);
                postInvalidateDelayed(800);
                break;
            case DOTS:
                c.drawCircle(12 * u, 5 * u, 2.2f * u, f);
                c.drawCircle(12 * u, 12 * u, 2.2f * u, f);
                c.drawCircle(12 * u, 19 * u, 2.2f * u, f);
                break;
            case PLUS:
                c.drawLine(12 * u, 5 * u, 12 * u, 19 * u, p);
                c.drawLine(5 * u, 12 * u, 19 * u, 12 * u, p);
                break;
            case CLOSE:
                c.drawLine(6 * u, 6 * u, 18 * u, 18 * u, p);
                c.drawLine(18 * u, 6 * u, 6 * u, 18 * u, p);
                break;
            case PLUSBOX:
                rect.set(3.5f * u, 3.5f * u, 20.5f * u, 20.5f * u);
                c.drawRoundRect(rect, 4.5f * u, 4.5f * u, p);
                c.drawLine(12 * u, 8 * u, 12 * u, 16 * u, p);
                c.drawLine(8 * u, 12 * u, 16 * u, 12 * u, p);
                break;
            case TRASH:
                c.drawLine(4.5f * u, 6.5f * u, 19.5f * u, 6.5f * u, p);
                path.reset();
                path.moveTo(9 * u, 6.5f * u);
                path.lineTo(9 * u, 4 * u);
                path.lineTo(15 * u, 4 * u);
                path.lineTo(15 * u, 6.5f * u);
                c.drawPath(path, p);
                path.reset();
                path.moveTo(6.5f * u, 6.5f * u);
                path.lineTo(7.4f * u, 20 * u);
                path.lineTo(16.6f * u, 20 * u);
                path.lineTo(17.5f * u, 6.5f * u);
                c.drawPath(path, p);
                c.drawLine(10.2f * u, 10 * u, 10.2f * u, 16.5f * u, p);
                c.drawLine(13.8f * u, 10 * u, 13.8f * u, 16.5f * u, p);
                break;
            case SLIDERS:
                c.drawCircle(7 * u, 7 * u, 2.6f * u, p);
                c.drawLine(12 * u, 7 * u, 20.5f * u, 7 * u, p);
                c.drawLine(3.5f * u, 17 * u, 12 * u, 17 * u, p);
                c.drawCircle(17 * u, 17 * u, 2.6f * u, p);
                break;
            case DLCHECK:
                path.reset();
                path.moveTo(5.5f * u, 10.5f * u);
                path.lineTo(10.5f * u, 15.5f * u);
                path.lineTo(19 * u, 5.5f * u);
                c.drawPath(path, p);
                c.drawLine(5 * u, 20 * u, 19 * u, 20 * u, p);
                break;
            case SHARE:
                c.drawLine(6 * u, 12 * u, 18 * u, 5.5f * u, p);
                c.drawLine(6 * u, 12 * u, 18 * u, 18.5f * u, p);
                c.drawCircle(6 * u, 12 * u, 3f * u, f);
                c.drawCircle(18 * u, 5.5f * u, 3f * u, f);
                c.drawCircle(18 * u, 18.5f * u, 3f * u, f);
                break;
            default: // GLOBE
                c.drawCircle(12 * u, 12 * u, 9.3f * u, p);
                rect.set(8 * u, 2.7f * u, 16 * u, 21.3f * u);
                c.drawOval(rect, p);
                c.drawLine(2.7f * u, 12 * u, 21.3f * u, 12 * u, p);
                c.drawLine(4.2f * u, 7.2f * u, 19.8f * u, 7.2f * u, p);
                c.drawLine(4.2f * u, 16.8f * u, 19.8f * u, 16.8f * u, p);
                break;
        }
        c.restore();
    }
}
