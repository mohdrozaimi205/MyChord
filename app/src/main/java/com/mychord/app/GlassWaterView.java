package com.mychord.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Latar belakang glassmorphism dengan kesan refleksi air bergerak.
 * Dipaparkan hanya apabila tema iosglass dipilih.
 * Menggunakan gabungan gradient radial bergerak yang memberi kesan
 * cahaya dan bayangan air berkilau di belakang elemen UI.
 */
public class GlassWaterView extends View {
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint[] ripples = new Paint[5];
    private float phase = 0f;
    private ValueAnimator anim;

    public GlassWaterView(Context context) { this(context, null); }
    public GlassWaterView(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public GlassWaterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        for (int i = 0; i < ripples.length; i++) ripples[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(12000);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (anim != null) { anim.cancel(); anim = null; }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        // Latar asas: kelabu kebiruan lembut (iOS Glass)
        bgPaint.setShader(new LinearGradient(0, 0, 0, h,
                0xFFF0F4FF, 0xFFE4E8F0, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, bgPaint);

        // Refleksi air 1: bulatan biru lembut bergerak dari kiri ke kanan
        float cx1 = w * (0.2f + 0.15f * sin(phase, 0));
        float cy1 = h * (0.15f + 0.08f * sin(phase, 0.3f));
        ripples[0].setShader(new RadialGradient(cx1, cy1, w * 0.45f,
                new int[]{0x3007AFFF, 0x1807AFFF, 0x0007AFFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx1, cy1, w * 0.45f, ripples[0]);

        // Refleksi air 2: sinar ungu lembut bergerak turun
        float cx2 = w * (0.75f + 0.12f * sin(phase, 0.5f));
        float cy2 = h * (0.35f + 0.1f * sin(phase, 0.7f));
        ripples[1].setShader(new RadialGradient(cx2, cy2, w * 0.4f,
                new int[]{0x25B4A0FF, 0x10B4A0FF, 0x00B4A0FF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx2, cy2, w * 0.4f, ripples[1]);

        // Refleksi air 3: cahaya putih berkilau (macam pantulan cahaya di air)
        float cx3 = w * (0.5f + 0.2f * sin(phase, 0.2f));
        float cy3 = h * (0.6f + 0.12f * sin(phase, 0.9f));
        ripples[2].setShader(new RadialGradient(cx3, cy3, w * 0.35f,
                new int[]{0x20FFFFFF, 0x10FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx3, cy3, w * 0.35f, ripples[2]);

        // Refleksi air 4: cyan bergerak dari bawah
        float cx4 = w * (0.35f + 0.18f * sin(phase, 0.6f));
        float cy4 = h * (0.8f + 0.08f * sin(phase, 0.4f));
        ripples[3].setShader(new RadialGradient(cx4, cy4, w * 0.38f,
                new int[]{0x2000D4FF, 0x1000D4FF, 0x0000D4FF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx4, cy4, w * 0.38f, ripples[3]);

        // Refleksi air 5: kilasan kecil bergerak pantas
        float cx5 = w * (0.6f + 0.25f * sin(phase * 1.5f, 0.1f));
        float cy5 = h * (0.25f + 0.15f * sin(phase * 1.3f, 0.8f));
        ripples[4].setShader(new RadialGradient(cx5, cy5, w * 0.2f,
                new int[]{0x18FFFFFF, 0x08E0F0FF, 0x00E0F0FF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx5, cy5, w * 0.2f, ripples[4]);

        // Lapisan garis air melintang (simulate water caustics)
        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < 8; i++) {
            float y = h * (0.1f + i * 0.12f) + h * 0.02f * sin(phase, i * 0.15f);
            float alpha = 0.04f + 0.03f * sin(phase, i * 0.2f + 0.5f);
            linePaint.setColor(0x00FFFFFF | ((int)(alpha * 255) << 24));
            linePaint.setStrokeWidth(1.5f + sin(phase, i * 0.1f) * 0.5f);
            float dx = w * 0.05f * sin(phase, i * 0.3f);
            canvas.drawLine(dx, y, w - dx, y, linePaint);
        }
    }

    /** sin dengan fasa offset (0..1 → 0..2π) */
    private static float sin(float phase, float offset) {
        return (float) Math.sin((phase + offset) * Math.PI * 2);
    }
}
