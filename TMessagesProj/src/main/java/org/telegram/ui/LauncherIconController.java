package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

public class LauncherIconController {
    public static void tryFixLauncherIconIfNeeded() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return;
            }
        }

        setIcon(LauncherIcon.BLUE);
    }

    public static boolean isEnabled(LauncherIcon icon) {
        // MeeroX v210: on some ROMs (MIUI observed by the owner) resolving a
        // launcher alias can throw (component not found / security), which
        // crashed the whole chat-settings screen while SCROLLING the icon
        // row. The picker must never die for a cosmetic probe.
        try {
            Context ctx = ApplicationLoader.applicationContext;
            int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
            return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.BLUE;
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        // Disable every known launcher alias (visible + legacy hidden), then
        // enable the one the user picked. Legacy keys stay in ALL_KEYS so a
        // previously-selected Nagram/AR icon cannot remain on the home screen.
        for (String key : ALL_KEYS) {
            try {
                ComponentName cn = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
                boolean on = icon.key.equals(key);
                pm.setComponentEnabledSetting(cn,
                        on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                           : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }

    /** All activity-alias keys ever registered in the Manifest (including
     *  removed AR/Nagram icons). Used only by setIcon() to force-disable
     *  leftovers; they are NOT shown in the picker. */
    private static final String[] ALL_KEYS = new String[] {
            "DefaultIcon", "GoogleIcon", "ColorfulIcon", "DarkGreenIcon",
            "NeonIcon", "NielloIcon", "DarkBlueIcon", "BlurBlueIcon",
            "TelegramIcon", "VintageIcon", "AquaIcon", "PremiumIcon",
            "TurboIcon", "NoxIcon", "BlueIcon",
            "MeeroMBoldIcon", "MeeroMMarkerIcon", "MeeroMTileIcon", "MeeroMDuoIcon",
    };

    public enum LauncherIcon {
        // Official Telegram icons (kept)
        TELEGRAM("TelegramIcon", R.drawable.icon_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconTelegramOriginal),
        VINTAGE("VintageIcon", R.drawable.icon_6_background_sa, R.mipmap.icon_6_foreground_sa, R.string.AppIconVintage),
        AQUA("AquaIcon", R.drawable.icon_4_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconAqua),
        PREMIUM("PremiumIcon", R.drawable.icon_3_background_sa, R.mipmap.icon_3_foreground_sa, R.string.AppIconPremium),
        TURBO("TurboIcon", R.drawable.icon_5_background_sa, R.mipmap.icon_5_foreground_sa, R.string.AppIconTurbo),
        NOX("NoxIcon", R.mipmap.icon_2_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconNox),

        // Default enabled alias (BlueIcon). Art = Aras Pink.
        // background = full art for picker; adaptive home icon uses separate bg+fg via mipmap XML.
        BLUE("BlueIcon", R.drawable.ic_launcher_nagram_blue_full, R.drawable.ic_launcher_nagram_blue_foreground, R.string.AppIconBlue),

        // Four Aras icons — new BACKGROUND + FOREGROUND under res/drawable/.
        // Reuse Meero*Icon alias keys so AndroidManifest stays untouched.
        MBOLD("MeeroMBoldIcon", R.drawable.meero_m_bold_full, R.drawable.meero_m_bold_foreground, 455),
        MMARKER("MeeroMMarkerIcon", R.drawable.meero_m_marker_full, R.drawable.meero_m_marker_foreground, 456),
        MTILE("MeeroMTileIcon", R.drawable.meero_m_tile_full, R.drawable.meero_m_tile_foreground, 457),
        MDUO("MeeroMDuoIcon", R.drawable.meero_m_duo_full, R.drawable.meero_m_duo_foreground, 458);

        public final String key;
        public final int background;
        public final int foreground;
        public final int title;
        public final String titleKey;
        public final int vaultTitle;
        public final boolean premium;

        private ComponentName componentName;

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
            }
            return componentName;
        }

        LauncherIcon(String key, int background, int foreground, int title) {
            this(key, background, foreground, title, false);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.titleKey = null;
            this.vaultTitle = -1;
            this.premium = premium;
        }

        /* v186 (batch 2D): M-icon titles by numeric vault id (long keeps the
         *  overload distinct from the R.string int form). */
        LauncherIcon(String key, int background, int foreground, long vaultTitle) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = 0;
            this.titleKey = null;
            this.vaultTitle = (int) vaultTitle;
            this.premium = false;
        }

        /** title of the picker row - vault strings for the new M icons,
         *  resource strings for legacy entries. */
        public String getTitle() {
            if (vaultTitle >= 0) {
                return tw.nekomimi.nekogram.MeeroStrings.s(vaultTitle);
            }
            return titleKey != null
                    ? tw.nekomimi.nekogram.MeeroStrings.s(titleKey)
                    : org.telegram.messenger.LocaleController.getString(title);
        }

        public boolean isNekoX() {
            // Full-bleed Aras art: show background bitmap as-is in the picker
            // (no outer-padding zoom / plane fg overlay).
            return this == BLUE || this == MBOLD || this == MMARKER || this == MTILE || this == MDUO;
        }
    }
}
