package com.mychord.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;

public class MainActivity extends BaseActivity {
    private final Fragment[] frags = new Fragment[4];
    private final String[] tags = {"home", "search", "library", "settings"};
    private int current = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Prefs.getB("langSet", false)) {
            Prefs.putB("langSet", true);
            Prefs.putS("lang", "ms");
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ms"));
        }
        setContentView(R.layout.activity_main);
        setupMini();
        PlayerHub.connect(this);

        FragmentManager fm = getSupportFragmentManager();
        for (int i = 0; i < 4; i++) frags[i] = fm.findFragmentByTag(tags[i]);

        BottomNavigationView nav = findViewById(R.id.nav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.tab_home) show(0);
            else if (id == R.id.tab_search) show(1);
            else if (id == R.id.tab_library) show(2);
            else show(3);
            return true;
        });
        int start = savedInstanceState != null ? savedInstanceState.getInt("tab", 0)
                : getIntent().getIntExtra("tab", 0);
        int[] navIds = {R.id.tab_home, R.id.tab_search, R.id.tab_library, R.id.tab_settings};
        show(Math.max(0, Math.min(3, start)));
        nav.setSelectedItemId(navIds[Math.max(0, Math.min(3, start))]);

        // Ujian automatik: main lagu pertama dari carian
        String testQuery = getIntent().getStringExtra("testQuery");
        if (testQuery != null) {
            App.IO.execute(() -> {
                try {
                    java.util.List<Track> r = YtMusic.search(testQuery);
                    android.util.Log.i("MyChordTest", "carian=" + r.size());
                    App.MAIN.post(() -> PlayerHub.playList(this, r, 0, false));
                } catch (Exception e) {
                    android.util.Log.e("MyChordTest", "carian gagal", e);
                }
            });
        }

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }

        // Semak addon baharu (auto-pasang)
        App.IO.execute(() -> {
            List<String> added = AddonManager.sync();
            if (!added.isEmpty()) {
                App.MAIN.post(() -> {
                    Toast.makeText(this, getString(R.string.addons_new, String.join(", ", added)),
                            Toast.LENGTH_LONG).show();
                    if (frags[0] instanceof HomeFragment) ((HomeFragment) frags[0]).buildTiles();
                });
            }
        });
    }

    @Override
    protected void onNewIntent(@androidx.annotation.NonNull android.content.Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra("tab")) {
            int t = Math.max(0, Math.min(3, intent.getIntExtra("tab", 0)));
            int[] navIds = {R.id.tab_home, R.id.tab_search, R.id.tab_library, R.id.tab_settings};
            ((BottomNavigationView) findViewById(R.id.nav)).setSelectedItemId(navIds[t]);
        }
    }

    public void selectTab(int t) {
        int[] navIds = {R.id.tab_home, R.id.tab_search, R.id.tab_library, R.id.tab_settings};
        ((BottomNavigationView) findViewById(R.id.nav)).setSelectedItemId(navIds[Math.max(0, Math.min(3, t))]);
    }

    public void show(int i) {
        if (i == current) return;
        FragmentManager fm = getSupportFragmentManager();
        androidx.fragment.app.FragmentTransaction tx = fm.beginTransaction();
        for (int k = 0; k < 4; k++) if (frags[k] != null && k != i) tx.hide(frags[k]);
        if (frags[i] == null) {
            frags[i] = i == 0 ? new HomeFragment() : i == 1 ? new SearchFragment()
                    : i == 2 ? new LibraryFragment() : new SettingsFragment();
            tx.add(R.id.content, frags[i], tags[i]);
        } else tx.show(frags[i]);
        tx.commitNowAllowingStateLoss();
        current = i;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", Math.max(current, 0));
    }
}
