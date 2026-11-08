package org.telegram.ui.Components.blur3.drawable;

import androidx.annotation.RequiresApi;

import org.telegram.utils.glass.source.GlassSource;

/** 13.2.1: upstream BlurredBackgroundDrawable now owns the render-node logic; this is a thin wrapper. */
@RequiresApi(api = 29)
public class BlurredBackgroundDrawableRenderNode extends BlurredBackgroundDrawable {

    public BlurredBackgroundDrawableRenderNode(GlassSource source) {
        super(source);
    }
}
