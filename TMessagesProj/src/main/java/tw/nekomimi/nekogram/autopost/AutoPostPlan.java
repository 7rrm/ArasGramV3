package tw.nekomimi.nekogram.autopost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** Pure iteration logic; this class never sends messages or schedules work. */
public final class AutoPostPlan {
    public enum Repeat { ONCE, COUNT, CONTINUOUS }

    public static final class Step {
        public final long dialogId;
        public final String message;
        public final int messageIndex;
        /** Delay before this step, in milliseconds. */
        public final long delayMs;
        public final int round;

        private Step(long dialogId, String message, int messageIndex, long delayMs, int round) {
            this.dialogId = dialogId;
            this.message = message;
            this.messageIndex = messageIndex;
            this.delayMs = delayMs;
            this.round = round;
        }
    }

    private final List<Long> dialogs;
    private final List<String> messages;
    private final long messageDelayMs;
    private final long dialogDelayMs;
    private final Repeat repeat;
    private final int rounds;
    private int dialogIndex;
    private int messageIndex;
    private int round = 1;
    private boolean first = true;
    private boolean finished;

    public AutoPostPlan(List<Long> dialogs, List<String> messages, long messageDelaySeconds,
                        long dialogDelaySeconds, Repeat repeat, int rounds) {
        if (dialogs == null || messages == null || repeat == null) throw new IllegalArgumentException("Missing settings");
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : dialogs) {
            if (id == null || id == 0) throw new IllegalArgumentException("Invalid dialog");
            unique.add(id);
        }
        if (unique.isEmpty()) throw new IllegalArgumentException("No dialogs");
        if (messages.isEmpty() || messages.size() > 10) throw new IllegalArgumentException("One to ten messages required");
        ArrayList<String> checked = new ArrayList<>();
        for (String text : messages) {
            if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("Empty message");
            checked.add(text);
        }
        if (messageDelaySeconds < 0 || dialogDelaySeconds < 0 ||
            messageDelaySeconds > Long.MAX_VALUE / 1000 || dialogDelaySeconds > Long.MAX_VALUE / 1000) {
            throw new IllegalArgumentException("Invalid delay");
        }
        if (repeat == Repeat.COUNT && rounds < 1) throw new IllegalArgumentException("Invalid rounds");
        this.dialogs = Collections.unmodifiableList(new ArrayList<>(unique));
        this.messages = Collections.unmodifiableList(checked);
        this.messageDelayMs = messageDelaySeconds * 1000;
        this.dialogDelayMs = dialogDelaySeconds * 1000;
        this.repeat = repeat;
        this.rounds = rounds;
    }

    public Step next() {
        if (finished) return null;
        long delay = first ? 0 : messageIndex == 0 ? dialogDelayMs : messageDelayMs;
        first = false;
        Step step = new Step(dialogs.get(dialogIndex), messages.get(messageIndex), messageIndex, delay, round);
        if (++messageIndex == messages.size()) {
            messageIndex = 0;
            if (++dialogIndex == dialogs.size()) {
                dialogIndex = 0;
                if (repeat == Repeat.ONCE || repeat == Repeat.COUNT && round >= rounds) {
                    finished = true;
                } else if (round < Integer.MAX_VALUE) {
                    round++;
                }
            }
        }
        return step;
    }
}
