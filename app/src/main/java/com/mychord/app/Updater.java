package com.mychord.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.Request;
import okhttp3.Response;

/** Semak versi baharu di laman web MyChord dan pasang terus dari dalam app. */
public class Updater {
    static final String VERSION_URL = "https://mohdrozaimi205.github.io/MyChord-PWA/version.json";

    static long installedCode(Activity a) {
        try {
            PackageInfo pi = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    /** @param manual true bila pengguna tekan "Semak kemas kini" (tunjuk mesej walaupun tiada versi baharu) */
    public static void check(Activity a, boolean manual) {
        if (manual) Toast.makeText(a, R.string.upd_checking, Toast.LENGTH_SHORT).show();
        App.IO.execute(() -> {
            try {
                Request req = new Request.Builder().url(VERSION_URL + "?t=" + System.currentTimeMillis()).build();
                JSONObject v;
                try (Response r = App.http.newCall(req).execute()) {
                    if (!r.isSuccessful() || r.body() == null) throw new Exception("HTTP " + r.code());
                    v = new JSONObject(r.body().string());
                }
                long code = v.optLong("versionCode");
                String name = v.optString("versionName");
                String url = v.optString("url");
                String notes = "en".equals(Prefs.lang()) ? v.optString("notes_en", v.optString("notes_ms")) : v.optString("notes_ms");
                boolean newer = code > installedCode(a);
                boolean skipped = !manual && Prefs.getI("updSkip", 0) == code;
                App.MAIN.post(() -> {
                    if (a.isFinishing()) return;
                    if (newer && !skipped) ask(a, name, notes, url, (int) code);
                    else if (manual) Toast.makeText(a, a.getString(R.string.upd_latest, BuildConfigInfo.version(a)), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                if (manual) App.MAIN.post(() -> Toast.makeText(a, R.string.error_net, Toast.LENGTH_SHORT).show());
            }
        });
    }

    static void ask(Activity a, String name, String notes, String url, int code) {
        new MaterialAlertDialogBuilder(a)
                .setTitle(a.getString(R.string.upd_title, name))
                .setMessage(notes == null || notes.isEmpty() ? a.getString(R.string.upd_msg) : notes)
                .setPositiveButton(R.string.upd_now, (d, w) -> download(a, url, name))
                .setNegativeButton(R.string.upd_later, (d, w) -> Prefs.putI("updSkip", code))
                .show();
    }

    static void download(Activity a, String url, String name) {
        AlertDialog prog = new MaterialAlertDialogBuilder(a)
                .setTitle(a.getString(R.string.upd_downloading, name))
                .setMessage("0%")
                .setCancelable(false)
                .show();
        App.IO.execute(() -> {
            try {
                File dir = new File(a.getCacheDir(), "update");
                dir.mkdirs();
                File out = new File(dir, "MyChord-update.apk");
                Request req = new Request.Builder().url(url + "?t=" + System.currentTimeMillis()).build();
                try (Response r = App.http.newCall(req).execute()) {
                    if (!r.isSuccessful() || r.body() == null) throw new Exception("HTTP " + r.code());
                    long total = r.body().contentLength();
                    try (InputStream in = r.body().byteStream(); OutputStream os = new FileOutputStream(out)) {
                        byte[] buf = new byte[64 * 1024];
                        long done = 0;
                        int n, lastPct = -1;
                        while ((n = in.read(buf)) > 0) {
                            os.write(buf, 0, n);
                            done += n;
                            if (total > 0) {
                                int pct = (int) (done * 100 / total);
                                if (pct != lastPct) {
                                    lastPct = pct;
                                    App.MAIN.post(() -> prog.setMessage(pct + "%"));
                                }
                            }
                        }
                    }
                }
                App.MAIN.post(() -> {
                    prog.dismiss();
                    install(a, out, url);
                });
            } catch (Exception e) {
                App.MAIN.post(() -> {
                    prog.dismiss();
                    openInBrowser(a, url);
                });
            }
        });
    }

    static void install(Activity a, File apk, String url) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(a, R.string.upd_allow, Toast.LENGTH_LONG).show();
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + a.getPackageName())));
            } catch (Exception e) {
                openInBrowser(a, url);
            }
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".files", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Exception e) {
            openInBrowser(a, url);
        }
    }

    static void openInBrowser(Activity a, String url) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) { }
    }
}
