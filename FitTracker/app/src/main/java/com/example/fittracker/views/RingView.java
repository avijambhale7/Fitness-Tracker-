package com.example.fittracker.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.core.content.ContextCompat;

import com.example.fittracker.R;

import java.util.Locale;

/**
 * Two gradient activity rings drawn on the violet hero card:
 * outer ring = Heart Points (coral), inner ring = Steps (mint).
 */
public class RingView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerArc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerArc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bigText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stepsText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final int heartStart, heartEnd, stepsStart, stepsEnd;

    private float outerFraction;
    private float innerFraction;
    private float animFactor = 1f;
    private int heartPoints;
    private int steps;
    private ValueAnimator animator;

    public RingView(Context context) {
        this(context, null);
    }

    public RingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        heartStart = ContextCompat.getColor(context, R.color.heart_light);
        heartEnd = ContextCompat.getColor(context, R.color.heart_color);
        stepsStart = ContextCompat.getColor(context, R.color.steps_light);
        stepsEnd = ContextCompat.getColor(context, R.color.steps_color);

        for (Paint p : new Paint[]{track, outerArc, innerArc}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
        }
        track.setColor(0x2EFFFFFF);

        bigText.setColor(0xFFFFFFFF);
        bigText.setTextAlign(Paint.Align.CENTER);
        bigText.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));

        labelText.setColor(ContextCompat.getColor(context, R.color.text_on_hero_dim));
        labelText.setTextAlign(Paint.Align.CENTER);
        labelText.setLetterSpacing(0.15f);
        labelText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        stepsText.setColor(stepsStart);
        stepsText.setTextAlign(Paint.Align.CENTER);
        stepsText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    }

    public void setData(int heartPoints, int heartGoal, int steps, int stepGoal, boolean animate) {
        this.heartPoints = heartPoints;
        this.steps = steps;
        outerFraction = heartGoal > 0 ? Math.min(1f, heartPoints / (float) heartGoal) : 0f;
        innerFraction = stepGoal > 0 ? Math.min(1f, steps / (float) stepGoal) : 0f;

        if (animate) {
            if (animator != null) animator.cancel();
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(1100);
            animator.setInterpolator(new DecelerateInterpolator(1.6f));
            animator.addUpdateListener(a -> {
                animFactor = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            if (animator == null || !animator.isRunning()) animFactor = 1f;
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float cx = w / 2f, cy = h / 2f;
        // Gradients start at 12 o'clock (rotated slightly so the round start cap keeps the start colour)
        Matrix m = new Matrix();
        m.setRotate(-95, cx, cy);
        SweepGradient heart = new SweepGradient(cx, cy, heartStart, heartEnd);
        heart.setLocalMatrix(m);
        outerArc.setShader(heart);
        SweepGradient step = new SweepGradient(cx, cy, stepsStart, stepsEnd);
        step.setLocalMatrix(m);
        innerArc.setShader(step);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float size = Math.min(w, h);
        float cx = w / 2f, cy = h / 2f;
        float stroke = size * 0.075f;

        track.setStrokeWidth(stroke);
        outerArc.setStrokeWidth(stroke);
        innerArc.setStrokeWidth(stroke);

        float rOuter = size / 2f - stroke / 2f - 2f;
        float rInner = rOuter - stroke * 1.35f;

        canvas.drawCircle(cx, cy, rOuter, track);
        rect.set(cx - rOuter, cy - rOuter, cx + rOuter, cy + rOuter);
        if (outerFraction > 0) canvas.drawArc(rect, -90, 360 * outerFraction * animFactor, false, outerArc);

        canvas.drawCircle(cx, cy, rInner, track);
        rect.set(cx - rInner, cy - rInner, cx + rInner, cy + rInner);
        if (innerFraction > 0) canvas.drawArc(rect, -90, 360 * innerFraction * animFactor, false, innerArc);

        bigText.setTextSize(size * 0.2f);
        labelText.setTextSize(size * 0.048f);
        stepsText.setTextSize(size * 0.075f);
        canvas.drawText(String.valueOf(Math.round(heartPoints * animFactor)), cx, cy + size * 0.02f, bigText);
        canvas.drawText("HEART PTS", cx, cy + size * 0.1f, labelText);
        canvas.drawText(String.format(Locale.getDefault(), "%,d steps", Math.round(steps * animFactor)),
                cx, cy + size * 0.215f, stepsText);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}
