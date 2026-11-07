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
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
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
import org.telegram.messenger.R;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.BlobDrawable;

import app.exteraless.player.CoverImage;
import app.exteraless.player.PlayerArt;
import app.exteraless.player.PlayerColors;
import app.exteraless.player.PlayerSheet;
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
    private static final int PANEL_WIDTH_DP = 224;   // v255 fix6: أوسع قليلاً كما طُلب

    private static final int STATE_HIDDEN = 0;
    private static final int STATE_VISIBLE = 1;

    private final int currentAccount;
    private final BubbleView bubble;
    private final PanelView panel;
    // The same two animated blobs used by the floating group-call button.
    private final BlobDrawable callWave = new BlobDrawable(8);
    private final BlobDrawable callWave2 = new BlobDrawable(9);
    private final Paint callWavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** إزاحة الدائرة عن الزاوية اليمنى السفلية — يُحفظ بين الجلسات. */
    private float posX = Float.NaN;
    private float posY = Float.NaN;

    private MessageObject current;
    private int seed;
    private boolean dark;
    private PlayerColors colors;

    private int state = STATE_HIDDEN;
    private boolean panelOpen;
    private boolean previewMode;
    private float appearProgress;

    private float panelProgress;
    private ValueAnimator panelAnimator;
    private ValueAnimator appearAnimator;

    private boolean dragArmed;
    private boolean dragging;
    private boolean bubbleTouchSequence;
    /** الهامش السفلي المحسوب (تبويبات + شريط تنقّل) — يُستعمل في الموضع الافتراضي. */
    private int bottomInsetDp;
    private float downRawX, downRawY, downX, downY;
    private int touchSlop;

    /** يستدعى عند تغيّر حجم/موضع الفقاعة حتى يعيد المضيف تثبيتها. */
    private Runnable onLayoutChanged;
    /** v255 fix6: المضيف لفتح المشغل الكبير (PlayerSheet) بضغطة على الغلاف. */
    private org.telegram.ui.ActionBar.BaseFragment hostFragment;

    public MeeroMusicBubble(Context context, int currentAccount) {
        super(context);
        this.currentAccount = currentAccount;
        setClipChildren(false);
        setClipToPadding(false);
        // Scale the call button's radii to the 48dp music bubble.
        callWave.minRadius = dp(23);
        callWave.maxRadius = dp(30);
        callWave2.minRadius = dp(24);
        callWave2.maxRadius = dp(28.5f);
        callWave.generateBlob();
        callWave2.generateBlob();

        bubble = new BubbleView(context);
        // v255 fix5: الموضع صار بـ (gravity + translationX/Y) بدل leftMargin/topMargin.
        // الترجمة تُطبَّق دائماً بعد التخطيط ولا تعتمد على توقيت setLayoutParams،
        // فلا يمكن أن تبقى الدائرة في الأعلى بعد اليوم.
        addView(bubble, LayoutHelper.createFrame(SIZE_DP, SIZE_DP, Gravity.TOP | Gravity.LEFT));

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
        // الطبقة نفسها لا تستهلك اللمس أبداً — الأبناء (الدائرة/القائمة)
        // يستقبلونه أولاً، وما يصل هنا يعني أنه خارجها.
        return false;
    }

    /**
     * v255 fix4: بما أن الطبقة تغطي الشاشة، نحسب أين وقع اللمس بأنفسنا
     * ونوجّهه للدائرة أو للقائمة. هذا يضمن السحب حتى لو تعامل Android
     * مع الطبقة كحاجز لمس.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // v255 fix6: سابقاً كنت أُعيد false للحدث خارج الدائرة، وهذا كان يقطع
        // سلسلة اللمس فلا تصل للأزرار داخل القائمة أبداً (كل زر يغلق القائمة).
        // الآن: التوجيه يقتصر على الدائرة، وكل ما سواها يمرّ للمسار الطبيعي
        // (القائمة ثم الأبناء ثم الأب).
        if (bubble == null) {
            return super.dispatchTouchEvent(ev);
        }
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            bubbleTouchSequence = false;
            final float x = ev.getX();
            final float y = ev.getY();
            // The panel has priority in the overlap with the enlarged bubble hit area.
            boolean inPanel = panel.getVisibility() == VISIBLE && panel.getAlpha() > 0f
                    && x >= panel.getX() && x <= panel.getX() + panel.getWidth()
                    && y >= panel.getY() && y <= panel.getY() + panel.getHeight();
            final float bx = bubble.getX(), by = bubble.getY();
            final int bs = bubble.getWidth() > 0 ? bubble.getWidth() : dp(SIZE_DP);
            final float touch = dp(18);
            if (!inPanel && x >= bx - touch && x <= bx + bs + touch
                    && y >= by - touch && y <= by + bs + touch) {
                MotionEvent local = MotionEvent.obtain(ev);
                local.offsetLocation(-bx, -by);
                bubbleTouchSequence = bubble.dispatchTouchEvent(local);
                local.recycle();
                if (bubbleTouchSequence) return true;
            }
        } else if (bubbleTouchSequence) {
            MotionEvent local = MotionEvent.obtain(ev);
            local.offsetLocation(-bubble.getX(), -bubble.getY());
            bubble.dispatchTouchEvent(local);
            local.recycle();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) bubbleTouchSequence = false;
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Draw in the full-screen host, not the 48dp child: otherwise the
        // animated lobes are clipped at the bubble's rectangular bounds.
        if (!previewMode && colors != null && appearProgress > 0f && bubble.getWidth() > 0) {
            float cx = bubble.getX() + bubble.getWidth() / 2f;
            float cy = bubble.getY() + bubble.getHeight() / 2f;
            boolean paused = MediaController.getInstance().isMessagePaused();
            // Keep the current blob paths unchanged during pause; update resumes
            // from these same animation positions when playback continues.
            if (!paused) {
                callWave.update(0.18f, 0.8f);
                callWave2.update(0.18f, 0.8f);
            }
            callWavePaint.setStyle(Paint.Style.FILL);
            callWavePaint.setColor(colors.primary);
            callWavePaint.setAlpha((int) (76 * appearProgress));
            canvas.save();
            float scale = 0.7f + 0.3f * appearProgress;
            canvas.scale(scale, scale, cx, cy);
            callWave.draw(cx, cy, canvas, callWavePaint);
            callWave2.draw(cx, cy, canvas, callWavePaint);
            canvas.restore();
            if (!paused) postInvalidateOnAnimation();
        }
        super.dispatchDraw(canvas);
    }

    public void setOnLayoutChanged(Runnable runnable) {
        onLayoutChanged = runnable;
    }

    public void setHostFragment(org.telegram.ui.ActionBar.BaseFragment fragment) {
        hostFragment = fragment;
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
            panel.invalidateProgress();
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
        ViewGroup.LayoutParams panelParams = panel.getLayoutParams();
        panelParams.width = dp(PlayerStyle.miniCompact() ? 200 : PANEL_WIDTH_DP);
        panel.setLayoutParams(panelParams);
        if (getWidth() > 0 && getHeight() > 0) layoutPanel(getWidth(), getHeight());
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
            // v255 fix6: أزرق واضح عند التوقّف — لا يتأثر بلون الثيم أو الغلاف
            return PlayerColors.fromSeed(0xff3390EC, false);
        }
        final int argb = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader);
        int chosenSeed;
        if (PlayerStyle.miniThemeColors()) {
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
            // المطلوب: منتصف الشاشة عمودياً، على اليسار أفقياً
            posX = dp(16);
            posY = (hostHeight - size) / 2f;
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
            // المطلوب: منتصف الشاشة عمودياً، على اليسار أفقياً
            posX = dp(16);
            posY = (ph - size) / 2f;
        }

        View parent = (View) getParent();
        if (parent != null && parent.getWidth() > 0 && parent.getHeight() > 0) {
            final int bs = dp(SIZE_DP);
            posX = clamp(posX, 0, Math.max(0, parent.getWidth() - bs));
            posY = clamp(posY, 0, Math.max(0, parent.getHeight() - bs));
        }

        // المارجن ليس ضرورياً بعد الآن، لكن نُبقيه صفراً لتفادي أي إزاحة مزدوجة
        lp.leftMargin = 0;
        lp.topMargin = 0;
        // الترجمة هي مصدر الحقيقة الوحيد للموضع — تُطبَّق دائماً بعد التخطيط
        bubble.setTranslationX(posX);
        bubble.setTranslationY(posY);
    }

    /**
     * القائمة تتبع الجهة مثل قائمة المكالمة:
     * إذا كانت الدائرة يمين الشاشة تُفتح القائمة على اليسار، وإذا كانت يساراً
     * تُفتح على اليمين، وإذا كانت في المنتصف تُفتح فوقها.
     */
    private void layoutPanel(int hostWidth, int hostHeight) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) panel.getLayoutParams();
        int pw = dp(PlayerStyle.miniCompact() ? 200 : PANEL_WIDTH_DP);
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
        private final RectF ringRect = new RectF();
        private final PlayerIcon noteIcon = PlayerIcon.stroke(PlayerIcon.NOTE, 22);
        private final PlayerIcon closeIcon = PlayerIcon.stroke(PlayerIcon.CLOSE, 22);

        private float pressDown;

        BubbleView(Context context) {
            super(context);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeCap(Paint.Cap.ROUND);
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

            drawDiscAndRing(canvas, cx, cy, radius);
            drawIcon(canvas, cx, cy);
            invalidate();
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
                            final int bs = dp(SIZE_DP);
                            nx = MeeroMusicBubble.clamp(nx, 0, Math.max(0, parent.getWidth() - bs));
                            ny = MeeroMusicBubble.clamp(ny, 0, Math.max(0, parent.getHeight() - bs));
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
            return previewMode ? 0.38f : 0f;
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
        colors = resolveColors(current);
        panel.bind(current, colors);
        ViewGroup.LayoutParams panelParams = panel.getLayoutParams();
        panelParams.width = dp(PlayerStyle.miniCompact() ? 200 : PANEL_WIDTH_DP);
        panel.setLayoutParams(panelParams);
        panelOpen = true;
        bubble.invalidate();
        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) {
            layoutPanel(parent.getWidth(), parent.getHeight());
        }
        panel.setVisibility(VISIBLE);
        panel.measure(
                MeasureSpec.makeMeasureSpec(dp(PlayerStyle.miniCompact() ? 200 : PANEL_WIDTH_DP), MeasureSpec.EXACTLY),
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
        private final LinearLayout header;
        private final PlayerIcon prevIcon = PlayerIcon.stroke(PlayerIcon.PREV, 18);
        private final PlayerIcon nextIcon = PlayerIcon.stroke(PlayerIcon.NEXT, 18);
        private final PlayerIcon playIcon = PlayerIcon.stroke(PlayerIcon.PAUSE, 18);
        private final PlayerIcon closeIcon = PlayerIcon.stroke(PlayerIcon.CLOSE, 18);
        private final ImageView prev;
        private final ImageView play;
        private final ImageView next;
        private final ImageView close;
        private final View progressLine;
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean seeking;
        private float seekProgress;
        private MessageObject seekMessage;

        PanelView(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(dp(10), dp(10), dp(10), dp(8));

            // ---- الرأس: غلاف + اسم الأغنية + الفنان (ينقل للمشغل الكبير) ----
            header = new LinearLayout(context) {
                @Override protected void dispatchDraw(Canvas canvas) {
                    super.dispatchDraw(canvas);
                    if (PlayerStyle.miniProgress() != PlayerStyle.MINI_PROGRESS_BORDER || colors == null) return;
                    float inset = dp(1.5f);
                    RectF rect = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
                    Path path = new Path();
                    float radius = PlayerStyle.bubbleShape() == 1 ? getHeight() / 2f : dp(10);
                    path.addRoundRect(rect, radius, radius, Path.Direction.CW);
                    PathMeasure measure = new PathMeasure(path, false);
                    Path segment = new Path();
                    measure.getSegment(0, measure.getLength() * currentProgress(), segment, true);
                    progressPaint.setStyle(Paint.Style.STROKE);
                    progressPaint.setStrokeWidth(dp(2));
                    progressPaint.setStrokeCap(Paint.Cap.ROUND);
                    progressPaint.setColor(colors.primary);
                    canvas.drawPath(segment, progressPaint);
                }
            };
            header.setOrientation(HORIZONTAL);
            header.setClickable(true);
            header.setOnClickListener(v -> openFullPlayer());
            cover = new CoverImage(context, 22);
            cover.setListener(PlayerColors.fallbackSeed(null), (message, extractedSeed) -> {
                // Extraction is asynchronous. Recheck the song when the result
                // arrives, so a late result cannot recolor the next track.
                post(() -> {
                    if (current == null || !android.text.TextUtils.equals(PlayerArt.key(current), PlayerArt.key(message))) return;
                    if (previewMode) {
                        refreshPanelPreview();
                    } else {
                        colors = resolveColors(current);
                        bind(current, colors);
                        bubble.invalidate();
                        MeeroMusicBubble.this.invalidate();
                    }
                });
            });
            header.addView(cover, LayoutHelper.createLinear(44, 44));
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

            progressLine = new View(context) {
                @Override protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);
                    if (colors == null) return;
                    float y = getHeight() / 2f;
                    progressPaint.setStyle(Paint.Style.STROKE);
                    progressPaint.setStrokeWidth(dp(3));
                    progressPaint.setStrokeCap(Paint.Cap.ROUND);
                    progressPaint.setColor(colors.outlineVariant);
                    canvas.drawLine(dp(2), y, getWidth() - dp(2), y, progressPaint);
                    progressPaint.setColor(colors.primary);
                    float progress = seeking ? seekProgress : currentProgress();
                    canvas.drawLine(dp(2), y, dp(2) + (getWidth() - dp(4)) * progress, y, progressPaint);
                }

                @Override public boolean onTouchEvent(MotionEvent event) {
                    int action = event.getActionMasked();
                    if (action == MotionEvent.ACTION_DOWN) {
                        if (previewMode || current == null || MediaController.getInstance().isDownloadingCurrentMessage()) return false;
                        seeking = true;
                        seekMessage = current;
                        getParent().requestDisallowInterceptTouchEvent(true);
                    } else if (!seeking) {
                        return false;
                    }
                    if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
                        seekProgress = MeeroMusicBubble.clamp((event.getX() - dp(2)) / Math.max(1f, getWidth() - dp(4)), 0f, 1f);
                        invalidate();
                    }
                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        if (action == MotionEvent.ACTION_UP && seekMessage != null && current != null
                                && android.text.TextUtils.equals(PlayerArt.key(seekMessage), PlayerArt.key(current))) {
                            if (MediaController.getInstance().seekToProgress(seekMessage, seekProgress)) {
                                seekMessage.audioProgress = seekProgress;
                                seekMessage.audioProgressSec = (int) (seekMessage.getDuration() * seekProgress);
                            }
                            performClick();
                        }
                        seeking = false;
                        seekMessage = null;
                        getParent().requestDisallowInterceptTouchEvent(false);
                        invalidateProgress();
                        bubble.invalidate();
                    }
                    return true;
                }

                @Override public boolean performClick() {
                    super.performClick();
                    return true;
                }
            };
            progressLine.setContentDescription(getString(R.string.OEPlayerSeek));
            addView(progressLine, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 28, Gravity.CENTER, 2, 0, 2, 0));

            // ---- الأزرار: السابق · إيقاف/تشغيل · التالي · إغلاق المشغل ----
            LinearLayout buttons = new LinearLayout(context);
            buttons.setOrientation(HORIZONTAL);
            buttons.setGravity(Gravity.CENTER);

            prev = makeButton(context, prevIcon);
            prev.setOnClickListener(v -> {
                if (previewMode) return;
                MediaController.getInstance().playPreviousMessage();
                refreshPanelOnly();
            });

            play = new ImageView(context) {
                @Override protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);
                    if (PlayerStyle.miniProgress() != PlayerStyle.MINI_PROGRESS_RING || colors == null) return;
                    float inset = dp(2);
                    RectF rect = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
                    progressPaint.setStyle(Paint.Style.STROKE);
                    progressPaint.setStrokeWidth(dp(2));
                    progressPaint.setStrokeCap(Paint.Cap.ROUND);
                    progressPaint.setColor(colors.outlineVariant);
                    canvas.drawArc(rect, -90, 360, false, progressPaint);
                    progressPaint.setColor(colors.primary);
                    canvas.drawArc(rect, -90, 360 * currentProgress(), false, progressPaint);
                }
            };
            play.setImageDrawable(playIcon);
            play.setScaleType(ImageView.ScaleType.CENTER);
            play.setClickable(true);
            play.setFocusable(true);
            play.setOnClickListener(v -> {
                if (previewMode) return;
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
                refreshPanelOnly();
            });

            next = makeButton(context, nextIcon);
            next.setOnClickListener(v -> {
                if (previewMode) return;
                MediaController.getInstance().playNextMessage();
                refreshPanelOnly();
            });

            // v255 fix6: ✕ يطفئ الموسيقى وتختفي الدائرة (كما في المشغل القديم)
            close = makeButton(context, closeIcon);
            close.setOnClickListener(v -> {
                if (previewMode) return;
                MediaController.getInstance().cleanupPlayer(true, true);
                closePanel(false);
                refresh(false);
            });

            buttons.addView(prev, LayoutHelper.createLinear(40, 40));
            buttons.addView(play, LayoutHelper.createLinear(46, 46, Gravity.CENTER, 4, 0, 4, 0));
            buttons.addView(next, LayoutHelper.createLinear(40, 40, Gravity.CENTER, 4, 0, 0, 0));
            buttons.addView(close, LayoutHelper.createLinear(36, 36, Gravity.CENTER, 4, 0, 0, 0));
            addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 6, 0, 0));
        }

        private ImageView makeButton(Context context, PlayerIcon icon) {
            ImageView v = new ImageView(context);
            v.setImageDrawable(icon);
            v.setScaleType(ImageView.ScaleType.CENTER);
            v.setClickable(true);
            v.setFocusable(true);
            v.setBackground(Theme.createSelectorDrawable(0x22000000, Theme.RIPPLE_MASK_CIRCLE_20DP));
            return v;
        }

        void bind(MessageObject mo, PlayerColors c) {
            if (c == null) return;
            boolean compact = PlayerStyle.miniCompact();
            setPadding(dp(compact ? 7 : 10), dp(compact ? 7 : 10), dp(compact ? 7 : 10), dp(compact ? 5 : 8));
            setBackground(Theme.createRoundRectDrawable(dp(PlayerStyle.bubbleShape() == 1 ? 80 : 18), c.surfaceContainer));
            cover.setVisibility(PlayerStyle.miniCover() == PlayerStyle.MINI_COVER_HIDDEN ? GONE : VISIBLE);
            cover.setRadius(dp(PlayerStyle.miniCover() == PlayerStyle.MINI_COVER_ROUND ? 22 : 10));
            // Same fallback artwork colors as the original mini player.
            cover.setColors(c.secondaryContainer, c.onSecondaryContainer);
            artistView.setVisibility(PlayerStyle.miniArtist() ? VISIBLE : GONE);
            prev.setVisibility(PlayerStyle.miniPrevious() ? VISIBLE : GONE);
            next.setVisibility(PlayerStyle.miniNext() ? VISIBLE : GONE);
            close.setVisibility(PlayerStyle.miniClose() ? VISIBLE : GONE);
            progressLine.setVisibility(PlayerStyle.miniProgress() == PlayerStyle.MINI_PROGRESS_LINE ? VISIBLE : GONE);
            titleView.setTextSize(compact ? 12 : 13);
            titleView.setTextColor(c.onSurface);
            artistView.setTextColor(c.onSurfaceVariant);
            titleView.setText(mo == null ? getString(R.string.OEPlayerPreviewTitle) : mo.getMusicTitle(false));
            artistView.setText(mo == null ? getString(R.string.OEPlayerPreviewArtist) : mo.getMusicAuthor(false));
            cover.setMessage(mo);
            header.setBackground(Theme.createSelectorDrawable(0x15000000, Theme.RIPPLE_MASK_CIRCLE_20DP));
            final int tint = c.onPrimaryContainer;
            prevIcon.setColor(tint);
            nextIcon.setColor(tint);
            playIcon.setColor(tint);
            closeIcon.setColor(tint);
            prev.setBackground(Theme.createSelectorDrawable(0x22000000, Theme.RIPPLE_MASK_CIRCLE_20DP));
            play.setBackground(Theme.createRoundRectDrawable(dp(23), c.primaryContainer));
            next.setBackground(Theme.createSelectorDrawable(0x22000000, Theme.RIPPLE_MASK_CIRCLE_20DP));
            close.setBackground(Theme.createSelectorDrawable(0x22000000, Theme.RIPPLE_MASK_CIRCLE_20DP));
            boolean paused = MediaController.getInstance().isMessagePaused();
            playIcon.setIcon(paused ? PlayerIcon.PLAY : PlayerIcon.PAUSE);
            invalidateProgress();
        }

        void invalidateProgress() {
            header.invalidate();
            progressLine.invalidate();
            play.invalidate();
        }
    }

    /** The editor uses the actual panel view, without activating playback controls. */
    public View createPanelPreview() {
        previewMode = true;
        removeView(panel);
        panel.setVisibility(VISIBLE);
        panel.setAlpha(1f);
        refreshPanelPreview();
        return panel;
    }

    public void refreshPanelPreview() {
        if (!previewMode) return;
        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        current = playing != null && playing.isMusic() ? playing : null;
        dark = PlayerColors.isDark(null);
        colors = current == null ? PlayerColors.fromSeed(PlayerColors.fallbackSeed(null), dark) : resolveColors(current);
        panel.bind(current, colors);
        ViewGroup.LayoutParams lp = panel.getLayoutParams();
        if (lp != null) {
            lp.width = dp(PlayerStyle.miniCompact() ? 200 : PANEL_WIDTH_DP);
            panel.setLayoutParams(lp);
        }
    }

    /** إعادة ربط القائمة فقط بعد كل ضغطة زر. */
    private void refreshPanelOnly() {
        if (current != null && colors != null) {
            panel.bind(current, colors);
        }
        bubble.invalidate();
    }

    /** v255 fix6: النقر على الغلاف/الاسم يفتح المشغل الكبير (PlayerSheet). */
    private void openFullPlayer() {
        try {
            if (hostFragment == null || hostFragment.getParentActivity() == null) {
                return;
            }
            closePanel(false);
            final PlayerSheet sheet = new PlayerSheet(hostFragment.getParentActivity(),
                    hostFragment.getResourceProvider());
            hostFragment.showDialog(sheet);
        } catch (Throwable t) {
            org.telegram.messenger.FileLog.e(t);
        }
    }

}
