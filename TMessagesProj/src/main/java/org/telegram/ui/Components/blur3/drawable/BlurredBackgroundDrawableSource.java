package org.telegram.ui.Components.blur3.drawable;

import org.telegram.utils.glass.source.GlassSource;

/** 13.2.1: upstream BlurredBackgroundDrawable now owns the render-node logic; this is a thin wrapper. */
public class BlurredBackgroundDrawableSource extends BlurredBackgroundDrawable {

    public BlurredBackgroundDrawableSource(GlassSource source) {
        super(source);
    }
}
