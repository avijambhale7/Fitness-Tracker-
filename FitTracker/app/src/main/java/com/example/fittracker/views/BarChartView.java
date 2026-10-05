package com.example.fittracker.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.core.content.ContextCompat;

import com.example.fittracker.R;

import java.util.Locale;

/**
 * 7-day bar chart with gradient pill bars and a dashed goal line.
 * The last bar (today) is highlighted and labelled with its value.
 */
public class BarChartView extends View {

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint goalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint goalTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path goalPath = new Path();
    private final float dp;

    private float[] values = new float[0];
    private String[] labels = new String[0];
    private float goal;
    private int colorTop, colorBottom;
    private float grow = 1f;
    private ValueAnimator animator;

    public BarChartView(Context context) {
        this(context, null);
    }

    public BarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        int grey = ContextCompat.getColor(context, R.color.text_secondary);
        colorTop = ContextCompat.getColor(context, R.color.steps_light);
        colorBottom = ContextCompat.getColor(context, R.color.steps_color);

        labelPaint.setColor(grey);
        labelPaint.setTextSize(11 * dp);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        todayLabelPaint.set(labelPaint);
        todayLabelPaint.setColor(ContextCompat.getColor(context, R.color.text_primary));
        todayLabelPaint.setTypeface(Typeface.DEFAULT_BOLD);

        valuePaint.setColor(ContextCompat.getColor(context, R.color.text_primary));
        valuePaint.setTextSize(11 * dp);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(Typeface.DEFAULT_BOLD);

        goalPaint.setColor(ContextCompat.getColor(context, R.color.primary));
        goalPaint.setAlpha(120);
        goalPaint.setStyle(Paint.Style.STROKE);
        goalPaint.setStrokeWidth(1.2f * dp);
        goalPaint.setPathEffect(new DashPathEffect(new float[]{5 * dp, 5 * dp}, 0));

        goalTextPaint.setColor(ContextCompat.getColor(context, R.color.primary));
        goalTextPaint.setTextSize(10 * dp);
        goalTextPaint.setTextAlign(Paint.Align.RIGHT);
        goalTextPaint.setTypeface(Typeface.DEFAULT_BOLD);

        emptyPaint.setColor(ContextCompat.getColor(context, R.color.divider));
    }

    public void setData(float[] values, String[] labels, float goal, int colorTop, int colorBottom,
                        boolean animate) {
        this.values = values;
        this.labels = labels;
        this.goal = goal;
        this.colorTop = colorTop;
        this.colorBottom = colorBottom;
        if (animate) {
            if (animator != null) animator.cancel();
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(700);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                grow = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = values.length;
        if (n == 0) return;

        float left = 4 * dp, right = getWidth() - 4 * dp;
        float top = 22 * dp, bottom = getHeight() - 24 * dp;
        float chartH = bottom - top;

        float max = goal;
        for (float v : values) max = Math.max(max, v);
        if (max <= 0) max = 1;
        max *= 1.1f;

        float slot = (right - left) / n;
        float barW = Math.min(slot * 0.5f, 26 * dp);
        float radius = barW / 2f;

        for (int i = 0; i < n; i++) {
            boolean today = i == n - 1;
            float cx = left + slot * (i + 0.5f);

            // Faint full-height track behind every bar
            rect.set(cx - barW / 2, top, cx + barW / 2, bottom);
            emptyPaint.setAlpha(90);
            canvas.drawRoundRect(rect, radius, radius, emptyPaint);

            float bh = values[i] / max * chartH * grow;
            if (values[i] > 0 && bh < barW) bh = barW;
            if (bh > 0) {
                rect.set(cx - barW / 2, bottom - bh, cx + barW / 2, bottom);
                barPaint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                        colorTop, colorBottom, Shader.TileMode.CLAMP));
                barPaint.setAlpha(today ? 255 : 110);
                canvas.drawRoundRect(rect, radius, radius, barPaint);
            }
            if (today && values[i] > 0) {
                canvas.drawText(formatValue(values[i]), cx, bottom - bh - 6 * dp, valuePaint);
            }
            canvas.drawText(labels[i], cx, getHeight() - 6 * dp, today ? todayLabelPaint : labelPaint);
        }

        if (goal > 0) {
            float y = bottom - goal / max * chartH;
            goalPath.reset();
            goalPath.moveTo(left, y);
            goalPath.lineTo(right, y);
            canvas.drawPath(goalPath, goalPaint);
            canvas.drawText("GOAL " + formatValue(goal), right, y - 5 * dp, goalTextPaint);
        }
    }

    private static String formatValue(float v) {
        if (v >= 10000) return String.format(Locale.getDefault(), "%.1fk", v / 1000f);
        return String.format(Locale.getDefault(), "%,d", Math.round(v));
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}
