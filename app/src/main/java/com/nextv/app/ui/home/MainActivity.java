package com.nextv.app.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nextv.app.R;
import com.nextv.app.data.Channel;
import com.nextv.app.data.ChannelRepository;
import com.nextv.app.ui.player.PlayerActivity;
import com.nextv.app.ui.settings.SettingsActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity
        implements ChannelAdapter.OnChannelClickListener {

    // ── Views ──────────────────────────────────────────────────────────
    private RecyclerView   rvChannels;
    private ChannelAdapter adapter;
    private View           layoutLoading;
    private View           layoutError;
    private TextView       tvErrorMsg;
    private TextView       tvChannelCount;
    private TextView       tvTotalChannel;
    private TextView       tvCustomCount;
    private TextView       tvSectionTitle;
    private TextView       tvActiveCategory;
    private LinearLayout   categoryContainer;
    private LinearLayout   qualityContainer;
    private EditText       etSearch;

    // ── State ──────────────────────────────────────────────────────────
    private List<Channel>        allChannels      = new ArrayList<>();
    private String               selectedCategory = "Semua";
    private String               selectedQuality  = "Auto";
    private Map<String, Integer> categoryCounts   = new LinkedHashMap<>();

    // Urutan prioritas kategori — kategori lain dari JSON otomatis ditambah di bawah
    private static final List<String> PRIORITY_CATS = Arrays.asList(
        "Semua", "Nasional", "Berita", "Vision+", "IndiHome",
        "Internasional", "Olahraga", "Film", "Anak-anak", "Musik", "Dokumenter"
    );

    // Ikon singkat per kategori untuk sidebar
    private static String getCatIcon(String cat) {
        if (cat == null) return "TV";
        switch (cat) {
            case "Semua":         return "ALL";
            case "Nasional":      return "TV";
            case "Berita":        return "NWS";
            case "Vision+":       return "V+";
            case "IndiHome":      return "IH";
            case "Internasional": return "INT";
            case "Olahraga":      return "SPT";
            case "Film":          return "MOV";
            case "Anak-anak":     return "KID";
            case "Musik":         return "MUS";
            case "Dokumenter":    return "DOC";
            case "Umum":          return "GEN";
            default:
                // Ambil 3 huruf pertama
                return cat.length() >= 3
                    ? cat.substring(0, 3).toUpperCase()
                    : cat.toUpperCase();
        }
    }

    private static final String[] QUALITY_LABELS = {"360p", "720p", "1080p", "4K", "Auto"};

    // ── Lifecycle ──────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        loadChannels();
    }

    private void initViews() {
        rvChannels        = findViewById(R.id.rv_channels);
        layoutLoading     = findViewById(R.id.progress_bar);
        layoutError       = findViewById(R.id.tv_error);
        tvErrorMsg        = findViewById(R.id.tv_error_msg);
        tvChannelCount    = findViewById(R.id.tv_channel_count);
        tvTotalChannel    = findViewById(R.id.tv_total_channel);
        tvCustomCount     = findViewById(R.id.tv_custom_count);
        tvSectionTitle    = findViewById(R.id.tv_section_title);
        tvActiveCategory  = findViewById(R.id.tv_active_category);
        categoryContainer = findViewById(R.id.category_container);
        qualityContainer  = findViewById(R.id.quality_container);
        etSearch          = findViewById(R.id.et_search);

        int spanCount = getResources().getConfiguration().screenWidthDp > 700 ? 5 : 4;
        rvChannels.setLayoutManager(new GridLayoutManager(this, spanCount));
        adapter = new ChannelAdapter(this, this);
        rvChannels.setAdapter(adapter);

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) { applyFilters(); }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        View btnSettings = findViewById(R.id.btn_settings);
        if (btnSettings != null)
            btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        View btnRefresh = findViewById(R.id.btn_refresh);
        if (btnRefresh != null)
            btnRefresh.setOnClickListener(v -> loadChannels());

        View btnForceRefresh = findViewById(R.id.btn_force_refresh);
        if (btnForceRefresh != null)
            btnForceRefresh.setOnClickListener(v -> {
                ChannelRepository.getInstance(this).clearCache();
                loadChannels();
                Toast.makeText(this, "Cache dihapus, memuat ulang…", Toast.LENGTH_SHORT).show();
            });

        View btnRetry = findViewById(R.id.btn_retry);
        if (btnRetry != null)
            btnRetry.setOnClickListener(v -> loadChannels());

        buildQualityButtons();
    }

    // ── Load ───────────────────────────────────────────────────────────
    private void loadChannels() {
        showLoading(true);
        ChannelRepository.getInstance(this).loadChannels(new ChannelRepository.Callback() {
            @Override
            public void onSuccess(List<Channel> channels) {
                allChannels = channels;
                showLoading(false);
                countCategories();
                buildCategorySidebar();
                applyFilters();
                updateInfoCard();
            }
            @Override
            public void onError(String message) {
                showLoading(false);
                if (layoutError != null) layoutError.setVisibility(View.VISIBLE);
                if (tvErrorMsg  != null) tvErrorMsg.setText("Gagal: " + message);
            }
        });
    }

    // ── Categories ─────────────────────────────────────────────────────
    private void countCategories() {
        categoryCounts.clear();
        categoryCounts.put("Semua", allChannels.size());
        for (Channel ch : allChannels) {
            String cat = ch.getCategory();
            // Pastikan category tidak null/kosong
            if (cat == null || cat.trim().isEmpty()) cat = "Umum";
            Integer prev = categoryCounts.get(cat);
            categoryCounts.put(cat, prev != null ? prev + 1 : 1);
        }
    }

    private void buildCategorySidebar() {
        if (categoryContainer == null) return;
        categoryContainer.removeAllViews();

        // Gabung: urutan prioritas dulu, sisanya alphabetis
        List<String> ordered = new ArrayList<>();
        for (String c : PRIORITY_CATS) {
            if (categoryCounts.containsKey(c)) ordered.add(c);
        }
        for (String c : categoryCounts.keySet()) {
            if (!ordered.contains(c)) ordered.add(c);
        }

        for (String cat : ordered) {
            Integer cnt = categoryCounts.get(cat);
            if (cnt == null) continue;

            View item = getLayoutInflater().inflate(
                R.layout.item_category_chip, categoryContainer, false);

            TextView tvIcon  = item.findViewById(R.id.tv_cat_icon);
            TextView tvName  = item.findViewById(R.id.tv_category);
            TextView tvCount = item.findViewById(R.id.tv_cat_count);

            if (tvIcon  != null) tvIcon.setText(getCatIcon(cat));
            if (tvName  != null) tvName.setText(cat);
            if (tvCount != null) tvCount.setText(String.valueOf(cnt));

            item.setSelected(cat.equals(selectedCategory));

            final String finalCat = cat;
            item.setOnClickListener(v -> {
                selectedCategory = finalCat;
                for (int i = 0; i < categoryContainer.getChildCount(); i++) {
                    View child = categoryContainer.getChildAt(i);
                    TextView t = child.findViewById(R.id.tv_category);
                    if (t != null)
                        child.setSelected(t.getText().toString().equals(finalCat));
                }
                applyFilters();
                updateTopBarCategory();
            });

            categoryContainer.addView(item);
        }
    }

    private void buildQualityButtons() {
        if (qualityContainer == null) return;
        qualityContainer.removeAllViews();
        for (String q : QUALITY_LABELS) {
            View btn = getLayoutInflater().inflate(
                R.layout.item_quality_chip, qualityContainer, false);
            // item_quality_chip root IS the TextView
            TextView tv = (btn instanceof TextView)
                ? (TextView) btn : (TextView) btn.findViewById(R.id.tv_quality_label);
            if (tv == null) continue;
            tv.setText(q);
            tv.setSelected(q.equals(selectedQuality));
            tv.setOnClickListener(v -> {
                selectedQuality = q;
                for (int i = 0; i < qualityContainer.getChildCount(); i++) {
                    View child = qualityContainer.getChildAt(i);
                    TextView t = (child instanceof TextView)
                        ? (TextView) child
                        : (TextView) child.findViewById(R.id.tv_quality_label);
                    if (t != null) t.setSelected(t.getText().toString().equals(q));
                }
                applyFilters();
            });
            qualityContainer.addView(btn);
        }
    }

    // ── Filter ─────────────────────────────────────────────────────────
    private void applyFilters() {
        String query = etSearch != null ? etSearch.getText().toString().trim() : "";
        List<Channel> filtered = new ArrayList<>();
        for (Channel ch : allChannels) {
            String cat = ch.getCategory();
            if (cat == null || cat.trim().isEmpty()) cat = "Umum";

            boolean catOk     = "Semua".equals(selectedCategory) || selectedCategory.equals(cat);
            boolean searchOk  = query.isEmpty() ||
                ch.getName().toLowerCase().contains(query.toLowerCase());
            boolean qualityOk = "Auto".equals(selectedQuality) ||
                matchQuality(ch.getQuality(), selectedQuality);

            if (catOk && searchOk && qualityOk) filtered.add(ch);
        }

        if (adapter != null) adapter.setChannels(filtered);
        if (tvChannelCount != null) tvChannelCount.setText(String.valueOf(filtered.size()));
        if (tvSectionTitle != null) tvSectionTitle.setText(selectedCategory);
        updateTopBarCategory();
    }

    private boolean matchQuality(String q, String filter) {
        if (q == null) return false;
        switch (filter) {
            case "4K":    return q.equalsIgnoreCase("4K");
            case "1080p": return q.equalsIgnoreCase("FHD") || q.equalsIgnoreCase("1080p");
            case "720p":  return q.equalsIgnoreCase("HD")  || q.equalsIgnoreCase("720p");
            case "360p":  return q.equalsIgnoreCase("SD")  || q.equalsIgnoreCase("360p");
            default:      return true;
        }
    }

    private void updateTopBarCategory() {
        if (tvActiveCategory == null) return;
        String icon = getCatIcon(selectedCategory);
        tvActiveCategory.setText("[" + icon + "] " + selectedCategory.toUpperCase());
    }

    // ── Info card ──────────────────────────────────────────────────────
    private void updateInfoCard() {
        if (tvTotalChannel != null)
            tvTotalChannel.setText(String.valueOf(allChannels.size()));
        // Custom = channel yang punya DRM atau URL non-default
        int customCount = 0;
        for (Channel ch : allChannels) {
            if (ch.hasDrm()) customCount++;
        }
        if (tvCustomCount != null) tvCustomCount.setText(String.valueOf(customCount));
    }

    // ── UI helpers ─────────────────────────────────────────────────────
    private void showLoading(boolean show) {
        if (layoutLoading != null) layoutLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        if (rvChannels    != null) rvChannels.setVisibility(show ? View.GONE : View.VISIBLE);
        if (layoutError   != null) layoutError.setVisibility(View.GONE);
    }

    // ── Channel click ──────────────────────────────────────────────────
    @Override
    public void onChannelClick(Channel channel) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_CHANNEL_NAME,  channel.getName());
        intent.putExtra(PlayerActivity.EXTRA_CHANNEL_URL,   channel.getUrl());
        intent.putExtra(PlayerActivity.EXTRA_CHANNEL_LOGO,  channel.getLogo());
        // Kirim index channel agar PlayerActivity bisa navigasi prev/next
        int idx = allChannels.indexOf(channel);
        intent.putExtra(PlayerActivity.EXTRA_CHANNEL_INDEX, idx >= 0 ? idx : 0);
        if (channel.getDrmScheme()     != null)
            intent.putExtra(PlayerActivity.EXTRA_DRM_SCHEME,     channel.getDrmScheme());
        if (channel.getDrmLicenseUrl() != null)
            intent.putExtra(PlayerActivity.EXTRA_DRM_LICENSE_URL, channel.getDrmLicenseUrl());
        if (channel.getDrmKeyId()      != null)
            intent.putExtra(PlayerActivity.EXTRA_DRM_KEY_ID,      channel.getDrmKeyId());
        if (channel.getDrmKey()        != null)
            intent.putExtra(PlayerActivity.EXTRA_DRM_KEY,         channel.getDrmKey());
        startActivity(intent);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
