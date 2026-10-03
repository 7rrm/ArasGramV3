package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

public class LauncherIconController {
    public static void tryFixLauncherIconIfNeeded() {
        // MeeroX: exactly one launcher alias may be enabled. After an update
        // more than one can end up enabled (e.g. the old BlueIcon plus the new
        // default), which shows two icons in the launcher. Rule: keep the user
        // pick when there is one, otherwise fall back to the K Dark default.
        LauncherIcon enabled = null;
        int count = 0;
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                count++;
                if (enabled == null) {
                    enabled = icon;
                }
            }
        }
        if (count == 1) {
            return;
        }
        LauncherIcon target = LauncherIcon.MKBLACK;
        if (count > 1) {
            for (LauncherIcon icon : LauncherIcon.values()) {
                if (isEnabled(icon) && icon != LauncherIcon.MKBLACK && icon != LauncherIcon.BLUE) {
                    target = icon;
                    break;
                }
            }
        }
        setIcon(target);
    }

    public static boolean isEnabled(LauncherIcon icon) {
        // MeeroX v210: on some ROMs (MIUI observed by the owner) resolving a
        // launcher alias can throw (component not found / security), which
        // crashed the whole chat-settings screen while SCROLLING the icon
        // row. The picker must never die for a cosmetic probe.
        try {
            Context ctx = ApplicationLoader.applicationContext;
            int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
            return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.MKBLACK;
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        for (LauncherIcon i : LauncherIcon.values()) {
            // MeeroX v210: never let one stubborn alias kill the rest of the
            // switch (same MIUI class of failure as above).
            try {
                pm.setComponentEnabledSetting(i.getComponentName(ctx), i == icon ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }

    public enum LauncherIcon {
        // ---- Aras icons first (6) - default is K Dark ----
        BLUE("BlueIcon", R.drawable.ic_launcher_nagram_blue_bg, R.drawable.ic_launcher_nagram_blue_foreground, R.string.AppIconArasPurple),
        MKBLACK("MeeroMKBlackIcon", R.drawable.meero_m_kblack_bg, R.drawable.meero_m_kblack_foreground, R.string.AppIconArasKBlack),
        MBOLD("MeeroMBoldIcon", R.drawable.meero_m_bold_bg, R.drawable.meero_m_bold_foreground, R.string.AppIconArasPink),
        MMARKER("MeeroMMarkerIcon", R.drawable.meero_m_marker_bg, R.drawable.meero_m_marker_foreground, R.string.AppIconArasCrimson),
        MTILE("MeeroMTileIcon", R.drawable.meero_m_tile_bg, R.drawable.meero_m_tile_foreground, R.string.AppIconArasGold),
        MDUO("MeeroMDuoIcon", R.drawable.meero_m_duo_bg, R.drawable.meero_m_duo_foreground, R.string.AppIconArasK),
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
            // MeeroX: لا أيقونة بهذا النوع بعد حذف عائلة AR (minSdk 27 → أيقونات adaptive دائمًا)
            return false;
        }
    }
}
