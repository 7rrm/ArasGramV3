package tw.nekomimi.nekogram;

/**
 * ArasGramX: المعرّفات المميّزة لإصدار ArasGramX.
 *
 * هذه الأرقام تأتي مباشرة من مطوّر النسخة و تُستعمل لإجبار عرض
 * علامة الكرز (Cherrygram-style verified cherry emoji) + النجوم
 * المتناثرة (radial particles) بجانب أسماء:
 *   - حساب المطور (Owner).
 *   - قناتي/ قناتي ثانية.
 *
 * معرّفات الحوار داخل التطبيق (dialogId):
 *   - المستخدم: موجب (نفس user_id الأصلي).
 *   - القناة/ المجموعة: سالب (أي -channel_id).
 *
 * أرقام chh الموحّدة لرمز الكرز (document_id على خوادم تيليجرام،
 * متاحة لأي عميل لأنها مستضافة على بوت Cherrygram الرسمي):
 *   - CHERRY_EMOJI_ID_VERIFIED       = 5449476181864779205L
 *   - CHERRY_EMOJI_ID_VERIFIED_BRA    = 5451850156318181341L
 */
public final class ArasGramConstants {

    private ArasGramConstants() {
    }

    // === المستخدم المميّز (المالك) ===
    /** معرّف حساب المطور على تيليجرام. */
    public static final long ArasGram_Owner = 5427469031L;

    // === القنوات المميّزة ===
    /**
     * القناة الأولى — أدخلها المستخدم بصيغة البوت API
     * (-1001607860920)؛ قمنا بنزع بادئة الـ -100 لتبقى
     * channel_id الأصلي و نستعملها مباشرة.
     */
    public static final long ArasGram_Channel = 1607860920L;

    /**
     * القناة الثانية — نفس المنطق: -1002551837124 → 2551837124.
     */
    public static final long ArasGram_Channel2 = 2551837124L;

    // === معرّفات إيموجي الكرز (مأخوذة من Constants.kt الخاص بـ chh) ===
    /** علامة الكرز الافتراضية المُتحقَّق منها. */
    public static final long CHERRY_EMOJI_ID_VERIFIED = 5199752274875796976L;
    /** علامة الكرز بنمط Bra — تُستعمل للمالك لتمييزه عن بقية المُتحقَّقين. */
    public static final long CHERRY_EMOJI_ID_VERIFIED_BRA = 5199752274875796976L;

    /**
     * هل المستخدم المُمرسِل هو المالك؟
     *
     * @param userId معرّف المستخدم المُمرسِل.
     * @return true إذا كان يطابق {@link #ArasGram_Owner}.
     */
    public static boolean isOwner(long userId) {
        return userId == ArasGram_Owner;
    }

    /**
     * هل القناة/ المجموعة ضمن قائمة الأقران المميّزين؟
     *
     * @param chatId معرّف القناة/ المجموعة (موجب).
     * @return true إذا كانت تطابق {@link #ArasGram_Channel} أو {@link #ArasGram_Channel2}.
     */
    public static boolean isSparkleChannel(long chatId) {
        return chatId == ArasGram_Channel || chatId == ArasGram_Channel2;
    }

    /**
     * هل يجب إجبار عرض الكرز لهذا الحوار؟
     *
     * @param dialogId معرّف الحوار (موجب للمستخدم، سالب للقناة).
     * @return true إذا كان ضمن قائمة المميّزين.
     */
    public static boolean isSparklePeer(long dialogId) {
        if (dialogId == 0) return false;
        if (dialogId > 0) {
            return dialogId == ArasGram_Owner;
        }
        long chatId = -dialogId;
        return chatId == ArasGram_Channel || chatId == ArasGram_Channel2;
    }
}
