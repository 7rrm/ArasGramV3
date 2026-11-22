package tw.nekomimi.nekogram;

import android.icu.text.BreakIterator;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Locale;

/**
 * MeeroX v230 (owner order): «تنسيق الإرسال» - pick a Telegram text style
 * once in Settings > Fonts and every outgoing text is sent with that
 * entity wrapped over its whole length. 0 = default (off). Values mirror
 * the format popup: 1 Bold, 2 Italic, 3 Underline, 4 Strike, 5 Spoiler,
 * 6 Quote, 7 Mono, 8 Code block. Styling is cosmetic and must never block
 * a send, so every failure path returns silently.
 */
public final class MeeroMessageStyler {

    private MeeroMessageStyler() {
    }

    public static int style() {
        try {
            return NekoConfig.meeroSendTextStyle.Int();
        } catch (Throwable ignore) {
            return 0;
        }
    }

    // An absent per-chat setting inherits the existing global send style.
    // Explicit default (0) disables the global style for this chat only.
    private static String key(int account, long dialogId) {
        return "meeroChatTextStyle_" + account + "_" + dialogId;
    }

    public static int chatStyle(int account, long dialogId) {
        return NekoConfig.getPreferences().getInt(key(account, dialogId), -1);
    }

    public static void setChatStyle(int account, long dialogId, int value) {
        if (value < 0) {
            NekoConfig.getPreferences().edit().remove(key(account, dialogId)).apply();
        } else if (value <= 9) {
            NekoConfig.getPreferences().edit().putInt(key(account, dialogId), value).apply();
        }
    }

    public static int style(int account, long dialogId) {
        int value = chatStyle(account, dialogId);
        return value >= 0 && value <= 9 ? value : style();
    }

    /** Apply the chat override, otherwise retain the existing global behavior. */
    public static void applyTo(SendMessagesHelper.SendMessageParams params, int account) {
        try {
            if (params == null || params.retryMessageObject != null) {
                return;
            }
            final int s = style(account, params.peer);
            if (s <= 0) {
                return;
            }
            final String text = params.message != null ? params.message : params.caption;
            if (text == null || text.isEmpty()) {
                return;
            }
            if (s == 9) {
                int limit = params.message != null
                        ? MessagesController.getInstance(account).getMaxMessageLength()
                        : MessagesController.getInstance(account).getCaptionMaxLengthLimit();
                separateText(params, text, limit);
                return;
            }
            final TLRPC.MessageEntity e = entityFor(s);
            if (e == null) {
                return;
            }
            e.offset = 0;
            e.length = text.length();
            if (params.entities == null) {
                params.entities = new ArrayList<>();
            }
            params.entities.add(e);
        } catch (Throwable ignore) {
        }
    }

    /** Insert spaces between grapheme clusters, preserving whitespace and semantic entities. */
    private static void separateText(SendMessagesHelper.SendMessageParams params, String text, int limit) {
        BreakIterator breaks = BreakIterator.getCharacterInstance(Locale.ROOT);
        breaks.setText(text);
        int length = text.length();
        boolean[] protectedBoundary = new boolean[length + 1];
        if (params.entities != null) {
            for (TLRPC.MessageEntity e : params.entities) {
                if (e == null || e instanceof TLRPC.TL_messageEntityBold || e instanceof TLRPC.TL_messageEntityItalic
                        || e instanceof TLRPC.TL_messageEntityUnderline || e instanceof TLRPC.TL_messageEntityStrike
                        || e instanceof TLRPC.TL_messageEntitySpoiler || e instanceof TLRPC.TL_messageEntityBlockquote) {
                    continue;
                }
                int from = Math.max(0, e.offset + 1);
                int to = Math.min(length, e.offset + e.length);
                for (int i = from; i < to; i++) protectedBoundary[i] = true;
            }
        }
        // Keep normal URLs clickable even when they were not supplied as entities.
        java.util.regex.Matcher urls = android.util.Patterns.WEB_URL.matcher(text);
        while (urls.find()) {
            for (int i = urls.start() + 1; i < urls.end(); i++) protectedBoundary[i] = true;
        }
        StringBuilder result = new StringBuilder(length * 2);
        int[] before = new int[length + 1];
        int[] after = new int[length + 1];
        int previous = breaks.first();
        int next;
        while ((next = breaks.next()) != BreakIterator.DONE) {
            before[previous] = result.length();
            if (previous > 0 && !protectedBoundary[previous]
                    && !Character.isWhitespace(text.codePointBefore(previous))
                    && !Character.isWhitespace(text.codePointAt(previous))) {
                result.append(' ');
            }
            after[previous] = result.length();
            for (int i = previous; i < next; i++) {
                if (i > previous) before[i] = after[i] = result.length();
                result.append(text.charAt(i));
            }
            previous = next;
        }
        before[length] = after[length] = result.length();
        // Never send an over-limit transformed caption or a message from another send path.
        if (result.length() > limit) return;
        if (params.entities != null) {
            for (TLRPC.MessageEntity e : params.entities) {
                if (e == null || e.offset < 0 || e.length < 0 || e.offset > length || e.length > length - e.offset) continue;
                int end = e.offset + e.length;
                int start = after[e.offset];
                e.offset = start;
                e.length = Math.max(0, before[end] - start);
            }
        }
        if (params.message != null) params.message = result.toString();
        else params.caption = result.toString();
    }

    private static TLRPC.MessageEntity entityFor(int s) {
        switch (s) {
            case 1:
                return new TLRPC.TL_messageEntityBold();
            case 2:
                return new TLRPC.TL_messageEntityItalic();
            case 3:
                return new TLRPC.TL_messageEntityUnderline();
            case 4:
                return new TLRPC.TL_messageEntityStrike();
            case 5:
                return new TLRPC.TL_messageEntitySpoiler();
            case 6:
                return new TLRPC.TL_messageEntityBlockquote();
            case 7:
                return new TLRPC.TL_messageEntityCode();
            case 8: {
                final TLRPC.TL_messageEntityPre pre = new TLRPC.TL_messageEntityPre();
                pre.language = "";
                return pre;
            }
            default:
                return null;
        }
    }
}
