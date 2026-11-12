package com.mychord.app;

import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EffectsActivity extends BaseActivity {
    private final List<Slider> bandSliders = new ArrayList<>();
    private static final int[] PRESET_NAMES = {R.string.preset_flat, R.string.preset_bass, R.string.preset_vocal,
            R.string.preset_rock, R.string.preset_pop, R.string.preset_treble, R.string.preset_night};
    private static final int[] PACK_NAMES = {R.string.preset_dangdut, R.string.preset_rockkapak, R.string.preset_nasyid,
            R.string.preset_clearvocal, R.string.preset_carbass, R.string.preset_acoustic, R.string.preset_electronic, R.string.preset_classical};

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_effects);
        setupTop(getString(R.string.eq_title));
        PlayerHub.connect(this);

        findViewById(R.id.btnDolby).setOnClickListener(v -> openSystemEffects());

        MaterialSwitch sw = findViewById(R.id.eqSwitch);
        sw.setChecked(Effects.eqOn());
        sw.setOnCheckedChangeListener((b, on) -> {
            Prefs.putB("eqOn", on);
            Effects.applyEq();
        });

        buildBands();

        ChipGroup presets = findViewById(R.id.eqPresets);
        String cur = Prefs.getS("eqPreset", "flat");
        for (int i = 0; i < Effects.PRESET_KEYS.length; i++) {
            Chip c = new Chip(this);
            c.setText(PRESET_NAMES[i]);
            c.setCheckable(true);
            c.setChecked(Effects.PRESET_KEYS[i].equals(cur));
            final int idx = i;
            c.setOnClickListener(v -> {
                Effects.setPreset(idx);
                if (!Effects.eqOn()) sw.setChecked(true);
                syncBands();
            });
            presets.addView(c);
        }
        if (AddonManager.installed("eqpack")) {
            for (int i = 0; i < Effects.PACK_KEYS.length; i++) {
                Chip c = new Chip(this);
                c.setText(PACK_NAMES[i]);
                c.setCheckable(true);
                c.setChecked(Effects.PACK_KEYS[i].equals(cur));
                final int idx = i;
                c.setOnClickListener(v -> {
                    Effects.setPreset(Effects.PACK_KEYS[idx], Effects.PACK[idx]);
                    if (!Effects.eqOn()) sw.setChecked(true);
                    syncBands();
                });
                presets.addView(c);
            }
        }
        buildQualityAddons();

        setupSlider(findViewById(R.id.sBass), "bass", Effects::applyBass);
        setupSlider(findViewById(R.id.sVirt), "virt", Effects::applyVirt);
        setupSlider(findViewById(R.id.sLoud), "loud", Effects::applyLoud);
    }

    /** Bahagian "Addon kualiti" — hanya muncul bila addon dipasang */
    private void buildQualityAddons() {
        LinearLayout box = findViewById(R.id.qaBox);
        box.removeAllViews();
        boolean any = false;
        android.view.LayoutInflater inf = android.view.LayoutInflater.from(this);
        if (AddonManager.installed("autoeq")) {
            any = true;
            View r = inf.inflate(R.layout.item_row, box, false);
            r.setPadding(0, 0, 0, 0);
            android.widget.ImageView iv = r.findViewById(R.id.rowIcon);
            iv.setImageResource(R.drawable.ic_eq);
            Ui.tint(iv, Ui.attr(this, R.attr.mcAccent));
            ((TextView) r.findViewById(R.id.rowTitle)).setText(R.string.qa_autoeq);
            TextView sub = r.findViewById(R.id.rowSub);
            sub.setVisibility(View.VISIBLE);
            Effects.Profile p = Effects.currentProfile();
            sub.setText(p != null ? p.name + " • AutoEq (" + p.src + ")" : getString(R.string.qa_autoeq_pick));
            r.setOnClickListener(v -> pickEarphone());
            box.addView(r);
        }
        if (AddonManager.installed("normalize")) {
            any = true;
            box.addView(addonSwitch(R.string.qa_normalize, R.string.qa_normalize_sub, "normalize", Effects::applyQualityAddons));
        }
        if (AddonManager.installed("autoquality")) {
            any = true;
            box.addView(addonSwitch(R.string.qa_autoq, R.string.qa_autoq_sub, "autoQ", Resolver::clear));
        }
        if (AddonManager.installed("crossfade")) {
            any = true;
            box.addView(addonSwitch(R.string.qa_crossfade, R.string.qa_crossfade_sub, "crossfade", () -> { }));
            ChipGroup secs = new ChipGroup(this);
            secs.setSingleSelection(true);
            int cur = Prefs.getI("crossfadeSec", 4);
            for (int sec : new int[]{2, 4, 6, 8, 10}) {
                Chip c = new Chip(this);
                c.setText(sec + "s");
                c.setCheckable(true);
                c.setChecked(sec == cur);
                c.setOnClickListener(v -> Prefs.putI("crossfadeSec", sec));
                secs.addView(c);
            }
            box.addView(secs);
        }
        findViewById(R.id.qaTitle).setVisibility(any ? View.VISIBLE : View.GONE);
        findViewById(R.id.qaNote).setVisibility(any && Build.VERSION.SDK_INT < 28 ? View.VISIBLE : View.GONE);
    }

    private View addonSwitch(int title, int sub, String key, Runnable apply) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        MaterialSwitch sw = new MaterialSwitch(this);
        sw.setText(title);
        sw.setTextColor(Ui.attr(this, R.attr.mcText));
        sw.setTextSize(16);
        sw.setChecked(Prefs.getB(key, true));
        sw.setOnCheckedChangeListener((b, on) -> {
            Prefs.putB(key, on);
            apply.run();
        });
        TextView t = new TextView(this);
        t.setText(sub);
        t.setTextColor(Ui.attr(this, R.attr.mcTextDim));
        t.setTextSize(12);
        col.addView(sw);
        col.addView(t);
        return col;
    }

    private void pickEarphone() {
        java.util.List<Effects.Profile> ps = Effects.profiles();
        String[] names = new String[ps.size() + 1];
        names[0] = getString(R.string.qa_autoeq_off);
        String cur = Prefs.getS("autoeqModel", "");
        int checked = 0;
        for (int i = 0; i < ps.size(); i++) {
            names[i + 1] = ps.get(i).name;
            if (ps.get(i).name.equals(cur)) checked = i + 1;
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.qa_autoeq)
                .setSingleChoiceItems(names, checked, (d, w) -> {
                    Prefs.putS("autoeqModel", w == 0 ? "" : names[w]);
                    Effects.applyQualityAddons();
                    d.dismiss();
                    buildQualityAddons();
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    private void setupSlider(Slider s, String key, Runnable apply) {
        s.setValue(Math.min(s.getValueTo(), Math.max(0, Prefs.getI(key, 0))));
        s.addOnChangeListener((sl, value, fromUser) -> {
            if (!fromUser) return;
            Prefs.putI(key, (int) value);
            apply.run();
        });
    }

    private void buildBands() {
        LinearLayout box = findViewById(R.id.eqBands);
        box.removeAllViews();
        bandSliders.clear();
        int n = 5;
        int min = -1500, max = 1500;
        int[] freqs = {60000, 230000, 910000, 3600000, 14000000};
        if (Effects.eq != null) {
            try {
                n = Effects.eq.getNumberOfBands();
                short[] r = Effects.eq.getBandLevelRange();
                min = r[0];
                max = r[1];
                freqs = new int[n];
                for (short b = 0; b < n; b++) freqs[b] = Effects.eq.getCenterFreq(b);
            } catch (Throwable t) {
                findViewById(R.id.eqUnsupported).setVisibility(View.VISIBLE);
            }
        }
        for (int b = 0; b < n; b++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView label = new TextView(this);
            int hz = freqs[b] / 1000;
            label.setText(hz >= 1000 ? String.format(Locale.US, "%.1fk", hz / 1000f) : hz + "");
            label.setTextColor(Ui.attr(this, R.attr.mcTextDim));
            label.setTextSize(12);
            label.setMinWidth(Ui.dp(this, 44));
            Slider s = new Slider(this);
            s.setValueFrom(min);
            s.setValueTo(max);
            s.setLabelBehavior(com.google.android.material.slider.LabelFormatter.LABEL_GONE);
            s.setValue(Math.max(min, Math.min(max, Effects.bandLevel(b))));
            final int band = b;
            s.addOnChangeListener((sl, value, fromUser) -> {
                if (!fromUser) return;
                Prefs.putI("eqBand" + band, (int) value);
                Prefs.putS("eqPreset", "custom");
                Effects.applyEq();
            });
            row.addView(label);
            row.addView(s, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            box.addView(row);
            bandSliders.add(s);
        }
    }

    private void syncBands() {
        for (int b = 0; b < bandSliders.size(); b++) {
            Slider s = bandSliders.get(b);
            s.setValue(Math.max(s.getValueFrom(), Math.min(s.getValueTo(), Effects.bandLevel(b))));
        }
    }

    private void openSystemEffects() {
        Intent i = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
        i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, Effects.session);
        i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        i.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
        try {
            startActivityForResult(i, 3);
            return;
        } catch (Exception ignored) { }
        try {
            startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
