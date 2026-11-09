package org.telegram.messenger;

import android.content.Context;
import android.content.Intent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * MeeroX temporary startup/crash tracer.
 * Writes progress markers and any uncaught exception to
 * <external-files>/meerox_crash/ so a silent startup crash can be diagnosed.
 */
public class CrashTrace {

    private static File dir;
    private static File dirty;
    private static long t0;
    private static boolean inited;

    public static void init(Context context) {
        if (inited) return;
        inited = true;
        try {
            dir = new File(context.getExternalFilesDir(null), "meerox_crash");
            if (!dir.exists()) dir.mkdirs();
            dirty = new File(dir, "dirty.flag");
            t0 = System.currentTimeMillis();
            final String header = "=== launch " + time() + " pid=" + android.os.Process.myPid()
                    + " ver=" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"
                    + " dirty-before=" + dirty.exists() + " ===";
            new FileOutputStream(dirty).close();
            append("startup.txt", header);
            final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                try {
                    append("crash.txt", "CRASH " + time() + "\n" + trace(error));
                } catch (Throwable ignore) {
                }
                try {
                    Intent i = new Intent(context, org.telegram.ui.CrashReportActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
                            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                    context.startActivity(i);
                } catch (Throwable ignore) {
                }
                try {
                    if (prev != null) prev.uncaughtException(thread, error);
                } catch (Throwable ignore) {
                }
            });
        } catch (Throwable ignore) {
        }
    }

    public static void mark(String msg) {
        try {
            append("startup.txt", "  +" + (System.currentTimeMillis() - t0) + "ms  " + msg);
        } catch (Throwable ignore) {
        }
    }

    public static boolean hasDirty() {
        return dirty != null && dirty.exists();
    }

    public static void clearDirty() {
        try {
            if (dirty != null && dirty.exists()) dirty.delete();
        } catch (Throwable ignore) {
        }
    }

    public static String read(String name) {
        try {
            File f = new File(dir, name);
            if (!f.exists()) return "(no " + name + ")";
            byte[] b = new byte[(int) Math.min(f.length(), 60000)];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int n = in.read(b);
            in.close();
            String s = new String(b, 0, Math.max(n, 0), "UTF-8");
            if (f.length() > 60000) s = "...(truncated)...\n" + s.substring(s.indexOf('\n') + 1);
            return s;
        } catch (Throwable e) {
            return "(read error " + e + ")";
        }
    }

    public static String dirPath() {
        return dir == null ? "(no dir)" : dir.getAbsolutePath();
    }

    private static synchronized void append(String name, String text) throws Exception {
        File f = new File(dir, name);
        if (f.exists() && f.length() > 200000) f.delete();
        FileOutputStream out = new FileOutputStream(f, true);
        out.write((text + "\n").getBytes("UTF-8"));
        out.flush();
        out.close();
    }

    private static String trace(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        pw.flush();
        Throwable c = e.getCause();
        int depth = 0;
        while (c != null && depth++ < 4) {
            sw.write("\nCaused by: ");
            c.printStackTrace(pw);
            c = c.getCause();
        }
        return sw.toString();
    }

    private static String time() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }
}
