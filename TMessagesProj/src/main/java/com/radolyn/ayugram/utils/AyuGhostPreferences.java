package com.radolyn.ayugram.utils;

import org.telegram.messenger.DialogObject;

import java.util.concurrent.ConcurrentHashMap;

import tw.nekomimi.nekogram.NekoConfig;

public class AyuGhostPreferences {
    public static final String ghostReadExclusionPrefix = "ghostModeReadExclusion_";
    public static final String ghostTypingExclusionPrefix = "ghostModeTypingExclusion_";

    public static final int EXCLUSION_NONE = 0;
    public static final int EXCLUSION_READ = 1;
    public static final int EXCLUSION_TYPING = 2;
    public static final int EXCLUSION_BOTH = 3;

    private static final ConcurrentHashMap<Long, Boolean> ghostModeReadExclusions = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Boolean> ghostModeTypingExclusions = new ConcurrentHashMap<>();

    public static void setGhostModeReadExclusion(long chatId, boolean value) {
        long key = Math.abs(chatId);
        ghostModeReadExclusions.put(key, value);
        NekoConfig.getPreferences().edit().putBoolean(ghostReadExclusionPrefix + key, value).apply();
    }

    public static boolean getGhostModeReadExclusion(long chatId) {
        long key = Math.abs(chatId);
        return ghostModeReadExclusions.computeIfAbsent(key, k ->
                NekoConfig.getPreferences().getBoolean(ghostReadExclusionPrefix + k, false)
        );
    }

    public static void setGhostModeTypingExclusion(long chatId, boolean value) {
        long key = Math.abs(chatId);
        ghostModeTypingExclusions.put(key, value);
        NekoConfig.getPreferences().edit().putBoolean(ghostTypingExclusionPrefix + key, value).apply();
    }

    public static boolean getGhostModeTypingExclusion(long chatId) {
        long key = Math.abs(chatId);
        return ghostModeTypingExclusions.computeIfAbsent(key, k ->
                NekoConfig.getPreferences().getBoolean(ghostTypingExclusionPrefix + k, false)
        );
    }

    // --- Global exclusions by dialog type (private chats vs groups) ---

    public static boolean isPrivateDialog(long dialogId) {
        return dialogId > 0 || DialogObject.isEncryptedDialog(dialogId);
    }

    private static int getScopeExclusions(long dialogId) {
        return isPrivateDialog(dialogId)
                ? NekoConfig.ghostPrivateExclusions.Int()
                : NekoConfig.ghostGroupExclusions.Int();
    }

    /**
     * Effective read exclusion for a dialog: the per-chat exclusion from the
     * swipe-back popup wins when set, otherwise the global private/group
     * setting from Ghost Mode settings applies.
     */
    public static boolean isReadExcluded(long dialogId) {
        if (getGhostModeReadExclusion(dialogId)) return true;
        return (getScopeExclusions(dialogId) & EXCLUSION_READ) != 0;
    }

    /**
     * Effective typing exclusion for a dialog: the per-chat exclusion from the
     * swipe-back popup wins when set, otherwise the global private/group
     * setting from Ghost Mode settings applies.
     */
    public static boolean isTypingExcluded(long dialogId) {
        if (getGhostModeTypingExclusion(dialogId)) return true;
        return (getScopeExclusions(dialogId) & EXCLUSION_TYPING) != 0;
    }

}
