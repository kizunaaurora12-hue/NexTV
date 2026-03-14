package com.nextv.app.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nextv.app.R;

import java.util.ArrayList;
import java.util.List;

public class SidebarAdapter extends RecyclerView.Adapter<SidebarAdapter.VH> {

    public interface OnCategoryClick {
        void onClick(String categoryName, int position);
    }

    // [categoryName, count]
    private final List<String[]> items = new ArrayList<>();
    private int selectedPosition = 0;
    private final OnCategoryClick listener;

    public SidebarAdapter(OnCategoryClick listener) {
        this.listener = listener;
    }

    public void setItems(List<String[]> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    public void selectPosition(int pos) {
        int prev = selectedPosition;
        selectedPosition = pos;
        notifyItemChanged(prev);
        notifyItemChanged(selectedPosition);
    }

    private static String getCatIcon(String name) {
        if (name == null) return "CH";
        String key = name.toLowerCase().trim();
        if (key.contains("semua") || key.contains("all"))     return "ALL";
        if (key.contains("nasional"))                          return "TV";
        if (key.contains("berita") || key.contains("news"))    return "NWS";
        if (key.contains("vision"))                            return "VIS";
        if (key.contains("indihome"))                          return "IND";
        if (key.contains("internasional") || key.contains("int")) return "INT";
        if (key.contains("olahraga") || key.contains("sport")) return "SPT";
        if (key.contains("film") || key.contains("movie"))     return "MOV";
        if (key.contains("anak"))                              return "KID";
        if (key.contains("musik") || key.contains("music"))    return "MUS";
        if (key.contains("dok"))                               return "DOC";
        if (key.contains("custom"))                            return "CST";
        if (key.contains("jepang") || key.contains("japan"))   return "JPN";
        if (key.contains("favorit"))                           return "\u2605";
        return name.length() >= 3 ? name.substring(0, 3).toUpperCase() : name.toUpperCase();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_sidebar, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        String[] item = items.get(position);
        String name  = item[0];
        String count = item.length > 1 ? item[1] : "0";

        h.tvIcon.setText(getCatIcon(name));
        h.tvName.setText(name);
        h.tvCount.setText(Integer.parseInt(count) > 99 ? "99+" : count);

        boolean selected = (position == selectedPosition);
        h.accent.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        h.tvName.setTextColor(selected ? 0xFFFFD700 : 0xE0B0BAD0);
        h.root.setBackgroundColor(selected ? 0x1AFFD700 : 0x00000000);

        h.root.setOnClickListener(v -> {
            int pos = h.getAdapterPosition();
            if (pos == RecyclerView.NO_ID) return;
            int prev = selectedPosition;
            selectedPosition = pos;
            notifyItemChanged(prev);
            notifyItemChanged(pos);
            if (listener != null) listener.onClick(name, pos);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        View     accent, root;
        TextView tvIcon, tvName, tvCount;

        VH(View v) {
            super(v);
            root    = v.findViewById(R.id.sidebar_item_root);
            accent  = v.findViewById(R.id.sidebar_accent);
            tvIcon  = v.findViewById(R.id.sidebar_icon);
            tvName  = v.findViewById(R.id.sidebar_name);
            tvCount = v.findViewById(R.id.sidebar_count);
        }
    }
}
