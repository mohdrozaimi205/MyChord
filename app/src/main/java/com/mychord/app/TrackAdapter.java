package com.mychord.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.VH> {
    public interface Listener {
        void onClick(int pos);
        void onMore(View anchor, int pos);
    }

    public final List<Track> items = new ArrayList<>();
    private final Listener l;
    private final boolean cards;
    private boolean verticalMode = false;

    public TrackAdapter(Listener l) { this(l, false); }

    public TrackAdapter(Listener l, boolean cards) {
        this.l = l;
        this.cards = cards;
    }

    /** Mod vertikal — guna item_track walaupun ini card adapter */
    public void setVerticalMode(boolean vertical) { this.verticalMode = vertical; }

    public void set(List<Track> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView art;
        TextView title, sub, badge;
        ImageButton more;

        VH(View v, boolean cards) {
            super(v);
            if (cards) {
                art = v.findViewById(R.id.cardArt);
                title = v.findViewById(R.id.cardTitle);
                sub = v.findViewById(R.id.cardSub);
            } else {
                art = v.findViewById(R.id.trackArt);
                title = v.findViewById(R.id.trackTitle);
                sub = v.findViewById(R.id.trackSub);
                badge = v.findViewById(R.id.trackBadge);
                more = v.findViewById(R.id.trackMore);
            }
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        boolean useCard = cards && !verticalMode;
        View v = LayoutInflater.from(parent.getContext())
                .inflate(useCard ? R.layout.item_card : R.layout.item_track, parent, false);
        return new VH(v, useCard);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Track t = items.get(position);
        h.title.setText(t.title);
        String sub = t.artist;
        if (!cards && t.durationSec > 0) sub = (sub.isEmpty() ? "" : sub + " • ") + Ui.time(t.durationSec * 1000);
        h.sub.setText(sub);
        Ui.art(h.art, t.art);
        if (h.badge != null) {
            if (t.badge != null && !t.badge.isEmpty()) {
                h.badge.setVisibility(View.VISIBLE);
                h.badge.setText(t.badge);
            } else h.badge.setVisibility(View.GONE);
        }
        h.itemView.setOnClickListener(v -> l.onClick(h.getBindingAdapterPosition()));
        if (h.more != null) h.more.setOnClickListener(v -> l.onMore(v, h.getBindingAdapterPosition()));
        if (cards) h.itemView.setOnLongClickListener(v -> {
            l.onMore(v, h.getBindingAdapterPosition());
            return true;
        });
    }

    @Override
    public int getItemCount() { return items.size(); }
}
