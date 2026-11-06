/*
 * MeeroMd3Loader — مؤشر تحميل Material 3 (نسخة exteraless) لجميع مواضع التحميل
 * =============================================================================
 * يُرسم داخل RadialProgressView نفسها، فلا يحتاج أي تعديل في المواضع الخمسة
 * والأربعين التي تستخدمها (الإعدادات، الملف الشخصي، إرسال الملفات، الصور،
 * جهات الاتصال، المجموعات، البوتات، واجهة الويب، القصص ... إلخ).
 *
 * المبدأ المطبَّق من exteraless (M3CircularProgress):
 *   • قوس واحد متحرّك (يمتد ويقصر) — CircularProgressIndicator
 *   • فوقه مسار خلفي (track) بشفافية 20%، بينه وبين القوس فجوة 2dp
 *   • أطراف مستديرة، سماكة تُشتق من حجم المؤشر
 *   • اللون يأتي من الثيم (key_progressCircle) — أي «بلون الأصل»
 *   • الدوران متصل بلا إعادة ضبط (لا نفضة)
 *
 * ما لا يلمسه هذا الملف إطلاقاً:
 *   • الحوار الأوسط في المحادثة (AlertDialog.ALERT_TYPE_SPINNER): يبقى على
 *     القوسين البنفسجي/السماوي القديمين عبر setUseCustomSpinner(true)
 *   • الدوائر المرسومة يدوياً في ChatActivity / ChatMessageCell / SharedMedia
 *   • مؤشر iOS في MediaActionDrawable (مفتاح meeroIosLoading)
 *   • وضع التقدّم (progress) — يبقى كما هو تماماً
 */
package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import org.telegram.messenger.AndroidUtilities;

public class MeeroMd3Loader {

    /** القوس المتحرّك الدائري. */
    public static final int STYLE_CIRCULAR = 0;
    /** نفس القوس لكن بحافة موجية (M3 Expressive) — اختياري. */
    public static final int STYLE_WAVY = 1;

    private static final float ROTATION_PERIOD_MS = 1333f;   // دورة كاملة ~1.3s (M3)
    private static final float HEAD_GROW_MS = 900f;          // امتداد القوس
    private static final float HEAD_SHRINK_MS = 900f;        // تقلّص القوس
    private static final float MIN_SWEEP = 12f;              // أقصر قوس
    private static final float MAX_SWEEP = 290f;             // أطول قوس

    private static Boolean enabledOverride;
    private static int defaultStyle = STYLE_CIRCULAR;

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private int style = STYLE_CIRCULAR;
    private boolean wavy;
    private float waveAmplitude, waveLength, waveSpeed;

    private long lastFrame;
    private boolean started;
    private float rotation;
    private float sweep = MIN_SWEEP;
    private float phase;

    public MeeroMd3Loader() {
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setStrokeJoin(Paint.Join.ROUND);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    // ==================== الإعدادات العامة ====================

    /** مفتاح التشغيل العام (يُقرأ من NekoConfig.meeroMd3Loading). */
    public static void setEnabled(Boolean value) {
        enabledOverride = value;
    }

    public static boolean isEnabled() {
        if (enabledOverride != null) {
            return enabledOverride;
        }
        try {
            return tw.nekomimi.nekogram.NekoConfig.meeroMd3Loading.Bool();
        } catch (Throwable ignore) {
            return true;
        }
    }

    public static void setDefaultStyle(int value) {
        defaultStyle = value;
    }

    public static int getDefaultStyle() {
        return defaultStyle;
    }

    // ==================== حالة الكائن ====================

    public void setStyle(int value) {
        style = value == STYLE_WAVY ? STYLE_WAVY : STYLE_CIRCULAR;
        wavy = style == STYLE_WAVY;
    }

    public void setWavy(boolean value) {
        wavy = value;
    }

    /** سماكة القلم تُشتق من قطر المؤشر: 11% بحدود 2..5dp. */
    public static float strokeWidthFor(float diameterPx) {
        float w = diameterPx * 0.11f;
        float min = AndroidUtilities.dpf2(2f);
        float max = AndroidUtilities.dpf2(5f);
        if (w < min) w = min;
        if (w > max) w = max;
        return w;
    }

    // ==================== الرسم ====================

    /**
     * @param canvas    لوحة الرسم
     * @param cx        مركز أفقي
     * @param cy        مركز رأسي
     * @param diameter  قطر المؤشر بالبكسل (يشمل سماكة الحد)
     * @param color     لون الأصل (progressColor)
     * @param alpha     شفافية 0..255
     */
    public void draw(Canvas canvas, float cx, float cy, float diameter, int color, int alpha) {
        final long now = System.currentTimeMillis();
        final long dt = started ? Math.min(64L, Math.max(0L, now - lastFrame)) : 0L;
        lastFrame = now;
        started = true;

        final float stroke = strokeWidthFor(diameter);
        final float radius = Math.max(0f, diameter / 2f - stroke / 2f);
        if (radius <= 0f) {
            return;
        }

        // ------ الحركة ------
        rotation += 360f * dt / ROTATION_PERIOD_MS;
        if (rotation > 100000f) {
            rotation -= 100000f;
        }
        phase += dt;
        if (phase > HEAD_GROW_MS + HEAD_SHRINK_MS) {
            phase -= HEAD_GROW_MS + HEAD_SHRINK_MS;
        }
        final float t = phase <= HEAD_GROW_MS
                ? phase / HEAD_GROW_MS
                : 1f - (phase - HEAD_GROW_MS) / HEAD_SHRINK_MS;
        final float eased = t * t * (3f - 2f * t);                      // smoothstep
        sweep = MIN_SWEEP + (MAX_SWEEP - MIN_SWEEP) * Math.max(0f, Math.min(1f, eased));

        // المقطع: نترك فجوة 2dp بين طرفي القوس والمسار
        final float gapDeg = (float) Math.toDegrees(AndroidUtilities.dpf2(2f) / radius);
        final float start = rotation;
        final float rest = 360f - sweep - gapDeg * 2f;

        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);

        final int alphaClamped = Math.max(0, Math.min(255, alpha));

        // ------ المسار الخلفي (20% من لون الأصل) ------
        if (rest > 0.5f) {
            trackPaint.setStrokeWidth(stroke);
            trackPaint.setColor(color);
            trackPaint.setAlpha((int) (alphaClamped * 0.20f));
            canvas.drawArc(oval, start + sweep + gapDeg, rest, false, trackPaint);
        }

        // ------ القوس ------
        arcPaint.setStrokeWidth(stroke);
        arcPaint.setColor(color);
        arcPaint.setAlpha(alphaClamped);
        if (wavy && waveAmplitude > 0f && waveLength > 0f) {
            drawWavyArc(canvas, cx, cy, radius, start, sweep);
        } else {
            canvas.drawArc(oval, start, sweep, false, arcPaint);
        }
    }

    /** حافة موجية (M3 Expressive): انحراف جيبي على طول القوس. */
    private void drawWavyArc(Canvas canvas, float cx, float cy, float radius,
                             float startAngle, float sweepAngle) {
        final int steps = Math.max(24, Math.min(160, (int) (Math.abs(sweepAngle))));
        final double lengthPx = Math.abs(sweepAngle) / 180.0 * Math.PI * radius;
        final float timePhase = waveSpeed <= 0f
                ? 0f
                : (System.currentTimeMillis() % 100000L) / 1000f * waveSpeed;

        final android.graphics.Path path = new android.graphics.Path();
        for (int i = 0; i <= steps; i++) {
            final float k = i / (float) steps;
            final float angle = startAngle + sweepAngle * k;
            final float along = (float) (lengthPx * k) + timePhase;
            final float offset = (float) (waveAmplitude *
                    Math.sin(2.0 * Math.PI * along / waveLength));
            final double rad = Math.toRadians(angle);
            final float r = radius + offset;
            final float x = cx + (float) (r * Math.cos(rad));
            final float y = cy + (float) (r * Math.sin(rad));
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, arcPaint);
    }
}
