package org.telegram.utils.glass.source;

import android.graphics.Canvas;

import androidx.annotation.NonNull;

public interface GlassSource {
    void draw(@NonNull Canvas canvas, float left, float top, float right, float bottom);

    default long getGenerationId() {
        return 0;
    }

    default int getVisiblePositions(java.util.List<android.graphics.RectF> positions, int index, int expand) {
        return 0;
    }
}
