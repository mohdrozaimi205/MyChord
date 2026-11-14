package com.mychord.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.ChipGroup;

public class SettingsFragment extends Fragment {
    private View root;

    private final ActivityResultLauncher<Intent> backupLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri == null) return;
                    try {
                        BackupRestore.writeTo(requireContext(), uri);
                        Toast.makeText(getContext(), R.string.backup_done, Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(getContext(), getString(R.string.backup_fail, e.getMessage()), Toast.LENGTH_LONG).show();
                    }
                }
            });

    private final ActivityResultLauncher<Intent> restoreLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri == null) return;
                    try {
                        String summary = BackupRestore.restoreFrom(requireContext(), uri);
                        Toast.makeText(getContext(), getString(R.string.backup_restored, summary), Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(getContext(), getString(R.string.backup_fail, e.getMessage()), Toast.LENGTH_LONG).show();
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        root = inf.inflate(R.layout.fragment_settings, c, false);
        buildThemes();
        buildIcons();

        ChipGroup lang = root.findViewById(R.id.langGroup);
        lang.check("en".equals(Prefs.lang()) ? R.id.langEn : R.id.langMs);
        lang.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            String l = ids.get(0) == R.id.langEn ? "en" : "ms";
            if (l.equals(Prefs.lang())) return;
            Prefs.putS("lang", l);
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(l));
        });

        setupQuality(root.findViewById(R.id.qWifi), new int[]{R.id.qWifi0, R.id.qWifi1, R.id.qWifi2}, "qWifi", Prefs.qWifi());
        setupQuality(root.findViewById(R.id.qData), new int[]{R.id.qData0, R.id.qData1, R.id.qData2}, "qData", Prefs.qData());

        buildAccounts();
        LinearLayout more = root.findViewById(R.id.moreRows);
        addRow(inf, more, R.drawable.ic_eq, R.string.set_effects, EffectsActivity.class);
        addRow(inf, more, R.drawable.ic_addon, R.string.set_addons, AddonsActivity.class);
        addRow(inf, more, R.drawable.ic_link, R.string.set_spotify, SpotifyActivity.class);
        addRow(inf, more, R.drawable.ic_note, R.string.set_moods, MoodsActivity.class);

        // Sandaran & Pulih
        View backupRow = inf.inflate(R.layout.item_row, more, false);
        backupRow.setPadding(0, 0, 0, 0);
        ImageView bi = backupRow.findViewById(R.id.rowIcon);
        bi.setImageResource(R.drawable.ic_history);
        Ui.tint(bi, Ui.attr(requireContext(), R.attr.mcAccent));
        ((TextView) backupRow.findViewById(R.id.rowTitle)).setText(R.string.set_backup);
        backupRow.setOnClickListener(v -> showBackupDialog());
        more.addView(backupRow);
        View share = inf.inflate(R.layout.item_row, more, false);
        share.setPadding(0, 0, 0, 0);
        ImageView si = share.findViewById(R.id.rowIcon);
        si.setImageResource(R.drawable.ic_share);
        Ui.tint(si, Ui.attr(requireContext(), R.attr.mcAccent));
        ((TextView) share.findViewById(R.id.rowTitle)).setText(R.string.set_share);
        share.setOnClickListener(v -> ShareUtil.shareApp(requireActivity()));
        more.addView(share);
        View upd = inf.inflate(R.layout.item_row, more, false);
        upd.setPadding(0, 0, 0, 0);
        ImageView ui = upd.findViewById(R.id.rowIcon);
        ui.setImageResource(R.drawable.ic_history);
        Ui.tint(ui, Ui.attr(requireContext(), R.attr.mcAccent));
        ((TextView) upd.findViewById(R.id.rowTitle)).setText(R.string.upd_check);
        TextView us = upd.findViewById(R.id.rowSub);
        us.setVisibility(View.VISIBLE);
        us.setText("v" + BuildConfigInfo.version(requireContext()));
        upd.setOnClickListener(v -> Updater.check(requireActivity(), true));
        more.addView(upd);

        ((TextView) root.findViewById(R.id.aboutText)).setText(getString(R.string.about_text, BuildConfigInfo.version(requireContext())));
        return root;
    }

    private void setupQuality(ChipGroup g, int[] ids, String key, int value) {
        g.check(ids[Math.max(0, Math.min(2, value))]);
        g.setOnCheckedStateChangeListener((group, checked) -> {
            if (checked.isEmpty()) return;
            int id = checked.get(0);
            for (int i = 0; i < 3; i++) if (ids[i] == id) Prefs.putI(key, i);
            Resolver.clear();
        });
    }

    private void addRow(LayoutInflater inf, LinearLayout parent, int icon, int title, Class<?> cls) {
        View r = inf.inflate(R.layout.item_row, parent, false);
        r.setPadding(0, 0, 0, 0);
        ImageView iv = r.findViewById(R.id.rowIcon);
        iv.setImageResource(icon);
        Ui.tint(iv, Ui.attr(requireContext(), R.attr.mcAccent));
        ((TextView) r.findViewById(R.id.rowTitle)).setText(title);
        r.setOnClickListener(v -> startActivity(new Intent(getContext(), cls)));
        parent.addView(r);
    }

    private void buildAccounts() {
        LinearLayout box = root.findViewById(R.id.accountRows);
        box.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(getContext());
        accountRow(inf, box, R.drawable.ic_remote, "YouTube Music", YtmAccount.loggedIn(), YtmAccount.name(), LoginActivity.YT);
        accountRow(inf, box, R.drawable.ic_link, "Spotify", SpotifyAccount.loggedIn(), SpotifyAccount.name(), LoginActivity.SP);
    }

    private void accountRow(LayoutInflater inf, LinearLayout parent, int icon, String title, boolean on, String name, String service) {
        View r = inf.inflate(R.layout.item_row, parent, false);
        r.setPadding(0, 0, 0, 0);
        ImageView iv = r.findViewById(R.id.rowIcon);
        iv.setImageResource(icon);
        Ui.tint(iv, Ui.attr(requireContext(), R.attr.mcAccent));
        ((TextView) r.findViewById(R.id.rowTitle)).setText(title);
        TextView sub = r.findViewById(R.id.rowSub);
        sub.setVisibility(View.VISIBLE);
        sub.setText(on ? getString(R.string.acc_logged_as, name.isEmpty() ? "✓" : name) : getString(R.string.acc_not_logged));
        r.setOnClickListener(v -> {
            if (on) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle(title)
                        .setMessage(R.string.acc_logout_q)
                        .setPositiveButton(R.string.acc_logout, (d, w) -> {
                            if (LoginActivity.YT.equals(service)) YtmAccount.logout(); else SpotifyAccount.logout();
                            buildAccounts();
                        })
                        .setNegativeButton(R.string.cancel, null).show();
            } else {
                startActivity(new Intent(getContext(), LoginActivity.class).putExtra("service", service));
            }
        });
        parent.addView(r);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (root != null) buildAccounts();
    }

    private void buildThemes() {
        LinearLayout row = root.findViewById(R.id.themeRow);
        row.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(getContext());
        int accent = Ui.attr(requireContext(), R.attr.mcAccent);
        for (int i = 0; i < ThemeUtil.IDS.length; i++) {
            String id = ThemeUtil.IDS[i];
            View v = inf.inflate(R.layout.item_swatch, row, false);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(ThemeUtil.COLORS[i][0]);
            bg.setStroke(Ui.dp(requireContext(), 1), 0x33888888);
            v.findViewById(R.id.swatchBg).setBackground(bg);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(ThemeUtil.COLORS[i][1]);
            v.findViewById(R.id.swatchDot).setBackground(dot);
            if (id.equals(Prefs.theme())) {
                GradientDrawable ring = new GradientDrawable();
                ring.setShape(GradientDrawable.OVAL);
                ring.setStroke(Ui.dp(requireContext(), 2), accent);
                v.findViewById(R.id.swatchFrame).setBackground(ring);
            }
            boolean locked = ThemeUtil.locked(id);
            ImageView lock = v.findViewById(R.id.swatchLock);
            lock.setVisibility(locked ? View.VISIBLE : View.GONE);
            Ui.tint(lock, accent);
            ((TextView) v.findViewById(R.id.swatchName)).setText(ThemeUtil.NAMES[i]);
            v.setOnClickListener(x -> {
                if (locked) {
                    Toast.makeText(getContext(), R.string.theme_locked, Toast.LENGTH_LONG).show();
                    return;
                }
                if (id.equals(Prefs.theme())) return;
                Prefs.putS("theme", id);
                requireActivity().recreate();
            });
            row.addView(v);
        }
    }

    private void buildIcons() {
        LinearLayout row = root.findViewById(R.id.iconRow);
        row.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(getContext());
        int accent = Ui.attr(requireContext(), R.attr.mcAccent);
        for (int i = 0; i < IconSwitcher.IDS.length; i++) {
            String id = IconSwitcher.IDS[i];
            View v = inf.inflate(R.layout.item_swatch, row, false);
            v.findViewById(R.id.swatchBg).setBackgroundResource(IconSwitcher.PREVIEWS[i]);
            v.findViewById(R.id.swatchDot).setVisibility(View.GONE);
            if (id.equals(Prefs.icon())) {
                GradientDrawable ring = new GradientDrawable();
                ring.setShape(GradientDrawable.OVAL);
                ring.setStroke(Ui.dp(requireContext(), 2), accent);
                v.findViewById(R.id.swatchFrame).setBackground(ring);
            }
            ((TextView) v.findViewById(R.id.swatchName)).setText(IconSwitcher.NAMES[i]);
            v.setOnClickListener(x -> {
                if (id.equals(Prefs.icon())) return;
                IconSwitcher.apply(requireContext(), id);
                Toast.makeText(getContext(), R.string.set_icon_note, Toast.LENGTH_LONG).show();
                buildIcons();
            });
            row.addView(v);
        }
    }

    private void showBackupDialog() {
        String[] items = {getString(R.string.backup_export), getString(R.string.backup_import)};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.backup_title)
                .setItems(items, (d, w) -> {
                    if (w == 0) {
                        // Eksport sandaran
                        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("application/json");
                        i.putExtra(Intent.EXTRA_TITLE, "mychord_backup.json");
                        backupLauncher.launch(i);
                    } else {
                        // Import sandaran
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("application/json");
                        restoreLauncher.launch(i);
                    }
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && root != null) buildThemes();
    }
}
