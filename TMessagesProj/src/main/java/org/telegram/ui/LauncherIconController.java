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

    private static final String[] ALL_KEYS = new String[] {
            "DefaultIcon", "GoogleIcon", "ColorfulIcon", "DarkGreenIcon",
            "NeonIcon", "NielloIcon", "DarkBlueIcon", "BlurBlueIcon",
            "TelegramIcon", "VintageIcon", "AquaIcon", "PremiumIcon",
            "TurboIcon", "NoxIcon", "BlueIcon",
            "MeeroMBoldIcon", "MeeroMMarkerIcon", "MeeroMTileIcon", "MeeroMDuoIcon",
            "MeeroMKBlackIcon",
    };

    public enum LauncherIcon {
        // ---- Aras icons first (picker order) ----
        // Default enabled alias
        BLUE("BlueIcon", R.drawable.ic_launcher_nagram_blue_full, R.drawable.ic_launcher_nagram_blue_foreground, R.string.AppIconArasPurple),
        MBOLD("MeeroMBoldIcon", R.drawable.meero_m_bold_full, R.drawable.meero_m_bold_foreground, R.string.AppIconArasPink),
        MMARKER("MeeroMMarkerIcon", R.drawable.meero_m_marker_full, R.drawable.meero_m_marker_foreground, R.string.AppIconArasCrimson),
        MTILE("MeeroMTileIcon", R.drawable.meero_m_tile_full, R.drawable.meero_m_tile_foreground, R.string.AppIconArasGold),
        MDUO("MeeroMDuoIcon", R.drawable.meero_m_duo_full, R.drawable.meero_m_duo_foreground, R.string.AppIconArasK),
        MKBLACK("MeeroMKBlackIcon", R.drawable.meero_m_kblack_full, R.drawable.meero_m_kblack_foreground, R.string.AppIconArasKBlack),

        // ---- Official Telegram icons ----
        TELEGRAM("TelegramIcon", R.drawable.icon_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconTelegramOriginal),
        VINTAGE("VintageIcon", R.drawable.icon_6_background_sa, R.mipmap.icon_6_foreground_sa, R.string.AppIconVintage),
        AQUA("AquaIcon", R.drawable.icon_4_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconAqua),
        PREMIUM("PremiumIcon", R.drawable.icon_3_background_sa, R.mipmap.icon_3_foreground_sa, R.string.AppIconPremium),
        TURBO("TurboIcon", R.drawable.icon_5_background_sa, R.mipmap.icon_5_foreground_sa, R.string.AppIconTurbo),
        NOX("NoxIcon", R.mipmap.icon_2_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconNox);

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

        LauncherIcon(String key, int background, int foreground, long vaultTitle) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = 0;
            this.titleKey = null;
            this.vaultTitle = (int) vaultTitle;
            this.premium = false;
        }

        public String getTitle() {
            if (vaultTitle >= 0) {
                try {
                    String s = tw.nekomimi.nekogram.MeeroStrings.s(vaultTitle);
                    if (s != null && !s.isEmpty() && !s.contains("LOC_ERR") && !s.contains("null")) {
                        return s;
                    }
                } catch (Throwable ignore) {}
            }
            if (titleKey != null) {
                try {
                    return tw.nekomimi.nekogram.MeeroStrings.s(titleKey);
                } catch (Throwable ignore) {}
            }
            if (title != 0) {
                return org.telegram.messenger.LocaleController.getString(title);
            }
            return key;
        }

        public boolean isNekoX() {
            return this == BLUE || this == MBOLD || this == MMARKER
                    || this == MTILE || this == MDUO || this == MKBLACK;
        }
    }
}
