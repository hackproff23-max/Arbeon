package com.arbeon.browser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Ícones preenchidos da tela de Configurações, desenhados no código (sem imagens). */
final class SIcon extends View {
    static final int SHIELD_LOCK = 0, COMPASS = 1, GEAR = 2, BELL = 3, ACCESS = 4, CHEVRON = 5,
            HELP = 6, SLIDERS = 7, MONITOR = 8, TRASH = 9, INFO = 10, PALETTE = 11;

    private final int type;
    private final int color;
    private final float iconDp;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);   // traço
    private final Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);   // preenchimento
    private final Paint w = new Paint(Paint.ANTI_ALIAS_FLAG);   // branco (preenchido)
    private final Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG);  // branco (traço)
    private final Path path = new Path();
    private final RectF rect = new RectF();

    SIcon(Context c, int type, int color, int iconDp) {
        super(c);
        this.type = type;
        this.color = color;
        this.iconDp = iconDp;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        f.setStyle(Paint.Style.FILL);
        w.setStyle(Paint.Style.FILL);
        w.setColor(0xFFFFFFFF);
        wp.setStyle(Paint.Style.STROKE);
        wp.setStrokeCap(Paint.Cap.ROUND);
        wp.setColor(0xFFFFFFFF);
    }

    @Override
    protected void onDraw(Canvas c) {
        float size = iconDp * getResources().getDisplayMetrics().density;
        float u = size / 24f;
        c.save();
        c.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
        p.setColor(color);
        f.setColor(color);
        p.setStrokeWidth(2.3f * u);

        switch (type) {
            case SHIELD_LOCK:
                path.reset();
                path.moveTo(12 * u, 2.2f * u);
                path.lineTo(20 * u, 5.2f * u);
                path.lineTo(20 * u, 11.5f * u);
                path.cubicTo(20 * u, 16.3f * u, 16.6f * u, 19.8f * u, 12 * u, 21.8f * u);
                path.cubicTo(7.4f * u, 19.8f * u, 4 * u, 16.3f * u, 4 * u, 11.5f * u);
                path.lineTo(4 * u, 5.2f * u);
                path.close();
                c.drawPath(path, f);
                rect.set(8.6f * u, 11f * u, 15.4f * u, 16.2f * u);
                c.drawRoundRect(rect, 1.2f * u, 1.2f * u, w);
                wp.setStrokeWidth(1.5f * u);
                rect.set(9.7f * u, 7f * u, 14.3f * u, 13f * u);
                c.drawArc(rect, 180, 180, false, wp);
                break;
            case COMPASS:
                c.drawCircle(12 * u, 12 * u, 10 * u, f);
                path.reset();
                path.moveTo(16.8f * u, 7.2f * u);
                path.lineTo(13.4f * u, 13.4f * u);
                path.lineTo(7.2f * u, 16.8f * u);
                path.lineTo(10.6f * u, 10.6f * u);
                path.close();
                c.drawPath(path, w);
                c.drawCircle(12 * u, 12 * u, 1.1f * u, f);
                break;
            case GEAR:
                c.drawCircle(12 * u, 12 * u, 6.8f * u, f);
                for (int i = 0; i < 8; i++) {
                    c.save();
                    c.rotate(i * 45f, 12 * u, 12 * u);
                    rect.set(10.3f * u, 2.6f * u, 13.7f * u, 6.8f * u);
                    c.drawRoundRect(rect, 0.8f * u, 0.8f * u, f);
                    c.restore();
                }
                c.drawCircle(12 * u, 12 * u, 3f * u, w);
                break;
            case BELL:
                path.reset();
                path.moveTo(4.5f * u, 17.5f * u);
                path.cubicTo(6.5f * u, 16f * u, 7f * u, 14.5f * u, 7f * u, 11f * u);
                path.cubicTo(7f * u, 8f * u, 9f * u, 5.8f * u, 12f * u, 5.8f * u);
                path.cubicTo(15f * u, 5.8f * u, 17f * u, 8f * u, 17f * u, 11f * u);
                path.cubicTo(17f * u, 14.5f * u, 17.5f * u, 16f * u, 19.5f * u, 17.5f * u);
                path.close();
                c.drawPath(path, f);
                c.drawCircle(12 * u, 4.4f * u, 1.4f * u, f);
                rect.set(9.6f * u, 17.6f * u, 14.4f * u, 21.6f * u);
                c.drawOval(rect, f);
                break;
            case ACCESS:
                c.drawCircle(12 * u, 4.6f * u, 2.4f * u, f);
                p.setStrokeWidth(2.6f * u);
                c.drawLine(4.5f * u, 9.2f * u, 19.5f * u, 9.2f * u, p);
                c.drawLine(12 * u, 9.2f * u, 12 * u, 14.5f * u, p);
                c.drawLine(12 * u, 14.5f * u, 9.4f * u, 21 * u, p);
                c.drawLine(12 * u, 14.5f * u, 14.6f * u, 21 * u, p);
                break;
            case CHEVRON:
                p.setStrokeWidth(2.6f * u);
                path.reset();
                path.moveTo(9 * u, 5 * u);
                path.lineTo(16 * u, 12 * u);
                path.lineTo(9 * u, 19 * u);
                c.drawPath(path, p);
                break;
            case HELP: {
                c.drawCircle(12 * u, 12 * u, 9.4f * u, p);
                Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
                t.setColor(color);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                t.setTextAlign(Paint.Align.CENTER);
                t.setTextSize(15 * u);
                c.drawText("?", 12 * u, 17.4f * u, t);
                break;
            }
            case SLIDERS:
                c.drawCircle(7 * u, 7 * u, 2.6f * u, p);
                c.drawLine(12 * u, 7 * u, 20.5f * u, 7 * u, p);
                c.drawLine(3.5f * u, 17 * u, 12 * u, 17 * u, p);
                c.drawCircle(17 * u, 17 * u, 2.6f * u, p);
                break;
            case MONITOR:
                rect.set(3 * u, 4 * u, 21 * u, 16 * u);
                c.drawRoundRect(rect, 2.5f * u, 2.5f * u, p);
                c.drawLine(8 * u, 20 * u, 16 * u, 20 * u, p);
                c.drawLine(12 * u, 16 * u, 12 * u, 20 * u, p);
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
            case PALETTE:
                c.drawCircle(12 * u, 12 * u, 10 * u, f);
                c.drawCircle(7.2f * u, 11.2f * u, 1.7f * u, w);
                c.drawCircle(10.2f * u, 6.8f * u, 1.7f * u, w);
                c.drawCircle(15.4f * u, 7.2f * u, 1.7f * u, w);
                c.drawCircle(18f * u, 11.6f * u, 1.7f * u, w);
                c.drawCircle(15.6f * u, 17.2f * u, 3f * u, w);
                break;
            default: // INFO
                c.drawCircle(12 * u, 12 * u, 9.4f * u, p);
                c.drawCircle(12 * u, 7.6f * u, 1.3f * u, f);
                c.drawLine(12 * u, 11 * u, 12 * u, 17 * u, p);
                break;
        }
        c.restore();
    }
}
