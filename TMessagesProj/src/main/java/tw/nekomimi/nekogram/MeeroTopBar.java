package tw.nekomimi.nekogram;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MeeroX v233: per-element sizing for the chat top bar.
 *
 * One master switch in Chat settings ("مقاسات وحجم خطوط الشريط العلوي"):
 *  - ON  (default): the MeeroX-tuned sizes (the ones we shipped during the
 *    tuning passes) are the 100% baseline, and every element can be scaled
 *    60%–160% live with a slider.
 *  - OFF: the conversation-start originals - the exact values the fork had
 *    before any of this session's top-bar tuning (glass title 17.5dp,
 *    status 13.5dp, pinned lines 14dp, single-line 15dp music bar at 36dp,
 *    translate 14dp). "باعتبار ممسوي شيء" - as if nothing was done.
 *
 * Consumers: ChatAvatarContainer (avatar/title/status), ActionBar (glass
 * capsules, back button), ChatActivity (pinned strip), FragmentContextView
 * (music bar), TranslateButton (translate line).
 */
public final class MeeroTopBar {

    private MeeroTopBar() {
    }

    /* ---- state ---- */

    public static boolean enabled() {
        try {
            return NekoConfig.meeroTopBarSizesOn.Bool();
        } catch (Throwable t) {
            return true;
        }
    }

    /** OFF = the pre-tuning (conversation-start) look. */
    public static boolean original() {
        return !enabled();
    }

    private static float k(tw.nekomimi.nekogram.config.ConfigItem item) {
        try {
            int v = item.Int();
            if (v < 60) v = 60;
            if (v > 160) v = 160;
            return v / 100f;
        } catch (Throwable t) {
            return 1f;
        }
    }

    public static float avatarK()  { return k(NekoConfig.meeroTopBarAvatarScale); }
    public static float titleK()   { return k(NekoConfig.meeroTopBarTitleScale); }
    public static float statusK()  { return k(NekoConfig.meeroTopBarStatusScale); }
    public static float capsuleK() { return k(NekoConfig.meeroTopBarCapsuleScale); }
    public static float backK()    { return k(NekoConfig.meeroTopBarBackScale); }
    public static float pinnedK()  { return k(NekoConfig.meeroTopBarPinnedScale); }
    public static float musicK()   { return k(NekoConfig.meeroTopBarMusicScale); }

    /* ---- header (ChatAvatarContainer + ActionBar capsules) ---- */

    public static float avatarDp()      { return original() ? 42f   : 42f * avatarK(); }
    public static float titleDp()       { return original() ? 17.5f : 15f * titleK(); }
    public static float statusDp()      { return original() ? 13.5f : 12f * statusK(); }
    public static float titleTopDp()    { return original() ? 1.66f : 5f * titleK(); }
    public static float subtitleTopDp() { return original() ? 23.66f : 23f * titleK(); }
    /** X offset of the text block after the avatar (glass layout). */
    public static float avatarLeftDp()  { return original() ? 49.66f : 49.66f * avatarK(); }

    /** Glass pill height behind avatar + name + status (ActionBar "s"). */
    public static float pillDp() {
        if (original()) return 46f;
        // the oval behind the avatar must always hug the picture
        return Math.max(46f * capsuleK(), avatarDp() + 8f);
    }

    /** Back-button capsule height. */
    public static float backPillDp() { return original() ? 46f : 46f * backK(); }

    /* ---- pinned strip (ChatActivity) ---- */

    public static float pinnedCounterDp() { return original() ? 14f : 12f * pinnedK(); }
    public static float pinnedTextDp()    { return original() ? 14f : 12.5f * pinnedK(); }
    public static float pinnedBarDp()     { return original() ? 48f : 48f * pinnedK(); }

    /* ---- music / audio player (FragmentContextView) ---- */

    /** Original = the pre-session single-line "author - title" bar. */
    public static boolean musicTwoLine() { return !original(); }
    public static float musicTitleDp()   { return original() ? 15f : 12f * musicK(); }
    public static float musicSubDp()     { return original() ? 13f : 11f * musicK(); }
    public static float musicBarDp()     { return original() ? 36f : 30f * musicK(); }
    public static float musicPlayDp()    { return original() ? 36f : 30f * musicK(); }

    /* ---- translate button (TranslateButton) ---- */

    /** Follows the chat-name line (that parity was the whole point). */
    public static float translateDp() { return original() ? 14f : titleDp(); }

    /* ---- live refresh ---- */

    public interface Listener {
        void onMeeroTopBarChanged();
    }

    private static final CopyOnWriteArrayList<WeakReference<Listener>> listeners = new CopyOnWriteArrayList<>();

    /** Register a listener that is strongly referenced by its owner view. */
    public static void addListener(Listener l) {
        listeners.add(new WeakReference<>(l));
    }

    /** Fire on every slider move / toggle: live views re-apply and relayout. */
    public static void notifyChanged() {
        for (WeakReference<Listener> r : listeners) {
            Listener l = r.get();
            if (l == null) {
                listeners.remove(r);
                continue;
            }
            try {
                l.onMeeroTopBarChanged();
            } catch (Throwable ignore) {
            }
        }
    }
}
