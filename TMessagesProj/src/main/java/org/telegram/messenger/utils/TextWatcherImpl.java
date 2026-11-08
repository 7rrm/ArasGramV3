package org.telegram.messenger.utils;

import android.text.Editable;
import android.text.TextWatcher;

public interface TextWatcherImpl extends TextWatcher {
    default void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    default void onTextChanged(CharSequence s, int start, int before, int count) {}

    default void afterTextChanged(Editable s) {}
}
