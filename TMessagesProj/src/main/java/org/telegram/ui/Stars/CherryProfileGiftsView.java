package org.telegram.ui.Stars;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ProfileActivity;
import java.util.HashMap;
import java.util.HashSet;

/** Experimental reconstruction of Cherrygram profile gift motion. */
public final class CherryProfileGiftsView extends ProfileGiftsView implements CherryProfileBeat.BeatListener {
    private final View avatar;
    private final int account;
    private Runnable animationFrameListener;
    private final HashMap<Long, CherryGiftMotion> motion = new HashMap<>();
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float expandedY, maxExpandedY, centerX, centerY, time;
    private float beat, smoothedBeat;
    private long lastFrame, lastSample;
    private Gift selected;
    private float downX, downY, offsetX, offsetY;
    private boolean dragging;
    private boolean active = true;
    private VelocityTracker velocity;

    public CherryProfileGiftsView(Context context, int account, long dialogId, View avatar,
                                  ProfileActivity.AvatarImageView avatarImage, Theme.ResourcesProvider provider) {
        super(context, account, dialogId, avatar, avatarImage, provider);
        this.avatar = avatar;
        this.account = account;
    }

    @Override public void setBounds(float left, float right, float cy, boolean animated, int maxExpandY) {
        super.setBounds(left, right, cy, animated, maxExpandY);
        this.maxExpandedY = maxExpandY + cy;
    }

    @Override public void setExpandCoords(float y) {
        super.setExpandCoords(y);
        expandedY = y;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        CherryProfileBeat.m2872(this);
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.messagePlayingDidReset);
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        CherryProfileBeat.m2871(this);
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.messagePlayingDidReset);
        cancelGesture();
        super.onDetachedFromWindow();
    }

    @Override public void didReceivedNotification(int id, int account, Object... args) {
        super.didReceivedNotification(id, account, args);
        if (id == NotificationCenter.messagePlayingDidStart || id == NotificationCenter.messagePlayingPlayStateChanged || id == NotificationCenter.messagePlayingDidReset) {
            invalidate();
            if (animationFrameListener != null) animationFrameListener.run();
        }
    }

    @Override public void setActive(boolean value) {
        super.setActive(value);
        active = value;
        if (!value) cancelGesture();
    }

    private boolean musicPlaying() {
        MediaController mc = MediaController.getInstance();
        MessageObject msg = mc.getPlayingMessageObject();
        return msg != null && msg.currentAccount == account && msg.isMusic() && !mc.isMessagePaused();
    }

    @Override public void mo2878(float strength) {
        if (musicPlaying()) {
            beat = strength;
            lastSample = SystemClock.uptimeMillis();
            invalidate();
        }
    }

    public float getBeatLevel() { return smoothedBeat; }
    public boolean isMusicPlaying() { return musicPlaying(); }
    public void setAnimationFrameListener(Runnable listener) { animationFrameListener = listener; }

    private void syncMotion() {
        HashSet<Long> alive = new HashSet<>();
        for (int i = 0; i < gifts.size(); i++) {
            Gift gift = gifts.get(i);
            alive.add(gift.id);
            CherryGiftMotion item = motion.get(gift.id);
            if (item == null) {
                motion.put(gift.id, new CherryGiftMotion(gift.id, i, gifts.size()));
            } else {
                item.m2044(gift.id, i, gifts.size());
            }
        }
        motion.keySet().retainAll(alive);
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        time += dt;
        float targetBeat = musicPlaying() && now - lastSample < 350 ? beat : 0f;
        smoothedBeat += (targetBeat - smoothedBeat) * (1f - (float)Math.exp(-dt / (targetBeat > smoothedBeat ? .045f : .2f)));
        if (animationFrameListener != null) animationFrameListener.run();
        if (gifts.isEmpty() || expandProgress >= 1f || collapseProgress <= 0f) {
            if ((musicPlaying() || smoothedBeat > .001f) && isShown()) postInvalidateOnAnimation();
            return;
        }
        float x = avatar.getX(), y = avatar.getY();
        float width = avatar.getWidth() * avatar.getScaleX(), height = avatar.getHeight() * avatar.getScaleY();
        float size = AndroidUtilities.dpf2(96);
        float ax = Math.min(x, (getWidth() - size) / 2f);
        float ay = Math.max(y, (maxExpandedY - size) / 2f);
        float aw = Math.max(width, size), ah = Math.max(height, size);
        centerX = ax + aw / 2f; centerY = ay + ah / 2f;
        float radius = AndroidUtilities.dp(25);
        float rx = Math.max(radius, Math.min(AndroidUtilities.dp(125), Math.min(centerX, getWidth() - centerX) - radius - AndroidUtilities.dp(8)));
        float ry = Math.min(AndroidUtilities.dp(56), ah * .52f + AndroidUtilities.dp(8));
        float visible = Math.max(0f, Math.min(1f, (expandedY - (AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight())) / AndroidUtilities.dp(50f)));
        syncMotion();
        for (Gift gift : gifts) {
            CherryGiftMotion m = motion.get(gift.id);
            m.m2052(dt, false);
            m.m2057(time, 0f, rx, ry);
            m.m2056();
            m.m2045(dt, radius, false);
            m.m2043(AndroidUtilities.dp(1800));
        }
        for (int i = 0; i < gifts.size(); i++) {
            CherryGiftMotion a = motion.get(gifts.get(i).id);
            for (int j = i + 1; j < gifts.size(); j++) {
                CherryGiftMotion b = motion.get(gifts.get(j).id);
                a.m2053(b, a.f2000 * radius * a.f2018, b.f2000 * radius * b.f2018, AndroidUtilities.dp(1800));
            }
        }
        canvas.save();
        canvas.clipRect(0, 0, getWidth(), expandedY);
        for (Gift gift : gifts) {
            CherryGiftMotion m = motion.get(gift.id);
            float enter = gift.animatedFloat.set(1f);
            float alpha = visible * enter * (1f - expandProgress);
            float gx = centerX + m.f2037, gy = centerY + m.f2028;
            float scale = m.f2000 * m.f2018 * (1f + smoothedBeat * .15f) * enter;
            float r = radius * scale;
            gift.bounds.set(gx - r, gy - r, gx + r, gy + r);
            bubblePaint.setShader(new RadialGradient(gx - r * .25f, gy - r * .35f, Math.max(1, r * 1.45f),
                    new int[]{Theme.multAlpha(gift.color, .45f), Theme.multAlpha(gift.color, .70f), Theme.multAlpha(gift.color, .95f)},
                    new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP));
            bubblePaint.setAlpha(Math.round(255 * alpha));
            canvas.drawCircle(gx, gy, r, bubblePaint);
            if (gift.emojiDrawable != null) {
                int e = AndroidUtilities.dp(31) / 2;
                canvas.save(); canvas.translate(gx, gy); canvas.scale(scale, scale);
                gift.emojiDrawable.setBounds(-e, -e, e, e);
                gift.emojiDrawable.setAlpha(Math.round(255 * alpha));
                gift.emojiDrawable.draw(canvas);
                canvas.restore();
            }
        }
        canvas.restore();
        if (isShown()) postInvalidateOnAnimation();
    }

    private void cancelGesture() {
        if (selected != null) {
            CherryGiftMotion m = motion.get(selected.id);
            if (m != null && dragging) m.m2046(0, 0, AndroidUtilities.dp(1400));
        }
        selected = null; dragging = false;
        if (velocity != null) { velocity.recycle(); velocity = null; }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            cancelGesture();
            if (!active || expandProgress >= 1f || collapseProgress <= 0f || event.getY() > expandedY) return false;
            selected = getGiftUnder(event.getX(), event.getY());
            if (selected == null) return false;
            downX = event.getX(); downY = event.getY();
            offsetX = downX - selected.bounds.centerX(); offsetY = downY - selected.bounds.centerY();
            velocity = VelocityTracker.obtain(); velocity.addMovement(event);
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        if (selected == null) return false;
        if (velocity != null) velocity.addMovement(event);
        if (action == MotionEvent.ACTION_MOVE) {
            if (!dragging && Math.hypot(event.getX() - downX, event.getY() - downY) > ViewConfiguration.get(getContext()).getScaledTouchSlop()) dragging = true;
            if (dragging) {
                CherryGiftMotion m = motion.get(selected.id);
                if (m != null) m.m2055(event.getX() - centerX - offsetX, event.getY() - centerY - offsetY);
                invalidate();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            Gift result = selected;
            if (dragging && velocity != null) {
                velocity.computeCurrentVelocity(1000, AndroidUtilities.dp(1400));
                CherryGiftMotion m = motion.get(result.id);
                if (m != null) m.m2046(velocity.getXVelocity(), velocity.getYVelocity(), AndroidUtilities.dp(1400));
                dragging = false;
            } else {
                performClick(); onGiftClick(result);
            }
            cancelGesture(); return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) { cancelGesture(); return true; }
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }
}
