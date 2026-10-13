package tw.nekomimi.nekogram;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MeeroX v235: top-bar measurements - ONE switch, no scaling.
 *
 * ON (default) = the measurements we tuned and shrank during the session:
 *   glass title 15dp, status 12dp, titleTop 5 / subtitleTop 23, pinned
 *   lines 12 / 12.5dp on the 48dp bar, two-line music bar (12 / 11dp) on
 *   a 31dp strip, translate line 15dp.
 * OFF = the conversation-start originals - the exact values the fork had
 *   before any tuning (title 17.5dp, status 13.5dp, pinned 14dp,
 *   single-line 15dp music bar at 36dp, translate 14dp).
 *   "باعتبار ممسوي شيء" - as if nothing was done.
 *
 * Consumers: ChatAvatarContainer (avatar/title/status), ActionBar (glass
 * capsules), ChatActivity (pinned strip), FragmentContextView (music bar),
 * TranslateButton (translate line).
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

    /* ---- header (ChatAvatarContainer + ActionBar capsules) ---- */

    /** Avatar and both glass capsules are identical in the two looks. */
    public static float avatarDp()      { return 42f; }
    public static float pillDp()        { return 46f; }
    public static float backPillDp()    { return 46f; }
    /** X offset of the text block after the avatar (glass layout). */
    public static float avatarLeftDp()  { return 49.66f; }

    public static float titleDp()       { return original() ? 17.5f : 15f; }
    public static float statusDp()      { return original() ? 13.5f : 12f; }
    public static float titleTopDp()    { return original() ? 1.66f : 5f; }
    public static float subtitleTopDp() { return original() ? 23.66f : 23f; }

    /* ---- pinned strip (ChatActivity) ---- */

    public static float pinnedCounterDp() { return original() ? 14f : 12f; }
    public static float pinnedTextDp()    { return original() ? 14f : 12.5f; }
    public static float pinnedBarDp()     { return 48f; }

    /* ---- music / audio player (FragmentContextView) ---- */

    /** Original = the conversation-start single-line "author - title" bar. */
    public static boolean musicTwoLine() { return !original(); }
    public static float musicTitleDp()   { return original() ? 15f : 12f; }
    public static float musicSubDp()     { return original() ? 13f : 11f; }
    public static float musicBarDp()     { return original() ? 36f : 31f; }
    public static float musicPlayDp()    { return original() ? 36f : 31f; }

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

    /** Fire on toggle: live views re-apply and relayout. */
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
