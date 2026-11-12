package com.mychord.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Kongsi MyChord: hantar fail APK terus (WhatsApp, Telegram, Bluetooth, Quick Share...). */
public class ShareUtil {
    public static void shareApp(Activity a) {
        Toast.makeText(a, R.string.share_preparing, Toast.LENGTH_SHORT).show();
        App.IO.execute(() -> {
            try {
                File src = new File(a.getApplicationInfo().sourceDir);
                File dir = new File(a.getCacheDir(), "share");
                dir.mkdirs();
                File out = new File(dir, "MyChord-v" + BuildConfigInfo.version(a) + ".apk");
                try (InputStream in = new FileInputStream(src); OutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                }
                Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".files", out);
                App.MAIN.post(() -> {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("application/vnd.android.package-archive");
                    i.putExtra(Intent.EXTRA_STREAM, uri);
                    i.putExtra(Intent.EXTRA_TEXT, a.getString(R.string.share_text));
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    a.startActivity(Intent.createChooser(i, a.getString(R.string.share_app)));
                });
            } catch (Exception e) {
                App.MAIN.post(() -> {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, a.getString(R.string.share_text));
                    a.startActivity(Intent.createChooser(i, a.getString(R.string.share_app)));
                });
            }
        });
    }
}
