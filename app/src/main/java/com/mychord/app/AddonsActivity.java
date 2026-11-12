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

        android.widget.EditText url = findViewById(R.id.addonUrl);
        View prog = findViewById(R.id.addonProgress);
        findViewById(R.id.addonAdd).setOnClickListener(v -> {
            String in = url.getText().toString().trim();
            if (in.isEmpty()) return;
            prog.setVisibility(View.VISIBLE);
            App.IO.execute(() -> {
                String name = null;
                try { name = CustomAddons.add(in); } catch (Throwable ignored) { }
                final String n = name;
                App.MAIN.post(() -> {
                    prog.setVisibility(View.GONE);
                    if (n != null) {
                        url.setText("");
                        Toast.makeText(this, getString(R.string.addon_added, n), Toast.LENGTH_LONG).show();
                        load();
                    } else Toast.makeText(this, R.string.addon_add_fail, Toast.LENGTH_LONG).show();
                });
            });
        });
        String shared = getIntent().getStringExtra(android.content.Intent.EXTRA_TEXT);
        if (shared != null) url.setText(shared);

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
                if (a.custom) {
                    btn.setText(R.string.addon_remove);
                    btn.setOnClickListener(x -> {
                        CustomAddons.remove(a.id);
                        load();
                    });
                    return;
                }
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
            List<AddonManager.Addon> list = new ArrayList<>();
            for (org.json.JSONObject o : CustomAddons.all()) {
                AddonManager.Addon a = new AddonManager.Addon();
                a.id = o.optString("id");
                a.name = a.nameEn = o.optString("name");
                a.desc = a.descEn = getString(R.string.addon_custom) + " • " + o.optString("desc");
                a.custom = true;
                list.add(a);
            }
            list.addAll(AddonManager.catalog());
            App.MAIN.post(() -> {
                items.clear();
                items.addAll(list);
                adapter.notifyDataSetChanged();
            });
        });
    }
}
