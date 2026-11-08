package org.telegram.ui.Components.blur3.drawable;

import android.graphics.Canvas;

import androidx.annotation.NonNull;

import org.telegram.utils.glass.source.GlassSource;
import org.telegram.utils.glass.source.GlassSourceWrapped;

public class BlurredBackgroundDrawableSource extends BlurredBackgroundDrawable {
    private final GlassSource source;

    public BlurredBackgroundDrawableSource(GlassSource source) {
        this.source = source;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        drawSource(canvas, source);
    }

    @Override
    public GlassSource getSource() {
        return source;
    }
}
