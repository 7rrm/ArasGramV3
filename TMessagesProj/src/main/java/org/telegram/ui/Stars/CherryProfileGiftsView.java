package org.telegram.ui.Stars;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.SparseArray;
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
    private final HashSet<Long> aliveMotionIds = new HashSet<>();
    private final SparseArray<GiftShaders> giftShaders = new SparseArray<>();

    // Reuse the same color gradients across frames, like Cherry's gift paints.
    // The geometry is normalized, so canvas scaling still controls each glow.
    private static final class GiftShaders {
        final Shader musicGlow, bubbleGlow, bubble;
        GiftShaders(int color) {
            musicGlow = new RadialGradient(0f, 0f, 1f,
                    new int[]{Theme.multAlpha(color, .65f), Theme.multAlpha(color, .3f), Theme.multAlpha(color, 0f)},
                    new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP);
            bubbleGlow = new RadialGradient(0f, 0f, 1.45f,
                    new int[]{Theme.multAlpha(color, .35f), Theme.multAlpha(color, .12f), Theme.multAlpha(color, 0f)},
                    new float[]{0f, .65f, 1f}, Shader.TileMode.CLAMP);
            bubble = new RadialGradient(-.25f, -.35f, 1.45f,
                    new int[]{Theme.multAlpha(color, .45f), Theme.multAlpha(color, .70f), Theme.multAlpha(color, .95f)},
                    new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP);
        }
    }

    private GiftShaders shadersFor(int color) {
        GiftShaders shaders = giftShaders.get(color);
        if (shaders == null) {
            // Bound the cache if a long-lived profile is updated repeatedly.
            if (giftShaders.size() >= 128) giftShaders.clear();
            giftShaders.put(color, shaders = new GiftShaders(color));
        }
        return shaders;
    }
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint musicGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sparklePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bubblePath = new Path();
    private float orbitAngle, orbitVelocity, orbitRx, orbitRy, lastOrbitAngle;
    private long lastOrbitTime;
    private boolean orbitDragging;
    private float expandedY, maxExpandedY, centerX, centerY, time;
    private float beat, smoothedBeat, musicPresence, actionModeProgress;
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
        glassPaint.setShader(new RadialGradient(-.38f, -.55f, 1.65f,
                new int[]{1442840575, 285212671, 16777215, 822083583}, new float[]{0f, .25f, .65f, 1f}, Shader.TileMode.CLAMP));
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(.035f);
        edgePaint.setShader(new LinearGradient(-1f, -1f, 1f, 1f,
                new int[]{-285212673, 905969663, -1143293697, -1593835521},
                new float[]{0f, .4f, .75f, 1f}, Shader.TileMode.CLAMP));
        sparklePaint.setColor(Color.WHITE);
        sparklePaint.setStrokeWidth(.012f);
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
            if (strength > beat + .25f && collapseProgress >= .8f && expandProgress <= .1f && actionModeProgress <= .1f) {
                float impulse = AndroidUtilities.dp(150) * strength * getMusicVisibility();
                for (CherryGiftMotion m : motion.values()) {
                    if (!m.f2027) {
                        float length = (float) Math.hypot(m.f2037, m.f2028);
                        m.m2050(length > 1f ? m.f2037 / length : 0f,
                                length > 1f ? m.f2028 / length : -1f, impulse, strength);
                    }
                }
            }
            beat = strength;
            lastSample = SystemClock.uptimeMillis();
            invalidate();
        }
    }

    public float getBeatLevel() { return smoothedBeat; }
    public float getMusicPresence() { return musicPresence; }
    public float getMusicVisibility() {
        if (!active || !isShown()) return 0f;
        return Math.max(0f, Math.min(1f, (collapseProgress - .5f) / .4f))
                * (1f - expandProgress) * (1f - actionModeProgress);
    }
    @Override public void setActionBarActionMode(float progress) {
        super.setActionBarActionMode(progress);
        actionModeProgress = Math.max(0f, Math.min(1f, progress));
    }
    public boolean isMusicPlaying() { return musicPlaying(); }
    public void setAnimationFrameListener(Runnable listener) { animationFrameListener = listener; }

    private void syncMotion() {
        aliveMotionIds.clear();
        for (int i = 0; i < gifts.size(); i++) {
            Gift gift = gifts.get(i);
            aliveMotionIds.add(gift.id);
            CherryGiftMotion item = motion.get(gift.id);
            if (item == null) {
                motion.put(gift.id, new CherryGiftMotion(gift.id, i, gifts.size()));
            } else {
                item.m2044(gift.id, i, gifts.size());
            }
        }
        motion.keySet().retainAll(aliveMotionIds);
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        time += dt;
        if (!orbitDragging) {
            orbitAngle += orbitVelocity * dt;
            orbitVelocity *= (float) Math.exp(-dt / .7f);
            if (Math.abs(orbitVelocity) < .01f) orbitVelocity = 0f;
        }
        boolean playing = musicPlaying();
        float presenceTarget = playing ? 1f : 0f;
        musicPresence += (presenceTarget - musicPresence) * (1f - (float)Math.exp(-dt / (playing ? .22f : .28f)));
        float targetBeat = playing && now - lastSample < 350 ? beat : 0f;
        if (!playing || now - lastSample >= 350) beat = 0f;
        smoothedBeat += (targetBeat - smoothedBeat) * (1f - (float)Math.exp(-dt / (targetBeat > smoothedBeat ? .045f : .2f)));
        if (animationFrameListener != null) animationFrameListener.run();
        if (gifts.isEmpty() || expandProgress >= 1f || collapseProgress <= 0f) {
            if ((playing || smoothedBeat > .001f || musicPresence > .001f) && isShown()) postInvalidateOnAnimation();
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
        orbitRx = rx; orbitRy = ry;
        float visible = Math.max(0f, Math.min(1f, (expandedY - (AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight())) / AndroidUtilities.dp(50f)));
        syncMotion();
        for (Gift gift : gifts) {
            CherryGiftMotion m = motion.get(gift.id);
            m.m2052(dt, false);
            m.m2057(time, orbitAngle, rx, ry);
            m.m2056();
            m.m2045(dt, radius, orbitDragging || orbitVelocity != 0f);
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
            float alpha = visible * enter * (1f - expandProgress) * (1f - actionModeProgress);
            float collapse = isOpening ? collapseProgress : Math.min(enter, collapseProgress);
            float interpolation = 1f - (float)Math.pow(1f - Math.max(0f, Math.min(1f, collapse)), 5);
            float realCenterX = x + width / 2f, realCenterY = y + height / 2f;
            float gx = realCenterX + (centerX + m.f2037 - realCenterX) * interpolation;
            float gy = realCenterY + (centerY + m.f2028 - realCenterY) * interpolation;
            float scale = (.3f + .7f * interpolation) * (.5f + .5f * enter) * m.f2000 * m.f2018
                    * (1f + smoothedBeat * .15f * getMusicVisibility());
            float r = radius * scale;
            gift.bounds.set(gx - r, gy - r, gx + r, gy + r);
            if (r > .01f && alpha > 0f) {
                canvas.save();
                canvas.translate(gx, gy);
                canvas.scale(r, r);
                float musicVisibility = getMusicVisibility();
                GiftShaders shaders = shadersFor(gift.color);
                if (musicPresence > .001f && musicVisibility > .001f) {
                    float glowRadius = 1.65f + smoothedBeat * .6f;
                    musicGlowPaint.setShader(shaders.musicGlow);
                    musicGlowPaint.setAlpha(Math.round(255f * alpha * musicVisibility * musicPresence * (smoothedBeat * .75f + .25f)));
                    canvas.save(); canvas.scale(glowRadius, glowRadius);
                    canvas.drawCircle(0f, 0f, 1f, musicGlowPaint); canvas.restore();
                }
                bubbleGlowPaint.setShader(shaders.bubbleGlow);
                bubbleGlowPaint.setAlpha(Math.round(255 * alpha));
                canvas.drawCircle(0f, 0f, 1.45f, bubbleGlowPaint);
                bubblePaint.setShader(shaders.bubble);
                bubblePaint.setAlpha(Math.round(255 * alpha));
                m.m2048(time);
                bubblePath.rewind();
                bubblePath.moveTo(m.f2010[0], m.f2016[0]);
                for (int k = 0; k < 16; k++) {
                    int prev = (k + 15) % 16, next = (k + 1) % 16, after = (k + 2) % 16;
                    float x0 = m.f2010[k], y0 = m.f2016[k], x1 = m.f2010[next], y1 = m.f2016[next];
                    bubblePath.cubicTo(x0 + (x1 - m.f2010[prev]) / 6f, y0 + (y1 - m.f2016[prev]) / 6f,
                            x1 - (m.f2010[after] - x0) / 6f, y1 - (m.f2016[after] - y0) / 6f, x1, y1);
                }
                bubblePath.close();
                canvas.drawPath(bubblePath, bubblePaint);
                glassPaint.setAlpha(Math.round(255 * alpha));
                canvas.drawPath(bubblePath, glassPaint);
                canvas.save(); canvas.clipPath(bubblePath);
                float seed = (gift.id & 1023) * .017f;
                for (int k = 0; k < 16; k++) {
                    float phase = k * 2.399963f + seed;
                    float drift = k % 2 == 0 ? .18f : -.13f;
                    float dist = ((float)Math.sin(1.7f * phase + time * .45f) * .5f + .5f) * .23f + .62f;
                    float px = (float)Math.cos(drift * time + phase) * dist;
                    float py = (float)Math.sin(drift * time + phase) * dist;
                    float twinkle = ((float)Math.sin(time * 1.9f + 2f * phase) * .5f + .5f) * .55f + .45f;
                    float starSize = (k % 4 == 0 ? .043f : .026f) * (.2f * twinkle + .8f);
                    sparklePaint.setAlpha((int)(45f * alpha * twinkle));
                    canvas.drawCircle(px, py, 2.8f * starSize, sparklePaint);
                    sparklePaint.setAlpha((int)(245f * alpha * twinkle));
                    canvas.drawCircle(px, py, starSize, sparklePaint);
                    if (k % 4 == 0) {
                        float arm = starSize * 1.9f;
                        canvas.drawLine(px - arm, py, px + arm, py, sparklePaint);
                        canvas.drawLine(px, py - arm, px, py + arm, sparklePaint);
                    }
                }
                canvas.restore();
                edgePaint.setAlpha((int)((smoothedBeat * 45f + 210f) * alpha));
                canvas.drawPath(bubblePath, edgePaint);
                canvas.restore();
            }
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

    public boolean canStartOrbit(float x, float y) {
        if (!active || gifts.isEmpty() || expandProgress > .1f || collapseProgress < .8f || y > expandedY || orbitRx <= 0 || orbitRy <= 0) return false;
        if (getGiftUnder(x, y) != null) return false;
        float ax = avatar.getX(), ay = avatar.getY();
        if (x >= ax && x <= ax + avatar.getWidth() * avatar.getScaleX() && y >= ay && y <= ay + avatar.getHeight() * avatar.getScaleY()) return false;
        return Math.hypot((x - centerX) / orbitRx, (y - centerY) / orbitRy) < 2.6f;
    }

    private float orbitAt(float x, float y) {
        return (float)Math.atan2((y - centerY) / orbitRy, (x - centerX) / orbitRx);
    }

    public void beginOrbit(float x, float y, long t) {
        orbitDragging = true; orbitVelocity = 0; lastOrbitAngle = orbitAt(x, y); lastOrbitTime = t;
    }

    public void moveOrbit(float x, float y, long t) {
        if (!orbitDragging) return;
        float angle = orbitAt(x, y);
        float delta = (float)Math.atan2(Math.sin(angle - lastOrbitAngle), Math.cos(angle - lastOrbitAngle));
        orbitAngle += delta;
        float seconds = (t - lastOrbitTime) / 1000f;
        if (seconds > 0) orbitVelocity = .5f * orbitVelocity + .5f * delta / seconds;
        lastOrbitAngle = angle; lastOrbitTime = t; invalidate();
    }

    public void endOrbit(long t, boolean cancelled) {
        orbitDragging = false;
        if (cancelled || t - lastOrbitTime > 80) orbitVelocity = 0;
        orbitVelocity = Math.max(-12f, Math.min(12f, orbitVelocity));
        invalidate();
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
