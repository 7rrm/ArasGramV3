package tw.nekomimi.nekogram.music;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import app.exteraless.player.CoverImage;
import app.exteraless.player.PlayerArt;
import app.exteraless.player.PlayerColors;
import app.exteraless.player.PlayerIcon;
import app.exteraless.player.PlayerStyle;

/**
 * MeeroX v255 — فقاعة المشغّل الدائري في قائمة المحادثات.
 *
 * <p>الفكرة منقولة من زر المكالمة العائم في المحادثات:
 * <ul>
 *   <li>دائرة عائمة قابلة للسحب (مكانها ليس ثابتاً — يُحفظ بين الجلسات).</li>
 *   <li>أمواج متحركة تنتشر خلفها باستمرار.</li>
 *   <li>حلقة تقدّم حول الأيقونة تُظهر موضع الأغنية.</li>
 *   <li>موسيقى = متوقّف/يعمل · ✕ = القائمة مفتوحة.</li>
 *   <li>الضغط يفتح قائمة صغيرة (غلاف + اسم الأغنية + الفنان + السابق/إيقاف/التالي)
 *       على الجهة المقابلة مثل قائمة المكالمة تماماً، والضغط مرة أخرى يغلقها.</li>
 *   <li>الأزرق عند التوقّف، ولون الإعدادات المختار (الثيم أو الغلاف) عند التشغيل.</li>
 * </ul>
 *
 * <p>لا تشغيل ولا إيقاف مباشر من الدائرة نفسها — الضغط يفتح القائمة فقط.
 */
public class MeeroMusicBubble extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    /** قطر الدائرة — أصغر من زر المكالمة (56dp). */
    public static final int SIZE_DP = 48;
    private static final int PANEL_WIDTH_DP = 168;

    private static final int STATE_HIDDEN = 0;
    private static final int STATE_VISIBLE = 1;

    private final int currentAccount;
    private final BubbleView bubble;
    private final PanelView panel;

    /** إزاحة الدائرة عن الزاوية اليمنى السفلية — يُحفظ بين الجلسات. */
    private float posX = Float.NaN;
    private float posY = Float.NaN;

    private MessageObject current;
    private int seed;
    private boolean dark;
    private PlayerColors colors;

    private int state = STATE_HIDDEN;
    private boolean panelOpen;
    private float appearProgress;

    private float panelProgress;
    private ValueAnimator panelAnimator;
    private ValueAnimator appearAnimator;

    private boolean dragArmed;
    private boolean dragging;
    /** الهامش السفلي المحسوب (تبويبات + شريط تنقّل) — يُستعمل في الموضع الافتراضي. */
    private int bottomInsetDp;
    private float downRawX, downRawY, downX, downY;
    private int touchSlop;

    /** يستدعى عند تغيّر حجم/موضع الفقاعة حتى يعيد المضيف تثبيتها. */
    private Runnable onLayoutChanged;

    public MeeroMusicBubble(Context context, int currentAccount) {
        super(context);
        this.currentAccount = currentAccount;
        setClipChildren(false);
        setClipToPadding(false);

        bubble = new BubbleView(context);
        addView(bubble, LayoutHelper.createFrame(SIZE_DP, SIZE_DP));

        panel = new PanelView(context);
        panel.setVisibility(GONE);
        panel.setAlpha(0f);
        addView(panel, LayoutHelper.createFrame(PANEL_WIDTH_DP, LayoutHelper.WRAP_CONTENT));

        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        // v255 fix3: الحاوية صارت بحجم الشاشة، فيجب ألا تعترض أي لمس —
        // اللمس يصل للدائرة والقائمة فقط، وما عداهما يمرّ للقائمة تحتهما.
        setClickable(false);
        setFocusable(false);
        setVisibility(GONE);
    }

    /**
     * v255 fix3: الطبقة السفلية الشفافة لا تستهلك اللمس. بدون هذا تصبح
     * طبقة بحجم الشاشة حاجزاً يمنع كل قوائم المحادثات من الاستجابة.
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // لا نسحب اللمس من الأبناء أبداً — الدائرة والقائمة تحتاجهما
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // اللمس خارج الدائرة/القائمة: أغلق القائمة ومرّر الحدث للأب
        if (panelOpen) {
            closePanel(true);
        }
        return false;
    }

    /**
     * v255 fix4: بما أن الطبقة تغطي الشاشة، نحسب أين وقع اللمس بأنفسنا
     * ونوجّهه للدائرة أو للقائمة. هذا يضمن السحب حتى لو تعامل Android
     * مع الطبقة كحاجز لمس.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (bubble == null) {
            return super.dispatchTouchEvent(ev);
        }
        final float x = ev.getX();
        final float y = ev.getY();
        final float bx = bubble.getX();
        final float by = bubble.getY();
        final int bs = bubble.getWidth() > 0 ? bubble.getWidth() : dp(SIZE_DP);
        final float touch = dp(16); // منطقة لمس أوسع قليلاً من الدائرة
        final boolean inBubble = x >= bx - touch && x <= bx + bs + touch
                && y >= by - touch && y <= by + bs + touch;

        if (!inBubble && !panelOpen) {
            // لا شيء تحت الإصبع: لا تعترض، مرّر للقائمة تحتها
            return false;
        }
        // وجّه الحدث للدائرة نفسها
        final MotionEvent local = MotionEvent.obtain(ev);
        local.offsetLocation(-bx, -by);
        boolean handled = bubble.dispatchTouchEvent(local);
        local.recycle();
        return handled || super.dispatchTouchEvent(ev);
    }

    public void setOnLayoutChanged(Runnable runnable) {
        onLayoutChanged = runnable;
    }

    // ======================= دورة الحياة =======================

    public void attach() {
        NotificationCenter.getInstance(currentAccount).addObserver(this,
                NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).addObserver(this,
                NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).addObserver(this,
                NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this,
                NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this,
                NotificationCenter.audioInfoLoaded);
        refresh(false);
    }

    public void detach() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this,
                NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).removeObserver(this,
                NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).removeObserver(this,
                NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this,
                NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this,
                NotificationCenter.audioInfoLoaded);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            bubble.invalidate();
            return;
        }
        refresh(true);
    }

    // ======================= الحالة =======================

    private void refresh(boolean animated) {
        MessageObject mo = MediaController.getInstance().getPlayingMessageObject();
        if (mo == null || !mo.isMusic() || mo.getId() == 0) {
            mo = null;
        }
        current = mo;
        if (mo == null) {
            closePanel(false);
            setState(STATE_HIDDEN, animated);
            return;
        }
        boolean themeDarkNow = PlayerColors.isDark(null);
        int themeSeed = PlayerColors.fallbackSeed(null);
        if (themeDarkNow != dark || themeSeed != seed) {
            dark = themeDarkNow;
            seed = themeSeed;
        }
        colors = resolveColors(mo);
        panel.bind(mo, colors);
        bubble.invalidate();
        setState(STATE_VISIBLE, animated);
    }

    /**
     * لون المشغّل: أزرق عند التوقّف، ولون الإعدادات المختار عند التشغيل.
     * الإعدادات إما «لون الغلاف» أو «لون الثيم» — وهي نفس إعدادات المشغّل.
     */
    private PlayerColors resolveColors(MessageObject mo) {
        boolean paused = MediaController.getInstance().isMessagePaused();
        if (paused) {
            final int blue = 0xff3390EC;
            return PlayerColors.fromSeed(blue, dark);
        }
        final int argb = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader);
        int chosenSeed;
        if (PlayerStyle.themeColors()) {
            // «لون الثيم» مختار ⇒ لون الثيم
            chosenSeed = argb;
        } else {
            // «لون الغلاف» مختار ⇒ لون غلاف الأغنية (يتبع الغلاف)
            Integer cached = PlayerArt.cachedSeed(mo);
            chosenSeed = cached != null ? cached : PlayerColors.fallbackSeed(null);
        }
        if (chosenSeed == 0) {
            chosenSeed = argb;
        }
        return PlayerColors.fromSeed(chosenSeed, dark);
    }

    private void setState(int newState, boolean animated) {
        if (state == newState) {
            return;
        }
        state = newState;
        if (appearAnimator != null) {
            appearAnimator.cancel();
            appearAnimator = null;
        }
        if (!animated) {
            appearProgress = newState == STATE_VISIBLE ? 1f : 0f;
            applyAppear();
            return;
        }
        float from = appearProgress;
        float to = newState == STATE_VISIBLE ? 1f : 0f;
        appearAnimator = ValueAnimator.ofFloat(from, to);
        appearAnimator.setDuration(220);
        appearAnimator.setInterpolator(org.telegram.ui.Components.CubicBezierInterpolator.EASE_OUT);
        appearAnimator.addUpdateListener(a -> {
            appearProgress = (float) a.getAnimatedValue();
            applyAppear();
        });
        appearAnimator.start();
    }

    private void applyAppear() {
        setVisibility(appearProgress > 0f ? VISIBLE : GONE);
        // v255 fix4: ثبّت الموضع فور الظهور — قبل هذا كانت الدائرة تظهر
        // في الزاوية العليا حتى يصل أول استدعاء من المضيف.
        layoutBubble();
        bubble.setAlpha(appearProgress);
        float s = 0.7f + 0.3f * appearProgress;
        bubble.setScaleX(s);
        bubble.setScaleY(s);
        if (onLayoutChanged != null) {
            onLayoutChanged.run();
        }
    }

    // ======================= الموضع (قابل للسحب) =======================

    /** يُستدعى من المضيف بعد حساب حدود المنطقة المتاحة (فوق شريط التبويبات). */
    public void place(int hostWidth, int hostHeight, int bottomInset) {
        final int size = dp(SIZE_DP);
        bottomInsetDp = (int) (bottomInset / getResources().getDisplayMetrics().density);
        if (Float.isNaN(posX) || Float.isNaN(posY)) {
            posX = hostWidth - size - dp(16);
            posY = hostHeight - size - bottomInset - dp(78);
        }
        posX = clamp(posX, 0, Math.max(0, hostWidth - size));
        posY = clamp(posY, 0, Math.max(0, hostHeight - size));
        layoutBubble();
        layoutPanel(hostWidth, hostHeight);
    }

    /** موضع الدائرة على الشاشة — لتحديد جهة القائمة. */
    public float[] screenCenter() {
        int[] loc = new int[2];
        bubble.getLocationInWindow(loc);
        return new float[]{loc[0] + bubble.getWidth() / 2f, loc[1] + bubble.getHeight() / 2f};
    }

    public int bubbleSize() {
        return dp(SIZE_DP);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // أول مقاس حقيقي للطبقة ⇒ احسب الموضع الافتراضي فوراً
        if (w > 0 && h > 0) {
            layoutBubble();
            layoutPanel(w, h);
        }
    }

    private void layoutBubble() {
        if (!(bubble.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) bubble.getLayoutParams();

        // v255 fix: كانت posX/posY تبقى NaN إذا لم تُستدعَ place() بأبعاد
        // صحيحة، وMath.round(NaN) = 0 ⇒ الدائرة في الزاوية العليا، و
        // NaN + dx = NaN ⇒ السحب لا يعمل أبداً. الآن نحسب موضعاً افتراضياً
        // من مقاس الأب مباشرة (أسفل يمين، فوق شريط التبويبات).
        if (Float.isNaN(posX) || Float.isNaN(posY) || Float.isInfinite(posX) || Float.isInfinite(posY)) {
            View parent = (View) getParent();
            final int pw = parent != null && parent.getWidth() > 0 ? parent.getWidth() : getResources().getDisplayMetrics().widthPixels;
            final int ph = parent != null && parent.getHeight() > 0 ? parent.getHeight() : getResources().getDisplayMetrics().heightPixels;
            final int size = dp(SIZE_DP);
            posX = pw - size - dp(16);
            posY = ph - size - dp(bottomInsetDp) - dp(78);
        }

        View parent = (View) getParent();
        if (parent != null && parent.getWidth() > 0 && parent.getHeight() > 0) {
            posX = clamp(posX, 0, Math.max(0, parent.getWidth() - getWidth()));
            posY = clamp(posY, 0, Math.max(0, parent.getHeight() - getHeight()));
        }

        lp.leftMargin = Math.round(posX);
        lp.topMargin = Math.round(posY);
        bubble.setLayoutParams(lp);
        // v255 fix4: setLayoutParams لا يُنفَّذ إلا بعد دورة تخطيط كاملة،
        // وكانت الدائرة تبقى في (0,0) — أعلى الشاشة كما في الصورة.
        // setX/setY تحدّد الموضع فعلياً في أي وقت، فتُستعمل مع المارجن.
        bubble.setX(posX);
        bubble.setY(posY);
    }

    /**
     * القائمة تتبع الجهة مثل قائمة المكالمة:
     * إذا كانت الدائرة يمين الشاشة تُفتح القائمة على اليسار، وإذا كانت يساراً
     * تُفتح على اليمين، وإذا كانت في المنتصف تُفتح فوقها.
     */
    private void layoutPanel(int hostWidth, int hostHeight) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) panel.getLayoutParams();
        int pw = dp(PANEL_WIDTH_DP);
        int ph = panel.getMeasuredHeight() > 0 ? panel.getMeasuredHeight() : dp(148);
        float centerX = posX + dp(SIZE_DP) / 2f;
        float third = hostWidth / 3f;

        if (centerX > third * 2f) {
            // يمين الشاشة ⇒ القائمة على اليسار
            lp.leftMargin = Math.max(dp(8), Math.round(posX) - pw - dp(10));
            lp.topMargin = Math.max(dp(8), Math.round(posY + dp(SIZE_DP) / 2f - ph / 2f));
        } else if (centerX < third) {
            // يسار الشاشة ⇒ القائمة على اليمين
            lp.leftMargin = Math.min(hostWidth - pw - dp(8), Math.round(posX) + dp(SIZE_DP) + dp(10));
            lp.topMargin = Math.max(dp(8), Math.round(posY + dp(SIZE_DP) / 2f - ph / 2f));
        } else {
            // المنتصف ⇒ القائمة فوقها
            lp.leftMargin = Math.max(dp(8), Math.min(hostWidth - pw - dp(8),
                    Math.round(centerX - pw / 2f)));
            lp.topMargin = Math.max(dp(8), Math.round(posY) - ph - dp(10));
        }
        panel.setLayoutParams(lp);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    // ======================= اللمس =======================

    @SuppressLint("ClickableViewAccessibility")
    private final class BubbleView extends View {
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF ringRect = new RectF();
        private final long startTime = SystemClock.elapsedRealtime();
        private final PlayerIcon noteIcon = PlayerIcon.stroke(PlayerIcon.NOTE, 22);
        private final PlayerIcon closeIcon = PlayerIcon.stroke(PlayerIcon.CLOSE, 22);

        private float pressDown;

        BubbleView(Context context) {
            super(context);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeCap(Paint.Cap.ROUND);
            wavePaint.setStyle(Paint.Style.STROKE);
            setClickable(true);
            setFocusable(true);
            // v255 fix4: الطبقة الأم صارت MATCH_PARENT، والدائرة عنصر داخلها.
            // بدون هذا تصبح ضغطات الدائرة من نصيب الطبقة الأم (التي لا تعالج
            // اللمس) ⇒ القائمة تُفتح ثم تُغلق فوراً ولا يمكن السحب.
            requestDisallowInterceptTouchEvent(true);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            if (colors == null) {
                return;
            }
            final float cx = getWidth() / 2f;
            final float cy = getHeight() / 2f;
            final float radius = Math.min(getWidth(), getHeight()) / 2f - dp(2);

            drawWaves(canvas, cx, cy, radius);
            drawDiscAndRing(canvas, cx, cy, radius);
            drawIcon(canvas, cx, cy);
            invalidate();
        }

        /** أمواج متحركة تنتشر خلف الدائرة — نفس إحساس زر المكالمة. */
        private void drawWaves(Canvas canvas, float cx, float cy, float radius) {
            final long t = SystemClock.elapsedRealtime();
            final float period = 2200f;
            for (int i = 0; i < 2; i++) {
                float phase = ((t - startTime) % period) / period + i * 0.5f;
                phase = phase % 1f;
                float grow = phase * dp(16);
                float alpha = (1f - phase) * 0.38f;
                if (alpha <= 0.01f) {
                    continue;
                }
                int c = colors.primary;
                wavePaint.setColor(Color.argb((int) (alpha * 255),
                        Color.red(c), Color.green(c), Color.blue(c)));
                wavePaint.setStrokeWidth(dp(2));
                canvas.drawCircle(cx, cy, radius + grow, wavePaint);
            }
            // هالة ناعمة ثابتة
            int c = colors.primary;
            wavePaint.setColor(Color.argb(26, Color.red(c), Color.green(c), Color.blue(c)));
            wavePaint.setStrokeWidth(dp(6));
            canvas.drawCircle(cx, cy, radius + dp(1), wavePaint);
        }

        private void drawDiscAndRing(Canvas canvas, float cx, float cy, float radius) {
            discPaint.setColor(colors.primary);
            canvas.drawCircle(cx, cy, radius, discPaint);

            float progress = currentProgress();
            if (progress > 0f) {
                ringRect.set(cx - radius + dp(3), cy - radius + dp(3),
                        cx + radius - dp(3), cy + radius - dp(3));
                ringPaint.setStrokeWidth(dp(2.2f));
                ringPaint.setColor(Color.argb(48, 255, 255, 255));
                canvas.drawCircle(cx, cy, radius - dp(3), ringPaint);
                ringPaint.setColor(Color.argb(230, 255, 255, 255));
                canvas.drawArc(ringRect, -90f, 360f * progress, false, ringPaint);
            }
        }

        private final Paint discPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private void drawIcon(Canvas canvas, float cx, float cy) {
            // ✕ عندما تكون القائمة مفتوحة، وموسيقى عندما تُغلق
            final PlayerIcon icon = panelOpen ? closeIcon : noteIcon;
            icon.setColor(colors.onPrimary);
            // v255 fix4: PlayerIcon.draw() يتمركز حول getBounds()، وكانت
            // الحدود (0,0,0,0) فلا تُرسم الأيقونة إلا في الزاوية العليا
            // (لهذا ظهرت ✕ وحدها مقطوعة). نضبط الحدود حول المركز أولاً.
            final int s = dp(24);
            icon.setBounds(Math.round(cx - s / 2f), Math.round(cy - s / 2f),
                    Math.round(cx + s / 2f), Math.round(cy + s / 2f));
            icon.draw(canvas);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragArmed = true;
                    dragging = false;
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    downX = event.getX();
                    downY = event.getY();
                    pressDown = 1f;
                    bubble.setScaleX(0.92f);
                    bubble.setScaleY(0.92f);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - downRawX;
                    float dy = event.getRawY() - downRawY;
                    if (dragArmed && Math.hypot(dx, dy) > touchSlop) {
                        dragging = true;
                    }
                    if (dragging) {
                        ViewGroup parent = (ViewGroup) getParent();
                        if (parent != null) {
                            float nx = posX + dx;
                            float ny = posY + dy;
                            nx = clamp(nx, 0, parent.getWidth() - getWidth());
                            ny = clamp(ny, 0, parent.getHeight() - getHeight());
                            posX = nx;
                            posY = ny;
                            downRawX = event.getRawX();
                            downRawY = event.getRawY();
                            owner().layoutBubble();
                            owner().layoutPanel(parent.getWidth(), parent.getHeight());
                        }
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    bubble.setScaleX(1f);
                    bubble.setScaleY(1f);
                    if (!dragging) {
                        owner().togglePanel();
                    } else if (owner().onLayoutChanged != null) {
                        owner().onLayoutChanged.run();
                    }
                    dragArmed = false;
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    bubble.setScaleX(1f);
                    bubble.setScaleY(1f);
                    dragArmed = false;
                    dragging = false;
                    return true;
            }
            return super.onTouchEvent(event);
        }

        private MeeroMusicBubble owner() {
            return MeeroMusicBubble.this;
        }
    }

    private float currentProgress() {
        if (current == null) {
            return 0f;
        }
        float d = (float) current.getDuration();
        if (d <= 0) {
            return 0f;
        }
        float p = current.audioProgress;
        if (p < 0) {
            p = 0;
        }
        if (p > 1) {
            p = 1;
        }
        return p;
    }

    // ======================= القائمة =======================

    private void togglePanel() {
        if (panelOpen) {
            closePanel(true);
        } else {
            openPanel();
        }
    }

    private void openPanel() {
        if (panelOpen || current == null) {
            return;
        }
        panelOpen = true;
        bubble.invalidate();
        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) {
            layoutPanel(parent.getWidth(), parent.getHeight());
        }
        panel.setVisibility(VISIBLE);
        panel.measure(
                MeasureSpec.makeMeasureSpec(dp(PANEL_WIDTH_DP), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        if (parent != null) {
            layoutPanel(parent.getWidth(), parent.getHeight());
        }
        animatePanel(0f, 1f);
    }

    private void closePanel(boolean animated) {
        if (!panelOpen) {
            return;
        }
        panelOpen = false;
        bubble.invalidate();
        if (animated) {
            animatePanel(1f, 0f);
        } else {
            panelProgress = 0f;
            applyPanel();
        }
    }

    private void animatePanel(float from, float to) {
        if (panelAnimator != null) {
            panelAnimator.cancel();
        }
        panelAnimator = ValueAnimator.ofFloat(from, to);
        panelAnimator.setDuration(160);
        panelAnimator.setInterpolator(org.telegram.ui.Components.CubicBezierInterpolator.EASE_OUT);
        panelAnimator.addUpdateListener(a -> {
            panelProgress = (float) a.getAnimatedValue();
            applyPanel();
        });
        panelAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                applyPanel();
            }
        });
        panelAnimator.start();
    }

    private void applyPanel() {
        panel.setAlpha(panelProgress);
        float s = 0.88f + 0.12f * panelProgress;
        panel.setScaleX(s);
        panel.setScaleY(s);
        panel.setVisibility(panelProgress > 0f ? VISIBLE : GONE);
    }

    /** القائمة الصغيرة: غلاف + اسم الأغنية + الفنان + السابق/إيقاف/التالي. */
    private final class PanelView extends LinearLayout {
        private final CoverImage cover;
        private final TextView titleView;
        private final TextView artistView;
        private final PlayerIcon prevIcon = PlayerIcon.stroke(PlayerIcon.PREV, 20);
        private final PlayerIcon nextIcon = PlayerIcon.stroke(PlayerIcon.NEXT, 20);
        private final PlayerIcon playIcon = PlayerIcon.stroke(PlayerIcon.PAUSE, 20);
        private final ImageView prev;
        private final ImageView play;
        private final ImageView next;

        PanelView(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(dp(10), dp(10), dp(10), dp(8));
            setBackground(Theme.createRoundRectDrawable(dp(18), 0xFFFFFFFF));

            LinearLayout header = new LinearLayout(context);
            header.setOrientation(HORIZONTAL);
            cover = new CoverImage(context, 16);
            header.addView(cover, LayoutHelper.createLinear(40, 40));
            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(VERTICAL);
            titleView = new TextView(context);
            titleView.setTextSize(13);
            titleView.setMaxLines(1);
            titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            artistView = new TextView(context);
            artistView.setTextSize(11);
            artistView.setMaxLines(1);
            artistView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            texts.addView(artistView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            header.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 8, 0, 0, 0));
            addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            LinearLayout buttons = new LinearLayout(context);
            buttons.setOrientation(HORIZONTAL);
            buttons.setGravity(Gravity.CENTER);
            prev = makeButton(context, prevIcon);
            prev.setOnClickListener(v -> MediaController.getInstance().playPreviousMessage());
            play = makeButton(context, playIcon);
            play.setOnClickListener(v -> {
                MediaController mc = MediaController.getInstance();
                MessageObject mo = mc.getPlayingMessageObject();
                if (mo == null || mc.isDownloadingCurrentMessage()) {
                    return;
                }
                if (mc.isMessagePaused()) {
                    mc.playMessage(mo);
                } else {
                    mc.pauseMessage(mo);
                }
            });
            next = makeButton(context, nextIcon);
            next.setOnClickListener(v -> MediaController.getInstance().playNextMessage());
            buttons.addView(prev, LayoutHelper.createLinear(38, 38));
            buttons.addView(play, LayoutHelper.createLinear(44, 44, Gravity.CENTER, 6, 0, 6, 0));
            buttons.addView(next, LayoutHelper.createLinear(38, 38));
            addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 6, 0, 0));
        }

        private ImageView makeButton(Context context, PlayerIcon icon) {
            ImageView v = new ImageView(context);
            v.setImageDrawable(icon);
            v.setClickable(true);
            v.setBackground(Theme.createSelectorDrawable(0x22FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
            return v;
        }

        void bind(MessageObject mo, PlayerColors c) {
            if (mo == null || c == null) {
                return;
            }
            setBackground(Theme.createRoundRectDrawable(dp(18), c.surfaceContainer));
            titleView.setTextColor(c.onSurface);
            artistView.setTextColor(c.onSurfaceVariant);
            titleView.setText(mo.getMusicTitle(false));
            artistView.setText(mo.getMusicAuthor(false));
            cover.setMessage(mo);
            prevIcon.setColor(c.onPrimaryContainer);
            nextIcon.setColor(c.onPrimaryContainer);
            playIcon.setColor(c.onPrimaryContainer);
            prev.setBackground(Theme.createSelectorDrawable(0x22FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
            play.setBackground(Theme.createRoundRectDrawable(dp(22), c.primaryContainer));
            next.setBackground(Theme.createSelectorDrawable(0x22FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
            boolean paused = MediaController.getInstance().isMessagePaused();
            playIcon.setIcon(paused ? PlayerIcon.PLAY : PlayerIcon.PAUSE);
        }
    }

}
