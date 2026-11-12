package com.mychord.app;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class BaseActivity extends AppCompatActivity {
    protected MiniPlayer mini;
    private String themeApplied;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(ThemeUtil.style());
        themeApplied = Prefs.theme();
        super.onCreate(savedInstanceState);
    }

    /** Bar atas dengan butang kembali */
    protected void setupTop(String title) {
        View top = findViewById(R.id.top);
        if (top == null) return;
        ((TextView) top.findViewById(R.id.topTitle)).setText(title);
        ImageButton back = top.findViewById(R.id.topBack);
        back.setOnClickListener(v -> finish());
    }

    protected void setupMini() {
        View v = findViewById(R.id.mini);
        if (v != null) mini = new MiniPlayer(this, v);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mini != null) mini.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (themeApplied != null && !themeApplied.equals(Prefs.theme())) recreate();
    }

    @Override
    protected void onStop() {
        if (mini != null) mini.stop();
        super.onStop();
    }
}
