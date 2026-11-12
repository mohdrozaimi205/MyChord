package com.mychord.app;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

public class SearchFragment extends Fragment {
    private TrackAdapter adapter;
    private ProgressBar progress;
    private TextView empty;
    private EditText input;
    private int searchId = 0;

    private static final String[] SUGGEST = {"Siti Nurhaliza", "Hindia", "Faizal Tahir", "Taylor Swift",
            "Ernie Zakri", "Bruno Mars", "Search", "Exists", "Lofi", "Lagu raya", "Nasyid"};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        View v = inf.inflate(R.layout.fragment_search, c, false);
        input = v.findViewById(R.id.searchInput);
        progress = v.findViewById(R.id.searchProgress);
        empty = v.findViewById(R.id.searchEmpty);
        RecyclerView rv = v.findViewById(R.id.searchList);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TrackAdapter(new TrackAdapter.Listener() {
            @Override
            public void onClick(int pos) { Nav.open(requireActivity(), adapter.items, pos); }

            @Override
            public void onMore(View anchor, int pos) { TrackMenu.show(requireActivity(), anchor, adapter.items.get(pos), null); }
        });
        rv.setAdapter(adapter);

        input.setOnEditorActionListener((tv, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                run(tv.getText().toString());
                return true;
            }
            return false;
        });

        ChipGroup chips = v.findViewById(R.id.searchChips);
        for (String q : SUGGEST) {
            Chip chip = new Chip(requireContext());
            chip.setText(q);
            chip.setOnClickListener(x -> {
                input.setText(q);
                run(q);
            });
            chips.addView(chip);
        }
        return v;
    }

    private void run(String q) {
        q = q.trim();
        if (q.isEmpty()) return;
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        final int my = ++searchId;
        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        adapter.set(null);
        final String query = q;
        App.IO.execute(() -> {
            try {
                List<Track> r = YtMusic.search(query);
                App.MAIN.post(() -> {
                    if (!isAdded() || my != searchId) return;
                    progress.setVisibility(View.GONE);
                    adapter.set(r);
                    if (r.isEmpty()) {
                        empty.setText(R.string.no_results);
                        empty.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                App.MAIN.post(() -> {
                    if (!isAdded() || my != searchId) return;
                    progress.setVisibility(View.GONE);
                    empty.setText(R.string.error_net);
                    empty.setVisibility(View.VISIBLE);
                });
            }
        });
    }
}
