package de.kaipressmar.a52srepair;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Lightweight timeline chart with no external UI dependency. */
final class HealthHistoryChart extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<HealthHistoryStore.Entry> entries = Collections.emptyList();

    private final int green = AppPalette.SUCCESS;
    private final int amber = AppPalette.WARNING;
    private final int red = AppPalette.ERROR;
    private final int grey = AppPalette.NEUTRAL;
    private final int ink = AppPalette.MUTED;
    private final int track = AppPalette.SURFACE_TINT;

    HealthHistoryChart(Context context) {
        super(context);
        setMinimumHeight(dp(132));
        setContentDescription("Verlauf der Bluetooth-Telefonie-Prüfungen");
        textPaint.setTextSize(sp(11));
        textPaint.setColor(ink);
    }

    void setEntries(List<HealthHistoryStore.Entry> value) {
        entries =
                value == null
                        ? Collections.emptyList()
                        : HealthHistoryStore.recent(value, 36);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desired = dp(142);
        int resolved = resolveSize(desired, heightMeasureSpec);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(resolved, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float left = dp(4);
        float right = getWidth() - dp(4);
        float top = dp(20);
        float bottom = getHeight() - dp(34);
        float height = bottom - top;

        paint.setColor(track);
        canvas.drawRoundRect(new RectF(left, top, right, bottom), dp(10), dp(10), paint);

        if (entries.isEmpty()) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(
                    "Noch keine Verlaufsdaten",
                    getWidth() / 2f,
                    top + height / 2f + dp(4),
                    textPaint);
            return;
        }

        float gap = dp(3);
        float usableWidth = right - left;
        float barWidth = Math.max(dp(4), (usableWidth - gap * (entries.size() - 1)) / entries.size());
        float x = left;

        for (HealthHistoryStore.Entry entry : entries) {
            paint.setColor(colorFor(entry));
            float fraction = heightFraction(entry);
            float barTop = bottom - Math.max(dp(10), height * fraction);
            canvas.drawRoundRect(
                    new RectF(x, barTop, Math.min(right, x + barWidth), bottom),
                    dp(4),
                    dp(4),
                    paint);
            x += barWidth + gap;
            if (x > right) break;
        }

        SimpleDateFormat time = new SimpleDateFormat("HH:mm", Locale.GERMANY);
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(
                time.format(new Date(entries.get(0).timestamp)),
                left,
                getHeight() - dp(10),
                textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(
                time.format(new Date(entries.get(entries.size() - 1).timestamp)),
                right,
                getHeight() - dp(10),
                textPaint);
    }

    private int colorFor(HealthHistoryStore.Entry entry) {
        switch (entry.state) {
            case HEALTHY:
                return green;
            case SUSPECT_ROUTING:
            case HFP_CONNECTED_NO_SCO:
            case CALL_WITHOUT_SCO:
                return amber;
            case ERROR:
                return red;
            case BLUETOOTH_OFF:
            case PERMISSION_REQUIRED:
                return red;
            case IDLE:
            default:
                return grey;
        }
    }

    private float heightFraction(HealthHistoryStore.Entry entry) {
        switch (entry.state) {
            case HEALTHY:
                return entry.inCommunication ? 0.95f : 0.58f;
            case SUSPECT_ROUTING:
                return 0.82f;
            case HFP_CONNECTED_NO_SCO:
            case CALL_WITHOUT_SCO:
                return 0.70f;
            case ERROR:
            case BLUETOOTH_OFF:
            case PERMISSION_REQUIRED:
                return 0.55f;
            case IDLE:
            default:
                return 0.34f;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private float sp(int value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
