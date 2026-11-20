package org.telegram.messenger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Shared merge rules for locally cached group tags and server search results. */
final class GroupTagsMerge {
    interface Entry<T> {
        long dialogId(T value);
        int messageId(T value);
        int date(T value);
        boolean isOwn(T value);
    }

    private static final class Key {
        final long dialogId;
        final int messageId;

        Key(long dialogId, int messageId) {
            this.dialogId = dialogId;
            this.messageId = messageId;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return dialogId == key.dialogId && messageId == key.messageId;
        }

        @Override public int hashCode() {
            return 31 * Long.hashCode(dialogId) + messageId;
        }
    }

    static <T> ArrayList<T> merge(List<T> server, List<T> local, Entry<T> entry) {
        ArrayList<T> result = new ArrayList<>();
        HashSet<Key> seen = new HashSet<>();
        // Server entries win even when the two versions have different dates.
        append(server, entry, seen, result);
        append(local, entry, seen, result);
        result.sort(Comparator.<T>comparingInt(entry::date).reversed()
                .thenComparing(Comparator.comparingInt(entry::messageId).reversed())
                .thenComparingLong(entry::dialogId));
        return result;
    }

    private static <T> void append(List<T> values, Entry<T> entry, HashSet<Key> seen, ArrayList<T> result) {
        for (T value : values) {
            if (!entry.isOwn(value) && seen.add(new Key(entry.dialogId(value), entry.messageId(value)))) {
                result.add(value);
            }
        }
    }

    private GroupTagsMerge() { }
}
