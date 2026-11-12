package com.mychord.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

public class AddonsActivity extends BaseActivity {
    private final List<AddonManager.Addon> items = new ArrayList<>();
    private RecyclerView.Adapter<RecyclerView.ViewHolder> adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_addons);
        setupTop(getString(R.string.addons_title));

        MaterialSwitch auto = findViewById(R.id.addonAuto);
        auto.setChecked(AddonManager.autoInstall());
        auto.setOnCheckedChangeListener((b, on) -> Prefs.putB("addonAuto", on));

        findViewById(R.id.addonCheck).setOnClickListener(v -> App.IO.execute(() -> {
            List<String> added = AddonManager.sync();
            App.MAIN.post(() -> {
                Toast.makeText(this, added.isEmpty() ? getString(R.string.addons_uptodate)
                        : getString(R.string.addons_new, String.join(", ", added)), Toast.LENGTH_LONG).show();
                load();
            });
        }));

        RecyclerView rv = findViewById(R.id.addonList);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
                View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_addon, p, false);
                return new RecyclerView.ViewHolder(v) { };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
                AddonManager.Addon a = items.get(pos);
                View v = h.itemView;
                ((TextView) v.findViewById(R.id.addonLetter)).setText(AddonManager.letter(a));
                ((TextView) v.findViewById(R.id.addonName)).setText(a.title());
                ((TextView) v.findViewById(R.id.addonDesc)).setText(a.about());
                MaterialButton btn = v.findViewById(R.id.addonBtn);
                boolean on = AddonManager.installed(a.id);
                btn.setText(on ? R.string.addon_remove : R.string.addon_install);
                btn.setOnClickListener(x -> {
                    AddonManager.setInstalled(a.id, !on);
                    notifyItemChanged(h.getBindingAdapterPosition());
                });
            }

            @Override
            public int getItemCount() { return items.size(); }
        };
        rv.setAdapter(adapter);
        load();
    }

    private void load() {
        App.IO.execute(() -> {
            List<AddonManager.Addon> list = AddonManager.catalog();
            App.MAIN.post(() -> {
                items.clear();
                items.addAll(list);
                adapter.notifyDataSetChanged();
            });
        });
    }
}
