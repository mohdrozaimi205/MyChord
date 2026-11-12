package com.mychord.app;

import android.content.Intent;
import android.media.audiofx.AudioEffect;
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

        setupSlider(findViewById(R.id.sBass), "bass", Effects::applyBass);
        setupSlider(findViewById(R.id.sVirt), "virt", Effects::applyVirt);
        setupSlider(findViewById(R.id.sLoud), "loud", Effects::applyLoud);
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
