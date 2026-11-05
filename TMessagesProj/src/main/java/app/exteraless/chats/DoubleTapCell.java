package app.exteraless.chats;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.MessageDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.LayoutHelper;

import xyz.nextalone.nagram.NaConfig;

/**
 * ArasGram v253: превью двойного тапа - два пузыря с анимированной
 * иконкой действия. Порт компонента exteraless (DoubleTapCell) без
 * вложенного SetReactionCell (строка выбора реакции в этой ветке не
 * переносится - здесь нужен только сам «симулятор» пузырей).
 *
 * Индексы действий - NagramX DoubleTap (0..10).
 */
public class DoubleTapCell extends LinearLayout {

    private final RectF rect = new RectF();
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint[] circleOutlinePaint = new Paint[2];

    private final MessageDrawable[] messages = new MessageDrawable[]{
            new MessageDrawable(MessageDrawable.TYPE_TEXT, false, false),
            new MessageDrawable(MessageDrawable.TYPE_TEXT, true, false)
    };

    // Иконки по индексам действий NagramX DoubleTap (0..10).
    private static final int[] doubleTapIcons = new int[]{
            R.drawable.msg_block,          // 0 NONE
            R.drawable.msg_reactions,      // 1 SEND_REACTIONS
            R.drawable.msg_reactions2,     // 2 SHOW_REACTIONS
            R.drawable.msg_translate,      // 3 TRANSLATE
            R.drawable.msg_reply_small,    // 4 REPLY
            R.drawable.msg_saved,          // 5 SAVE
            R.drawable.msg_repeat,         // 6 REPEAT
            R.drawable.msg_copy,           // 7 REPEAT_AS_COPY
            R.drawable.msg_edit,           // 8 EDIT
            R.drawable.msg_translate,      // 9 TRANSLATE_LLM
            R.drawable.msg_delete,         // 10 DELETE
            R.drawable.msg_forward,
            R.drawable.msg_download
    };

    private static final int[] ICON_WIDTH = new int[]{AndroidUtilities.dp(12), AndroidUtilities.dp(12)};

    private final ValueAnimator[] animator = new ValueAnimator[2];
    private final ValueAnimator[] circleAnimator = new ValueAnimator[2];
    private final ValueAnimator[] circleSizeAnimator = new ValueAnimator[2];
    private final float[] circleSizeProgress = new float[4];
    private final float[] iconChangingProgress = new float[2];
    private final float[] circleProgress = new float[4];
    private final int[] actionIcon = new int[2];

    private final FrameLayout preview;

    private static int iconFor(int action) {
        return action >= 0 && action < doubleTapIcons.length ? doubleTapIcons[action] : doubleTapIcons[0];
    }

    private static int incomingAction() {
        return NaConfig.INSTANCE.getDoubleTapAction().Int();
    }

    private static int outgoingAction() {
        return NaConfig.INSTANCE.getDoubleTapActionOut().Int();
    }

    public DoubleTapCell(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        setPadding(AndroidUtilities.dp(13), 0, AndroidUtilities.dp(13), AndroidUtilities.dp(10));

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_switchTrack), 0x3F));
        outlinePaint.setStrokeWidth(Math.max(2, AndroidUtilities.dp(1f)));

        preview = new FrameLayout(context) {
            @SuppressLint("DrawAllocation")
            @Override
            protected void onDraw(Canvas canvas) {
                int color = Theme.getColor(Theme.key_switchTrack);
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);

                @SuppressLint("DrawAllocation") Rect rect1 = new Rect();

                float stroke = outlinePaint.getStrokeWidth() / 2;
                Theme.dialogs_onlineCirclePaint.setColor(Color.argb(20, r, g, b));

                for (int i = 0; i < 2; i++) {
                    if (i == 0) {
                        rect.set(stroke + AndroidUtilities.dp(8), stroke + AndroidUtilities.dp(10), getMeasuredWidth() / 2f - AndroidUtilities.dp(8) - stroke, AndroidUtilities.dp(75) - stroke);
                    } else {
                        canvas.translate(0, AndroidUtilities.dp(80));
                        rect.set(stroke + getMeasuredWidth() / 2f + AndroidUtilities.dp(8), stroke + AndroidUtilities.dp(5), getMeasuredWidth() - AndroidUtilities.dp(8) - stroke, AndroidUtilities.dp(70) - stroke);
                    }
                    rect.round(rect1);
                    messages[i].setBounds(rect1);
                    messages[i].draw(canvas, Theme.dialogs_onlineCirclePaint);
                    messages[i].draw(canvas, outlinePaint);

                    for (int j = 0; j < 2; j++) {
                        circleOutlinePaint[j] = new Paint(Paint.ANTI_ALIAS_FLAG);
                        circleOutlinePaint[j].setStyle(Paint.Style.STROKE);
                        circleOutlinePaint[j].setColor(ColorUtils.blendARGB(0x00, Color.argb(76, r, g, b), circleProgress[i + 2 * j]));
                        circleOutlinePaint[j].setStrokeWidth(AndroidUtilities.dp(1.5f) * circleProgress[i + 2 * j] * circleProgress[i + 2 * j]);
                        canvas.drawCircle((i == 0 ? 1 : 3) * getMeasuredWidth() / 4f, getMeasuredHeight() / 4f + AndroidUtilities.dpf2(i == 0 ? 3f : -2f), AndroidUtilities.dp(25 - 6 * j) * circleSizeProgress[i + 2 * j], circleOutlinePaint[j]);
                    }

                    Drawable icon = ContextCompat.getDrawable(context, actionIcon[i]);
                    if (icon == null) continue;
                    if (i == 0)
                        icon.setBounds(getMeasuredWidth() / 4 - ICON_WIDTH[i], (int) (getMeasuredHeight() / 4 - ICON_WIDTH[i] + AndroidUtilities.dpf2(3f)), getMeasuredWidth() / 4 + ICON_WIDTH[i], (int) (getMeasuredHeight() / 4 + ICON_WIDTH[i] + AndroidUtilities.dpf2(3f)));
                    else
                        icon.setBounds(3 * getMeasuredWidth() / 4 - ICON_WIDTH[i], (int) (getMeasuredHeight() / 4 - ICON_WIDTH[i] - AndroidUtilities.dpf2(2f)), 3 * getMeasuredWidth() / 4 + ICON_WIDTH[i], (int) (getMeasuredHeight() / 4 + ICON_WIDTH[i] - AndroidUtilities.dpf2(2f)));

                    icon.setBounds(
                            icon.getBounds().left - AndroidUtilities.dp(4 - 4 * iconChangingProgress[i]),
                            icon.getBounds().top - AndroidUtilities.dp(4 - 4 * iconChangingProgress[i]),
                            icon.getBounds().right + AndroidUtilities.dp(4 - 4 * iconChangingProgress[i]),
                            icon.getBounds().bottom + AndroidUtilities.dp(4 - 4 * iconChangingProgress[i])
                    );
                    icon.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(0x00, Theme.getColor(Theme.key_chats_menuItemIcon), iconChangingProgress[i]), PorterDuff.Mode.MULTIPLY));
                    icon.draw(canvas);
                }
            }
        };
        preview.setWillNotDraw(false);
        addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        updateIcons(0, false);
    }

    @SuppressLint("Recycle")
    public void updateIcons(int inv, boolean animate) {
        for (int i = 0; i < 2; i++) {
            if (i == 0 && inv == 2 || i == 1 && inv == 1) continue;
            if (animate) {
                int finalI = i;
                for (int j = 0; j < 2; j++) {
                    int finalJ = j;
                    circleSizeAnimator[j] = ValueAnimator.ofFloat(0f, 1f).setDuration(1300);
                    circleSizeAnimator[j].setStartDelay(j * 60L);
                    circleSizeAnimator[j].setInterpolator(Easings.easeInOutQuad);
                    circleSizeAnimator[j].addUpdateListener(animation -> {
                        circleSizeProgress[finalJ * 2 + finalI] = (Float) animation.getAnimatedValue();
                        invalidate();
                    });

                    circleAnimator[j] = ValueAnimator.ofFloat(0f, 1f).setDuration(700);
                    circleAnimator[j].setStartDelay(150 + j * 80L);
                    circleAnimator[j].setInterpolator(Easings.easeInOutQuad);
                    circleAnimator[j].addUpdateListener(animation -> {
                        circleProgress[finalJ * 2 + finalI] = (Float) animation.getAnimatedValue();
                        invalidate();
                    });
                    circleAnimator[j].addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            circleAnimator[finalJ].setFloatValues(1f, 0f);
                            circleAnimator[finalJ].setDuration(700);
                            circleAnimator[finalJ].removeAllListeners();
                            circleAnimator[finalJ].start();
                        }
                    });
                    circleSizeAnimator[j].start();
                    circleAnimator[j].start();
                }

                animator[i] = ValueAnimator.ofFloat(1f, 0f).setDuration(250);
                animator[i].setInterpolator(Easings.easeInOutQuad);
                animator[i].addUpdateListener(animation -> {
                    iconChangingProgress[finalI] = (Float) animation.getAnimatedValue();
                    invalidate();
                });
                animator[i].addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        actionIcon[finalI] = finalI == 0 ? iconFor(incomingAction()) : iconFor(outgoingAction());
                        animator[finalI].setFloatValues(0f, 1f);
                        animator[finalI].removeAllListeners();
                        animator[finalI].addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                super.onAnimationEnd(animation);
                                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                            }
                        });
                        animator[finalI].start();
                    }
                });
                animator[i].start();
            } else {
                circleSizeProgress[i] = 0f;
                circleProgress[i] = 0f;
                iconChangingProgress[i] = 1f;
                actionIcon[i] = i == 0 ? iconFor(incomingAction()) : iconFor(outgoingAction());
                invalidate();
            }
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (preview != null) {
            preview.invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(21), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(21) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(170), MeasureSpec.EXACTLY));
    }
}
