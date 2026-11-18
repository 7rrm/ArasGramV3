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

    /** Opt-in for ordinary voice messages in OGG or MPEG; view-once stays native. */
    public static boolean voiceBar(MessageObject messageObject) {
        if (!tw.nekomimi.nekogram.NekoConfig.meeroVoiceBar.Bool() || messageObject == null
                || !messageObject.isVoice() || messageObject.isVoiceOnce() || messageObject.isRoundVideo()) return false;
        org.telegram.tgnet.TLRPC.Document d = messageObject.getDocument();
        return d != null && ("audio/ogg".equalsIgnoreCase(d.mime_type) || "audio/mpeg".equalsIgnoreCase(d.mime_type));
    }

    public static boolean supports(MessageObject messageObject) {
        return messageObject != null && (messageObject.isMusic() || voiceBar(messageObject));
    }

    private static boolean handles(MessageObject messageObject) {
        return (enabled() && messageObject != null && messageObject.isMusic()) || voiceBar(messageObject);
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
        if (!miniEnabled() || fragment == null || messageObject == null || !messageObject.isMusic()) {
            return false;
        }
        Bundle args = fragment.getArguments();
        return args != null && args.getBoolean("hasMainTabs", false);
    }
}
