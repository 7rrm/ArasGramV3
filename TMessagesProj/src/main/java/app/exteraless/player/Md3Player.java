package app.exteraless.player;

import android.content.Context;
import android.os.Bundle;

import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AudioPlayerAlert;

import app.exteraless.appearance.AppearanceConfig;

public final class Md3Player {

    private Md3Player() {
    }

    public static boolean enabled() {
        return AppearanceConfig.md3Player();
    }

    public static boolean miniEnabled() {
        return AppearanceConfig.md3MiniPlayer();
    }

    /** Voice-tagged audio except OGG/Opus voice notes and view-once messages. */
    public static boolean isSupportedVoice(MessageObject messageObject) {
        if (messageObject == null || !messageObject.isVoice() || messageObject.isVoiceOnce()
                || messageObject.isRoundVideo() || messageObject.isVideo()) return false;
        org.telegram.tgnet.TLRPC.Document document = messageObject.getDocument();
        if (document == null || document.mime_type == null) return false;
        String mime = document.mime_type.trim().toLowerCase(java.util.Locale.ROOT);
        String name = org.telegram.messenger.FileLoader.getDocumentFileName(document);
        name = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        // These aliases/extensions are used for regular Telegram voice notes.
        if (mime.contains("ogg") || mime.contains("opus") || name.endsWith(".ogg")
                || name.endsWith(".oga") || name.endsWith(".opus")) return false;
        // Unknown binary with no recognizable audio name must stay on the
        // native voice path, rather than accidentally treating a voice note as music.
        return mime.startsWith("audio/") || (mime.equals("application/octet-stream")
                && (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac")
                || name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".wma")));
    }

    public static boolean supports(MessageObject messageObject) {
        return messageObject != null && (messageObject.isMusic() || isSupportedVoice(messageObject));
    }

    private static boolean handles(MessageObject messageObject) {
        return enabled() && supports(messageObject);
    }

    public static BottomSheet create(Context context, Theme.ResourcesProvider resourcesProvider) {
        if (handles(MediaController.getInstance().getPlayingMessageObject())) {
            if (PlayerSheet.instance != null) {
                PlayerSheet.instance.dismissImmediately();
            }
            return new PlayerSheet(context, resourcesProvider);
        }
        return new AudioPlayerAlert(context, resourcesProvider);
    }

    public static void open(BaseFragment fragment, PlayerMiniView source) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (messageObject == null) {
            return;
        }
        BottomSheet sheet = create(fragment.getParentActivity(), fragment.getResourceProvider());
        if (sheet instanceof PlayerSheet && source != null && source.canTransition()) {
            ((PlayerSheet) sheet).setTransitionSource(source);
        }
        fragment.showDialog(sheet);
    }

    public static boolean hidesContextPlayer(BaseFragment fragment, MessageObject messageObject) {
        if (!miniEnabled() || fragment == null || !supports(messageObject)) {
            return false;
        }
        Bundle args = fragment.getArguments();
        return args != null && args.getBoolean("hasMainTabs", false);
    }
}
