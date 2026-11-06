/*
 * MeeroMd3Loader — أشكال التحميل الهندسية المتحوّلة (نسخة exteraless / Material 3)
 * =============================================================================
 * يحاكي LoadingIndicator في Material 3 Expressive: شكل مصمت يدور ويتحوّل بين
 * سبعة أشكال هندسية بنفس ترتيب المكتبة الرسمية:
 *
 *   SOFT_BURST → COOKIE_9 → PENTAGON → PILL → SUNNY → COOKIE_4 → OVAL
 *
 * القيم مأخوذة من MaterialShapes.java:
 *   SOFT_BURST 10 رؤوس ناعمة، COOKIE_9 نجمة 9 رؤوس (inner 0.8)،
 *   PENTAGON 5 رؤوس باستدارة 0.172، PILL كبسولة، SUNNY نجمة 8 رؤوس (0.8)،
 *   COOKIE_4 4 رؤوس، OVAL بيضاوي.
 *
 * • اللون: لون الأصل من الثيم (progressColor) — كما في exteraless.
 * • كل أشكال الرسوم محسوبة مرة واحدة في blocs ثابت (لا تخصيص في حلقة الرسم).
 * • لا يُستخدم إطلاقاً في الحوار الأوسط بالمحادثة (ذاك يبقى على القوسين).
 */
package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import org.telegram.messenger.AndroidUtilities;

public class MeeroMd3Loader {

    /** عدد عيّنات نصف القطر لكل شكل (2 درجة لكل خانة). */
    private static final int BINS = 180;
    private static final int SHAPE_COUNT = 7;
    private static final float TAU = (float) (Math.PI * 2);

    // ===== توقيت مطابق حرفياً لمكتبة Material 3 =====
    // LoadingIndicatorAnimatorDelegate: DURATION_PER_SHAPE_IN_MS = 650
    // CONSTANT_ROTATION_PER_SHAPE_DEGREES = 50، EXTRA_ROTATION_PER_SHAPE_DEGREES = 90
    private static final float DURATION_PER_SHAPE_MS = 650f;
    private static final float CONSTANT_ROTATION_PER_SHAPE = 50f;
    private static final float EXTRA_ROTATION_PER_SHAPE = 90f;

    // ==================== الأشكال ====================

    private static final float[][] PROFILES = new float[SHAPE_COUNT][BINS];

    static {
        PROFILES[0] = normalize(profileFromPolygon(roundedShape(starPoints(10, 0.758f), 0.30f, 10, 10)));
        PROFILES[1] = normalize(profileFromPolygon(roundedShape(starPoints(9, 0.80f), 0.62f, 10, 10)));
        PROFILES[2] = normalize(profileFromPolygon(roundedShape(polyPoints(5), 0.172f, 10, 12)));
        PROFILES[3] = normalize(profileFromPolygon(pillPoints(1.0f, 0.62f)));
        PROFILES[4] = normalize(profileFromPolygon(roundedShape(starPoints(8, 0.80f), 0.20f, 10, 10)));
        PROFILES[5] = normalize(profileFromPolygon(roundedShape(starPoints(4, 0.70f), 0.70f, 10, 10)));
        PROFILES[6] = normalize(ellipseProfile(1.0f, 0.78f));
    }

    // ==================== الحالة ====================

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float[] radii = new float[BINS];

    private long lastFrame;
    private boolean started;
    private float rotation;
    private float morph;

    private static Boolean enabledOverride;

    public MeeroMd3Loader() {
        paint.setStyle(Paint.Style.FILL);
    }

    // ==================== مفتاح التشغيل ====================

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
            return false;   // IMPORTANT: عند أي شك نرجع للشكل الأصلي
        }
    }

    /** متوافق مع الاستدعاءات السابقة — لم يعد مستخدماً. */
    public void setStyle(int style) { }

    public static void setDefaultStyle(int style) { }

    public static int getDefaultStyle() { return 0; }

    // ==================== الرسم ====================

    /**
     * @param canvas   لوحة الرسم
     * @param cx       المركز الأفقي
     * @param cy       المركز الرأسي
     * @param diameter قطر الشكل بالبكسل
     * @param color    لون الأصل من الثيم
     * @param alpha    شفافية 0..255
     */
    public void draw(Canvas canvas, float cx, float cy, float diameter, int color, int alpha) {
        final long now = System.currentTimeMillis();
        final long dt = started ? Math.min(64L, Math.max(0L, now - lastFrame)) : 0L;
        lastFrame = now;
        started = true;

        final float radius = diameter / 2f;
        if (radius <= 0f) {
            return;
        }

        // ------ الحركة: نفس معادلة الدوران في المكتبة ------
        // الأساس = (50 + 90) × رقم الشكل، + 50 × نسبة الزمن داخل الشكل،
        // + 90 × تقدّم التحوّل. ثم % 360.
        morph += dt / DURATION_PER_SHAPE_MS;
        if (morph > SHAPE_COUNT) {
            morph -= SHAPE_COUNT;
        }

        final int i0 = ((int) Math.floor(morph) % SHAPE_COUNT + SHAPE_COUNT) % SHAPE_COUNT;
        final int i1 = (i0 + 1) % SHAPE_COUNT;
        float t = morph - (float) Math.floor(morph);
        t = t * t * (3f - 2f * t);            // smoothstep — تحوّل ناعم

        rotation = ((CONSTANT_ROTATION_PER_SHAPE + EXTRA_ROTATION_PER_SHAPE) * i0
                + CONSTANT_ROTATION_PER_SHAPE * t
                + EXTRA_ROTATION_PER_SHAPE * t) % 360f;

        final float[] a = PROFILES[i0];
        final float[] b = PROFILES[i1];
        for (int k = 0; k < BINS; k++) {
            radii[k] = a[k] + (b[k] - a[k]) * t;
        }

        // ------ بناء المسار ------
        path.rewind();
        final float rad = (float) Math.toRadians(rotation);
        for (int k = 0; k < BINS; k++) {
            final float th = k * TAU / BINS + rad;
            final float r = radii[k] * radius;
            final float x = cx + (float) Math.cos(th) * r;
            final float y = cy + (float) Math.sin(th) * r;
            if (k == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();

        paint.setColor(color);
        paint.setAlpha(Math.max(0, Math.min(255, alpha)));
        canvas.drawPath(path, paint);
    }

    // ==================== هندسة الأشكال (نفس خوارزمية المكتبة) ====================

    /** رؤوس نجمة/قرص بعدد رؤوس n ونسبة نصف قطر داخلي. */
    private static float[][] starPoints(int n, float inner) {
        float[][] pts = new float[n * 2][2];
        for (int i = 0; i < n * 2; i++) {
            double ang = -Math.PI / 2 + i * Math.PI / n;
            float r = (i % 2 == 0) ? 1f : inner;
            pts[i][0] = (float) Math.cos(ang) * r;
            pts[i][1] = (float) Math.sin(ang) * r;
        }
        return pts;
    }

    /** مضلّع منتظم بعدد أضلاع n. */
    private static float[][] polyPoints(int n) {
        float[][] pts = new float[n][2];
        for (int i = 0; i < n; i++) {
            double ang = -Math.PI / 2 + i * TAU / n;
            pts[i][0] = (float) Math.cos(ang);
            pts[i][1] = (float) Math.sin(ang);
        }
        return pts;
    }

    /** كبسولة: مستطيل بنصف ارتفاع h/2 ونصف قطر زاوية = h/2. */
    private static float[][] pillPoints(float w, float h) {
        final int steps = 40;
        final float r = h / 2f, half = w / 2f;
        final float c0 = half - r, c1 = -half + r;
        float[][] pts = new float[(steps + 1) * 2][2];
        int idx = 0;
        for (int i = 0; i <= steps; i++) {
            double a = -Math.PI / 2 + i * Math.PI / steps;
            pts[idx][0] = c0 + r * (float) Math.cos(a);
            pts[idx][1] = r * (float) Math.sin(a);
            idx++;
        }
        for (int i = 0; i <= steps; i++) {
            double a = Math.PI / 2 + i * Math.PI / steps;
            pts[idx][0] = c1 + r * (float) Math.cos(a);
            pts[idx][1] = r * (float) Math.sin(a);
            idx++;
        }
        return pts;
    }

    /**
     * يستدير زوايا المضلّع: قوس عند كل رأس + نقاط على الحواف المستقيمة.
     * الخطوط المستقيمة ضرورية، بدونها تُسقَط الحواف ويبدو الشكل دائرة.
     */
    private static float[][] roundedShape(float[][] pts, float cornerFrac, int arcSteps, int lineSteps) {
        final int n = pts.length;
        float[][] a = new float[n][2];
        float[][] b = new float[n][2];
        for (int i = 0; i < n; i++) {
            float vx = pts[i][0], vy = pts[i][1];
            int pi = (i - 1 + n) % n, ni = (i + 1) % n;
            float px = pts[pi][0], py = pts[pi][1];
            float nx = pts[ni][0], ny = pts[ni][1];
            float lp = (float) Math.hypot(px - vx, py - vy);
            float ln = (float) Math.hypot(nx - vx, ny - vy);
            float cut = Math.min(cornerFrac * Math.min(lp, ln), Math.min(lp, ln) * 0.49f);
            a[i][0] = vx + (px - vx) / lp * cut;
            a[i][1] = vy + (py - vy) / lp * cut;
            b[i][0] = vx + (nx - vx) / ln * cut;
            b[i][1] = vy + (ny - vy) / ln * cut;
        }
        final int perCorner = (arcSteps + 1) + (lineSteps - 1);
        float[][] out = new float[n * perCorner][2];
        int idx = 0;
        for (int i = 0; i < n; i++) {
            float vx = pts[i][0], vy = pts[i][1];
            int next = (i + 1) % n;
            // قوس الزاوية: a → v → b (منحنى تربيعي)
            for (int s = 0; s <= arcSteps; s++) {
                float t = s / (float) arcSteps, it = 1f - t;
                out[idx][0] = it * it * a[i][0] + 2 * it * t * vx + t * t * b[i][0];
                out[idx][1] = it * it * a[i][1] + 2 * it * t * vy + t * t * b[i][1];
                idx++;
            }
            // الحافة المستقيمة: b[i] → a[next]
            for (int s = 1; s < lineSteps; s++) {
                float t = s / (float) lineSteps;
                out[idx][0] = b[i][0] + (a[next][0] - b[i][0]) * t;
                out[idx][1] = b[i][1] + (a[next][1] - b[i][1]) * t;
                idx++;
            }
        }
        return out;
    }

    /** يحوّل نقاط الشكل إلى دالة نصف قطر r(θ). */
    private static float[] profileFromPolygon(float[][] poly) {
        float[] prof = new float[BINS];
        for (int i = 0; i < BINS; i++) {
            prof[i] = -1f;
        }
        for (float[] p : poly) {
            float r = (float) Math.hypot(p[0], p[1]);
            if (r <= 0f) {
                continue;
            }
            float th = (float) Math.atan2(p[1], p[0]);
            if (th < 0) {
                th += TAU;
            }
            float f = th / TAU * BINS;
            int b0 = ((int) Math.floor(f)) % BINS;
            int b1 = (b0 + 1) % BINS;
            if (r > prof[b0]) prof[b0] = r;
            if (r > prof[b1]) prof[b1] = r;
        }
        // سدّ الخانات الفارغة بالتدرّج بين الجارتين
        for (int i = 0; i < BINS; i++) {
            if (prof[i] >= 0f) {
                continue;
            }
            int l = i - 1, r = i + 1, guard = 0;
            while (prof[(l + BINS) % BINS] < 0f && guard++ < BINS) l--;
            guard = 0;
            while (prof[(r + BINS) % BINS] < 0f && guard++ < BINS) r++;
            float av = prof[(l + BINS) % BINS], bv = prof[(r + BINS) % BINS];
            prof[i] = av + (bv - av) * ((i - l) / (float) Math.max(1, r - l));
        }
        return prof;
    }

    private static float[] ellipseProfile(float ax, float ay) {
        float[] prof = new float[BINS];
        for (int i = 0; i < BINS; i++) {
            float th = i * TAU / BINS;
            prof[i] = 1f / (float) Math.hypot(Math.cos(th) / ax, Math.sin(th) / ay);
        }
        return prof;
    }

    /** توحيد المقياس: كل شكل يلمس نصف قطر 1 عند أوسع نقطة. */
    private static float[] normalize(float[] prof) {
        float max = 0f;
        for (int i = 0; i < BINS; i++) {
            if (prof[i] > max) max = prof[i];
        }
        if (max > 0f) {
            for (int i = 0; i < BINS; i++) {
                prof[i] /= max;
            }
        }
        return prof;
    }

    // ==================== استدعاء عام لأي محرّك آخر ====================

    /** نسخة مشتركة للرسم في المحرّكات الأخرى (Drawable/View مخصّص). */
    private static MeeroMd3Loader shared;

    /**
     * يرسم أشكال Material 3 في أي مكان آخر غير RadialProgressView.
     * @return false إذا كان المفتاح مطفأً (فلا يُرسم شيء ويبقى الأصل)
     */
    public static boolean drawInto(Canvas canvas, float cx, float cy,
                                   float diameter, int color, int alpha) {
        if (!isEnabled() || diameter <= 0f) {
            return false;
        }
        if (shared == null) {
            shared = new MeeroMd3Loader();
        }
        shared.draw(canvas, cx, cy, diameter, color, alpha);
        return true;
    }
}
