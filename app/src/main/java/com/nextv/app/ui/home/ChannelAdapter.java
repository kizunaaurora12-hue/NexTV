package com.nextv.app.ui.home;

import android.content.Context;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.nextv.app.R;
import com.nextv.app.data.Channel;

import java.util.ArrayList;
import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ViewHolder> {

    public interface OnChannelClickListener {
        void onChannelClick(Channel channel);
    }

    private static final int[] CATEGORY_COLORS = {
        0xFF1d4ed8, 0xFF7c3aed, 0xFFd97706,
        0xFF059669, 0xFFdb2777, 0xFF0891b2,
        0xFFb91c1c, 0xFF4338ca, 0xFF65a30d
    };

    private final Context context;
    private final OnChannelClickListener listener;
    private List<Channel> channels = new ArrayList<>();

    public ChannelAdapter(Context context, OnChannelClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setChannels(List<Channel> channels) {
        this.channels = channels != null ? channels : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_channel, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(channels.get(position), position);
    }

    @Override
    public int getItemCount() { return channels.size(); }

    class ViewHolder extends RecyclerView.ViewHolder {

        ImageView ivLogo;
        TextView tvName, tvQuality, tvCategory;
        TextView tvDrmBadge;
        View cardRoot, liveIndicator;

        ViewHolder(View itemView) {
            super(itemView);
            ivLogo       = itemView.findViewById(R.id.iv_logo);
            tvName       = itemView.findViewById(R.id.tv_channel_name);
            tvQuality    = itemView.findViewById(R.id.tv_quality);
            tvCategory   = itemView.findViewById(R.id.tv_category_label);
            tvDrmBadge   = itemView.findViewById(R.id.tv_drm_badge);
            cardRoot     = itemView.findViewById(R.id.card_root);
            liveIndicator = itemView.findViewById(R.id.live_indicator);

            itemView.setFocusable(true);
            itemView.setFocusableInTouchMode(false);
            itemView.setOnFocusChangeListener((v, hasFocus) -> {
                v.animate()
                    .scaleX(hasFocus ? 1.06f : 1.0f)
                    .scaleY(hasFocus ? 1.06f : 1.0f)
                    .setDuration(120)
                    .start();
                if (cardRoot != null) cardRoot.setSelected(hasFocus);
            });
        }

        void bind(Channel channel, int position) {
            tvName.setText(channel.getName());

            // Quality badge
            String q = channel.getQuality().toUpperCase();
            tvQuality.setText(q);
            switch (q) {
                case "4K":  tvQuality.setBackgroundResource(R.drawable.badge_4k);  break;
                case "FHD": tvQuality.setBackgroundResource(R.drawable.badge_fhd); break;
                case "HD":  tvQuality.setBackgroundResource(R.drawable.badge_hd);  break;
                default:    tvQuality.setBackgroundResource(R.drawable.badge_sd);  break;
            }

            // Category label
            if (tvCategory != null) tvCategory.setText(channel.getCategory());

            // LIVE indicator
            if (liveIndicator != null)
                liveIndicator.setVisibility(channel.isLive() ? View.VISIBLE : View.GONE);

            // DRM badge
            if (tvDrmBadge != null)
                tvDrmBadge.setVisibility(channel.hasDrm() ? View.VISIBLE : View.GONE);

            // Load logo
            if (!channel.getLogo().isEmpty()) {
                Glide.with(context)
                    .load(channel.getLogo())
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_tv_placeholder)
                    .error(R.drawable.ic_tv_placeholder)
                    .into(ivLogo);
            } else {
                int colorIdx = Math.abs(channel.getName().hashCode()) % CATEGORY_COLORS.length;
                ivLogo.setBackgroundColor(CATEGORY_COLORS[colorIdx]);
                ivLogo.setImageResource(R.drawable.ic_tv_placeholder);
            }

            // Click
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onChannelClick(channel);
            });

            itemView.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                    if (listener != null) listener.onChannelClick(channel);
                    return true;
                }
                return false;
            });
        }
    }
}
