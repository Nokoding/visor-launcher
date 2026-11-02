package com.nokoding.deck;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Original, simple vector button glyphs for gamepad hints. Shape style follows the vendor id. */
final class Glyphs {
    private Glyphs() {}

    static final int A = 0, B = 1, X = 2, Y = 3, LB = 4, RB = 5, START = 6, STICK = 7, SELECT = 8;
    private static final String[] LETTERS = {"A", "B", "X", "Y"};
    private static final Path P = new Path();
    private static final RectF R = new RectF();

    static String label(int style, int btn) {
        switch (btn) {
            case LB: return style == InputMode.STYLE_SONY ? "L1" : "LB";
            case RB: return style == InputMode.STYLE_SONY ? "R1" : "RB";
            case START: return style == InputMode.STYLE_SONY ? "OPTIONS" : (style == InputMode.STYLE_XBOX ? "MENU" : "START");
            case SELECT: return style == InputMode.STYLE_SONY ? "CREATE" : (style == InputMode.STYLE_XBOX ? "VIEW" : "SELECT");
            default: return "";
        }
    }

    static float width(int style, int btn, float r, Paint text) {
        if (btn <= Y) return 2 * r;
        if (btn == STICK) return 2.7f * r;
        float old = text.getTextSize();
        text.setTextSize(r * 0.95f);
        float w = text.measureText(label(style, btn)) + r * 1.3f;
        text.setTextSize(old);
        return w;
    }

    /** Draws a glyph with its left edge at x, centered on cy. @return width used. */
    static float draw(Canvas c, int style, int btn, boolean rightStick, float x, float cy, float r, Paint line, Paint text) {
        float old = text.getTextSize();
        Paint.Align oldAlign = text.getTextAlign();
        text.setTextAlign(Paint.Align.CENTER);
        float w;
        if (btn <= Y) {
            float cx = x + r;
            c.drawCircle(cx, cy, r, line);
            if (style == InputMode.STYLE_SONY) {
                float s = r * 0.46f;
                switch (btn) {
                    case A:
                        c.drawLine(cx - s, cy - s, cx + s, cy + s, line);
                        c.drawLine(cx - s, cy + s, cx + s, cy - s, line);
                        break;
                    case B: c.drawCircle(cx, cy, s, line); break;
                    case X: c.drawRect(cx - s * 0.85f, cy - s * 0.85f, cx + s * 0.85f, cy + s * 0.85f, line); break;
                    default:
                        P.reset();
                        P.moveTo(cx, cy - s);
                        P.lineTo(cx + s, cy + s * 0.75f);
                        P.lineTo(cx - s, cy + s * 0.75f);
                        P.close();
                        c.drawPath(P, line);
                        break;
                }
            } else {
                text.setTextSize(r * 1.15f);
                c.drawText(LETTERS[btn], cx, cy - (text.ascent() + text.descent()) / 2f, text);
            }
            w = 2 * r;
        } else if (btn == STICK) {
            float cx = x + 1.35f * r;
            c.drawCircle(cx, cy, r * 0.8f, line);
            R.set(cx - r * 1.3f, cy - r * 1.3f, cx + r * 1.3f, cy + r * 1.3f);
            c.drawArc(R, -150, 280, false, line);
            float ax = cx + (float) Math.cos(Math.toRadians(130)) * r * 1.3f;
            float ay = cy + (float) Math.sin(Math.toRadians(130)) * r * 1.3f;
            c.drawLine(ax, ay, ax + r * 0.45f, ay + r * 0.05f, line);
            c.drawLine(ax, ay, ax + r * 0.1f, ay - r * 0.45f, line);
            text.setTextSize(r * 0.95f);
            c.drawText(rightStick ? "R" : "L", cx, cy - (text.ascent() + text.descent()) / 2f, text);
            w = 2.7f * r;
        } else {
            text.setTextSize(r * 0.95f);
            String s = label(style, btn);
            w = text.measureText(s) + r * 1.3f;
            R.set(x, cy - r * 0.85f, x + w, cy + r * 0.85f);
            c.drawRoundRect(R, r * 0.85f, r * 0.85f, line);
            c.drawText(s, x + w / 2f, cy - (text.ascent() + text.descent()) / 2f, text);
        }
        text.setTextSize(old);
        text.setTextAlign(oldAlign);
        return w;
    }
}
