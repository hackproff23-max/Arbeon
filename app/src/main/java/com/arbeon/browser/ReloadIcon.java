package com.arbeon.browser;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Ícone de recarregar: uma seta circular só, com ponta cheia (no lugar do ícone antigo de duas setas). */
final class ReloadIcon {
    private ReloadIcon() {
    }

    /** Desenha no espaço de 24 unidades (u = tamanho/24). p = traço, f = preenchimento, já com a cor. */
    static void draw(Canvas c, float u, Paint p, Paint f) {
        p.setStrokeWidth(2.5f * u);
        float cx = 12 * u, cy = 12.4f * u, rad = 7.3f * u;
        RectF r = new RectF(cx - rad, cy - rad, cx + rad, cy + rad);
        float start = 305f, sweep = 285f;
        c.drawArc(r, start, sweep, false, p);

        // ponta cheia no fim do arco, apontando no sentido do giro
        double th = Math.toRadians(start + sweep);
        float ex = cx + rad * (float) Math.cos(th);
        float ey = cy + rad * (float) Math.sin(th);
        float tx = (float) -Math.sin(th), ty = (float) Math.cos(th);
        float nx = -ty, ny = tx;
        float tipX = ex + tx * 4.2f * u, tipY = ey + ty * 4.2f * u;
        float bx = ex - tx * 0.4f * u, by = ey - ty * 0.4f * u;
        Path head = new Path();
        head.moveTo(tipX, tipY);
        head.lineTo(bx + nx * 3.7f * u, by + ny * 3.7f * u);
        head.lineTo(bx - nx * 3.7f * u, by - ny * 3.7f * u);
        head.close();
        c.drawPath(head, f);
    }
}
