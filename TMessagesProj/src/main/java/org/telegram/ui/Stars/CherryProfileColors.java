package org.telegram.ui.Stars;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import androidx.core.graphics.ColorUtils;
import org.telegram.messenger.MessagesController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.PeerColorActivity;

/** Profile palette adapted from Cherrygram 11.6.0 (GPL v2 or later). */
public class CherryProfileColors implements Theme.ResourcesProvider {

        private static final double NEUTRAL_CHROMA = 12;
        private static final double ACCENT_HUE_RANGE = Math.toRadians(40);
        private static final double SURFACE_AMOUNT = .1;
        private static final double SURFACE_CONTRAST = .06;
        private static final double ACCENT_LIGHTNESS = 86;
        private static final double ACCENT_MAX_CHROMA = 40;
        private static final float CARD_DARKEN = .10f;

        private static final SparseBooleanArray keptKeys = new SparseBooleanArray();
        private static final SparseBooleanArray onAccentKeys = new SparseBooleanArray();
        private static final SparseBooleanArray lightThemeKeys = new SparseBooleanArray();
        static {
            for (int key : Theme.keys_avatar_background) keptKeys.put(key, true);
            for (int key : Theme.keys_avatar_background2) keptKeys.put(key, true);
            for (int key : Theme.keys_avatar_nameInMessage) keptKeys.put(key, true);
            final int[] kept = {
                Theme.key_avatar_text,
                Theme.key_avatar_backgroundInProfileBlue,
                Theme.key_avatar_backgroundActionBarBlue,
                Theme.key_avatar_actionBarSelectorBlue,
                Theme.key_avatar_subtitleInProfileBlue,
                Theme.key_actionBarDefault,
                Theme.key_actionBarDefaultTitle,
                Theme.key_actionBarDefaultSubtitle,
                Theme.key_profile_title,
                Theme.key_profile_status,
                Theme.key_profile_verifiedBackground,
                Theme.key_profile_verifiedCheck,
                Theme.key_profile_actionIcon,
                Theme.key_profile_actionBackground,
                Theme.key_profile_actionPressedBackground,
                Theme.key_premiumGradient1,
                Theme.key_premiumGradient2,
                Theme.key_windowBackgroundGrayShadow,
            };
            for (int key : kept) keptKeys.put(key, true);
            final int[] onAccent = {
                Theme.key_chats_unreadCounterText,
                Theme.key_checkboxCheck,
            };
            for (int key : onAccent) onAccentKeys.put(key, true);
            final int[] lightTheme = {
                Theme.key_chats_verifiedBackground,
                Theme.key_chats_verifiedCheck,
            };
            for (int key : lightTheme) lightThemeKeys.put(key, true);
        }

        private final CherryProfileColors lightThemeOf;
        private Theme.ResourcesProvider parent;
        private MessagesController.PeerColor peerColor;

        public CherryProfileColors(CherryProfileColors lightThemeOf) {
            this.lightThemeOf = lightThemeOf;
        }

        private final SparseIntArray cache = new SparseIntArray();
        private int cacheBackground, cacheThemeBackground, cacheThemeText, cacheAccent;
        private final double[] lab = new double[3];
        private final double[] xyz = new double[3];

        public Theme.ResourcesProvider getParent() {
            return lightThemeOf != null ? lightThemeOf.getParent() : parent;
        }

        public void setParent(Theme.ResourcesProvider parent) {
            if (this.parent != parent) {
                this.parent = parent;
                cache.clear();
            }
        }

        public boolean isTinting() {
            return getPeerColor() != null;
        }

        private MessagesController.PeerColor getPeerColor() {
            return lightThemeOf != null ? lightThemeOf.peerColor : peerColor;
        }

        private int getBaseColor(int key) {
            return lightThemeOf != null ? Theme.getDefaultColor(key) : Theme.getColor(key, parent);
        }

        public void setPeerColor(MessagesController.PeerColor peerColor) {
            if (this.peerColor != peerColor) {
                this.peerColor = peerColor;
                cache.clear();
            }
        }

        private float musicDim;

        public void setMusicDim(float musicDim) {
            this.musicDim = musicDim;
        }

        private float getMusicDim() {
            return lightThemeOf != null ? lightThemeOf.musicDim : musicDim;
        }

        static int dimColor(int color, float amount) {
            return amount <= 0 ? color : ColorUtils.blendARGB(color, color & 0xFF000000, amount);
        }

        @Override
        public int getColor(int key) {
            final MessagesController.PeerColor peerColor = getPeerColor();
            if (peerColor == null || keptKeys.get(key)) {
                return Theme.getColor(key, getParent());
            }
            final int color = getBaseColor(key);
            final int background = dimColor(peerColor.getBgColor1(true), getMusicDim());
            if (key == Theme.key_windowBackgroundGray) {
                return background;
            }
            if (key == Theme.key_undo_background) {
                return ColorUtils.setAlphaComponent(ColorUtils.blendARGB(background, Color.BLACK, .35f), Color.alpha(color));
            }
            if (key == Theme.key_undo_infoColor) {
                return ColorUtils.setAlphaComponent(Color.WHITE, Color.alpha(color));
            }
            if (key == Theme.key_actionBarDefaultIcon) {
                return ColorUtils.setAlphaComponent(Color.WHITE, Color.alpha(color));
            }
            if (key == Theme.key_windowBackgroundWhite) {
                return ColorUtils.setAlphaComponent(getCardColor(background), Color.alpha(color));
            }
            if (key == Theme.key_featuredStickers_addButton || key == Theme.key_featuredStickers_addButtonPressed) {
                final int buttonColor = ColorUtils.blendARGB(getCardColor(background), Color.WHITE, key == Theme.key_featuredStickers_addButton ? .08f : .16f);
                return ColorUtils.setAlphaComponent(buttonColor, Color.alpha(color));
            }
            if (key == Theme.key_featuredStickers_buttonText) {
                return ColorUtils.setAlphaComponent(Color.WHITE, Color.alpha(color));
            }
            if (onAccentKeys.get(key)) {
                return ColorUtils.setAlphaComponent(background, Color.alpha(color));
            }
            if (lightThemeKeys.get(key)) {
                return mapColor(Theme.getDefaultColor(key), background,
                        Theme.getDefaultColor(Theme.key_windowBackgroundGray),
                        Theme.getDefaultColor(Theme.key_windowBackgroundWhiteBlackText),
                        Theme.getDefaultColor(Theme.key_windowBackgroundWhiteBlueHeader));
            }

            final int themeBackground = getBaseColor(Theme.key_windowBackgroundGray);
            final int themeText = getBaseColor(Theme.key_windowBackgroundWhiteBlackText);
            final int accent = getBaseColor(Theme.key_windowBackgroundWhiteBlueHeader);
            if (background != cacheBackground || themeBackground != cacheThemeBackground || themeText != cacheThemeText || accent != cacheAccent) {
                cache.clear();
                cacheBackground = background;
                cacheThemeBackground = themeBackground;
                cacheThemeText = themeText;
                cacheAccent = accent;
            }
            final int index = cache.indexOfKey(color);
            if (index >= 0) {
                return cache.valueAt(index);
            }
            final int mapped = mapColor(color, background, themeBackground, themeText, accent);
            cache.put(color, mapped);
            return mapped;
        }

        private int mapColor(int color, int background, int themeBackground, int themeText, int accent) {
            ColorUtils.colorToLAB(background, lab);
            final double peerChroma = Math.hypot(lab[1], lab[2]);
            final double peerHue = Math.atan2(lab[2], lab[1]);

            ColorUtils.colorToLAB(themeBackground, lab);
            final double themeBackgroundL = lab[0];
            ColorUtils.colorToLAB(themeText, lab);
            final double themeTextL = lab[0];

            ColorUtils.colorToLAB(accent, lab);
            final double accentChroma = Math.hypot(lab[1], lab[2]);
            final double accentHue = Math.atan2(lab[2], lab[1]);

            ColorUtils.colorToLAB(ColorUtils.setAlphaComponent(color, 0xFF), lab);
            double l = lab[0];
            double c = Math.hypot(lab[1], lab[2]);
            double h = Math.atan2(lab[2], lab[1]);

            if (c < NEUTRAL_CHROMA) {
                final double contrast = Math.abs(themeTextL - themeBackgroundL) < 1 ? 0 : (l - themeBackgroundL) / (themeTextL - themeBackgroundL);
                final int mapped;
                if (contrast > SURFACE_CONTRAST) {
                    final double amount = Math.min(1, SURFACE_AMOUNT + contrast * (1 - SURFACE_AMOUNT));
                    ColorUtils.colorToLAB(background, lab);
                    mapped = ColorUtils.LABToColor(lab[0] + (100 - lab[0]) * amount, lab[1] * (1 - amount), lab[2] * (1 - amount));
                } else {
                    mapped = getCardColor(background);
                }
                return ColorUtils.setAlphaComponent(mapped, Color.alpha(color));
            }

            final double hueDiff = Math.abs(h - accentHue) % (2 * Math.PI);
            final double accentHueDistance = Math.min(hueDiff, 2 * Math.PI - hueDiff);
            if (accentChroma >= NEUTRAL_CHROMA && accentHueDistance < ACCENT_HUE_RANGE) {
                h = peerHue;
                l = ACCENT_LIGHTNESS;
                c = Math.min(c, ACCENT_MAX_CHROMA);
                if (peerChroma < 15) {
                    c *= Math.max(.2, peerChroma / 15);
                }
            } else {
                return color;
            }

            final double cos = Math.cos(h), sin = Math.sin(h);
            if (!isInGamut(l, c * cos, c * sin)) {
                double lo = 0, hi = c;
                for (int i = 0; i < 10; ++i) {
                    final double mid = (lo + hi) / 2;
                    if (isInGamut(l, mid * cos, mid * sin)) {
                        lo = mid;
                    } else {
                        hi = mid;
                    }
                }
                c = lo;
            }
            return ColorUtils.setAlphaComponent(ColorUtils.LABToColor(l, c * cos, c * sin), Color.alpha(color));
        }

        private int getCardColor(int background) {
            final MessagesController.PeerColor peerColor = getPeerColor();
            final int buttonColor = peerColor.patternColor != 0
                    ? Theme.multAlpha(dimColor(peerColor.patternColor, getMusicDim()), .45f)
                    : Theme.multAlpha(PeerColorActivity.adaptProfileEmojiColor(background), .15f);
            final int card = ColorUtils.compositeColors(buttonColor, ColorUtils.compositeColors(Color.argb(4, 0, 0, 0), background));
            return ColorUtils.blendARGB(card, Color.BLACK, getCardDarkenAmount());
        }

        public static float getCardDarkenAmount() {
            return CARD_DARKEN;
        }

        private boolean isInGamut(double l, double a, double b) {
            ColorUtils.LABToXYZ(l, a, b, xyz);
            final double x = xyz[0] / 100, y = xyz[1] / 100, z = xyz[2] / 100;
            final double r = x * 3.2406 + y * -1.5372 + z * -0.4986;
            final double g = x * -0.9689 + y * 1.8758 + z * 0.0415;
            final double bl = x * 0.0557 + y * -0.2040 + z * 1.0570;
            final double e = .002;
            return r >= -e && r <= 1 + e && g >= -e && g <= 1 + e && bl >= -e && bl <= 1 + e;
        }

        @Override
        public Drawable getDrawable(String drawableKey) {
            final Theme.ResourcesProvider parent = getParent();
            return parent != null ? parent.getDrawable(drawableKey) : null;
        }

        @Override
        public Paint getPaint(String paintKey) {
            final Theme.ResourcesProvider parent = getParent();
            return parent != null ? parent.getPaint(paintKey) : null;
        }

        @Override
        public boolean hasGradientService() {
            final Theme.ResourcesProvider parent = getParent();
            return parent != null && parent.hasGradientService();
        }

        @Override
        public void applyServiceShaderMatrix(int w, int h, float translationX, float translationY) {
            final Theme.ResourcesProvider parent = getParent();
            if (parent != null) {
                parent.applyServiceShaderMatrix(w, h, translationX, translationY);
            } else {
                Theme.applyServiceShaderMatrix(w, h, translationX, translationY);
            }
        }

        @Override
        public boolean isDark() {
            if (lightThemeOf != null && isTinting()) {
                return false;
            }
            final Theme.ResourcesProvider parent = getParent();
            return parent != null ? parent.isDark() : Theme.isCurrentThemeDark();
        }
    }
