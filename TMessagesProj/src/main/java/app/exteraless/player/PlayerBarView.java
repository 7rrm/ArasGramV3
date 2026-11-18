package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class PlayerBarView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public static final int HEIGHT_DP = 52;

    private final CoverImage cover;
    private final TextView titleView;
    private final TextView artistView;
    private final int originalTitleGravity;
    private final int originalArtistGravity;
    private final RingPlayButton playButton;
    private final ImageView nextButton;
    private final TextView speedButton;
    private final View voiceIcon;
    private final ImageView closeButton;
    private final PlayerIcon nextIcon = PlayerIcon.fill(PlayerIcon.NEXT, 22);
    private final PlayerIcon closeIcon = PlayerIcon.stroke(PlayerIcon.CLOSE, 20);
    private MessageObject current;

    public PlayerBarView(Context context, Theme.ResourcesProvider resourcesProvider, Runnable onOpen, Runnable onClose) {
        super(context);
        PlayerColors theme = PlayerColors.fromSeed(PlayerColors.fallbackSeed(resourcesProvider), PlayerColors.isDark(resourcesProvider));

        setBackground(Theme.getSelectorDrawable(false));
        setOnClickListener(v -> onOpen.run());
        setContentDescription(getString(R.string.OEPlayerOpen));

        cover = new CoverImage(context, 18);
        cover.setRadius(dp(10));
        addView(cover, LayoutHelper.createFrame(36, 36, Gravity.LEFT | Gravity.CENTER_VERTICAL, 12, 0, 0, 0));
        voiceIcon = new View(context) {
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                float d = AndroidUtilities.density, x = getWidth() / 2f, y = getHeight() / 2f;
                paint.setColor(theme.onPrimaryContainer);
                paint.setStyle(Paint.Style.FILL);
                canvas.save(); canvas.rotate(-18, x - 6*d, y + 7*d);
                canvas.drawOval(x - 10*d, y + 4*d, x - 3*d, y + 9*d, paint);
                canvas.restore();
                canvas.drawRoundRect(x - 4*d, y - 10*d, x - 2*d, y + 7*d, d, d, paint);
                float[] lengths = {7, 6, 9, 6, 7};
                for (int i = 0; i < lengths.length; i++) {
                    float top = y - 9*d + i*4*d;
                    canvas.drawRoundRect(x + d, top, x + (1 + lengths[i])*d, top + 2.2f*d, d, d, paint);
                }
            }
        };
        voiceIcon.setBackground(Theme.createRoundRectDrawable(dp(10), theme.primaryContainer));
        voiceIcon.setVisibility(GONE);
        addView(voiceIcon, LayoutHelper.createFrame(36, 36, Gravity.LEFT | Gravity.CENTER_VERTICAL, 12, 0, 0, 0));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        titleView = new TextView(context);
        originalTitleGravity = titleView.getGravity();
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        artistView = new TextView(context);
        originalArtistGravity = artistView.getGravity();
        artistView.setSingleLine(true);
        artistView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(artistView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));
        // ArasGram: نصّا العنوان/الفنان يتبعان مفتاح «تصغير المقاسات»
        // (15/13 عادي، 12/10 مصغّر) — ارتفاع الشريط لا يتغيّر.
        applyTextSizes();
        addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 60, 0, 6 + 40 * 3 + 4, 0));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        playButton = new RingPlayButton(context, 15, 2.5f, 16, 1.1f, 10);
        playButton.setOnClickListener(v -> {
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
        buttons.addView(playButton, LayoutHelper.createLinear(40, 40));
        nextButton = new ImageView(context);
        nextButton.setScaleType(ImageView.ScaleType.CENTER);
        nextButton.setImageDrawable(nextIcon);
        nextButton.setContentDescription(getString(R.string.Next));
        nextButton.setOnClickListener(v -> MediaController.getInstance().playNextMessage());
        buttons.addView(nextButton, LayoutHelper.createLinear(40, 40));
        speedButton = new TextView(context);
        speedButton.setGravity(Gravity.CENTER);
        speedButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        speedButton.setTypeface(AndroidUtilities.bold());
        speedButton.setContentDescription(getString(R.string.AccDescrPlayerSpeed));
        speedButton.setVisibility(GONE);
        speedButton.setOnClickListener(v -> {
            float old = MediaController.getInstance().getPlaybackSpeed(false);
            float[] speeds = {1f, 1.5f, 2f, 0.5f};
            int index = -1;
            for (int i = 0; i < speeds.length; i++) if (Math.abs(old - speeds[i]) < 0.05f) { index = i; break; }
            MediaController.getInstance().setPlaybackSpeed(false, speeds[(index + 1) % speeds.length]);
            updateSpeed();
        });
        buttons.addView(speedButton, LayoutHelper.createLinear(40, 40));
        closeButton = new ImageView(context);
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        closeButton.setImageDrawable(closeIcon);
        closeButton.setContentDescription(getString(R.string.AccDescrClosePlayer));
        closeButton.setOnClickListener(v -> onClose.run());
        buttons.addView(closeButton, LayoutHelper.createLinear(40, 40));
        addView(buttons, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 6, 0));

        int text = Theme.getColor(Theme.key_inappPlayerPerformer, resourcesProvider);
        int secondary = Theme.getColor(Theme.key_inappPlayerTitle, resourcesProvider);
        int icons = Theme.getColor(Theme.key_inappPlayerClose, resourcesProvider);
        titleView.setTextColor(text);
        artistView.setTextColor(ColorUtils.setAlphaComponent(secondary, 0xbf));
        nextIcon.setColor(icons);
        speedButton.setTextColor(icons);
        closeIcon.setColor(icons);
        int ripple = icons & 0x19ffffff;
        nextButton.setBackground(Theme.createSelectorDrawable(ripple, Theme.RIPPLE_MASK_CIRCLE_20DP));
        closeButton.setBackground(Theme.createSelectorDrawable(ripple, Theme.RIPPLE_MASK_CIRCLE_20DP));
        playButton.setBackground(Theme.createSelectorDrawable(ripple, Theme.RIPPLE_MASK_CIRCLE_20DP));
        int accent = Theme.getColor(Theme.key_inappPlayerPlayPause, resourcesProvider);
        playButton.setColors(accent, ColorUtils.setAlphaComponent(accent, 51), accent);
        cover.setColors(theme.primaryContainer, theme.onPrimaryContainer);
    }

    /** مفاتيح «تصغير المقاسات»: same sizes as the stock music bar. */
    private void applyTextSizes() {
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, tw.nekomimi.nekogram.MeeroTopBar.musicTitleDp());
        artistView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, tw.nekomimi.nekogram.MeeroTopBar.musicSubDp());
    }

    private final tw.nekomimi.nekogram.MeeroTopBar.Listener meeroTopBarListener = this::applyTextSizes;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        tw.nekomimi.nekogram.MeeroTopBar.addListener(meeroTopBarListener);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.messagePlayingSpeedChanged);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter nc = NotificationCenter.getInstance(a);
            nc.addObserver(this, NotificationCenter.messagePlayingDidStart);
            nc.addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            nc.addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            nc.addObserver(this, NotificationCenter.fileLoaded);
            nc.addObserver(this, NotificationCenter.audioInfoLoaded);
        }
        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        tw.nekomimi.nekogram.MeeroTopBar.removeListener(meeroTopBarListener);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.messagePlayingSpeedChanged);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter nc = NotificationCenter.getInstance(a);
            nc.removeObserver(this, NotificationCenter.messagePlayingDidStart);
            nc.removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            nc.removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            nc.removeObserver(this, NotificationCenter.fileLoaded);
            nc.removeObserver(this, NotificationCenter.audioInfoLoaded);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingSpeedChanged) {
            updateSpeed();
        } else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            playButton.progressChanged();
        } else if (id == NotificationCenter.audioInfoLoaded) {
            MessageObject mo = current;
            if (mo != null && PlayerArt.isPlaying(mo)) {
                cover.setMessage(mo);
            }
        } else if (id == NotificationCenter.fileLoaded) {
            MessageObject mo = current;
            if (mo != null && mo.getDocument() != null && TextUtils.equals((String) args[0], FileLoader.getAttachFileName(mo.getDocument()))) {
                cover.setMessage(mo);
            }
        } else {
            update();
        }
    }

    private void updateSpeed() {
        float speed = MediaController.getInstance().getPlaybackSpeed(false);
        speedButton.setText(String.format(java.util.Locale.US, "%s×", speed == (int) speed ? Integer.toString((int) speed) : Float.toString(speed)));
    }

    public void update() {
        MessageObject mo = MediaController.getInstance().getPlayingMessageObject();
        if (!Md3Player.supports(mo)) {
            return;
        }
        current = mo;
        boolean voice = Md3Player.voiceBar(mo);
        titleView.setGravity(voice ? Gravity.CENTER : originalTitleGravity);
        artistView.setGravity(voice ? Gravity.CENTER : originalArtistGravity);
        voiceIcon.setVisibility(voice ? VISIBLE : GONE);
        cover.setVisibility(voice ? GONE : VISIBLE);
        nextButton.setVisibility(voice ? GONE : VISIBLE);
        speedButton.setVisibility(voice ? VISIBLE : GONE);
        if (voice) updateSpeed();
        titleView.setText(mo.getMusicTitle());
        artistView.setText(mo.getMusicAuthor());
        cover.setMessage(mo);
        playButton.setMessage(mo);
        boolean paused = MediaController.getInstance().isMessagePaused();
        playButton.setPlaying(!paused, isAttachedToWindow());
        playButton.setContentDescription(getString(paused ? R.string.AccActionPlay : R.string.AccActionPause));
    }
}
