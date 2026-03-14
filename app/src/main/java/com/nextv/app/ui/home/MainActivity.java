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

public class MainActivity extends AppCompatActivity implements ChannelAdapter.OnChannelClickListener {

    // ── Views ──────────────────────────────────────────────────────────
    private RecyclerView    rvChannels;
    private ChannelAdapter  adapter;
    private View            layoutLoading;
    private View            layoutError;
    private TextView        tvErrorMsg;
    private TextView        tvChannelCount;
    private TextView        tvTotalChannel;
    private TextView        tvCustomCount;
    private TextView        tvSectionTitle;
    private TextView        tvActiveCategory;
    private LinearLayout    categoryContainer;
    private LinearLayout    qualityContainer;
    private EditText        etSearch;

    // ── State ──────────────────────────────────────────────────────────
    private List<Channel>        allChannels      = new ArrayList<>();
    private String               selectedCategory = "Semua";
    private String               selectedQuality  = "Auto";
    private Map<String, Integer> categoryCounts   = new LinkedHashMap<>();

    // Urutan kategori seperti di gambar
    private static final List<String> CATEGORY_ORDER = Arrays.asList(
        "Semua", "Nasional", "Berita", "Internasional",
        "Olahraga", "Film", "Anak-anak", "Musik", "Dokumenter"
    );

    // Ikon singkat per kategori (ditampilkan di sidebar)
    private static final Map<String, String> CAT_ICONS = new LinkedHashMap<String, String>() {{
        put("Semua",         "ALL");
        put("Nasional",      "TV");
        put("Berita",        "NWS");
        put("Internasional", "INT");
        put("Olahraga",      "SPT");
        put("Film",          "MOV");
        put("Anak-anak",     "KID");
        put("Musik",         "MUS");
        put("Dokumenter",    "DOC");
    }};

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
        rvChannels       = findViewById(R.id.rv_channels);
        layoutLoading    = findViewById(R.id.progress_bar);
        layoutError      = findViewById(R.id.tv_error);
        tvErrorMsg       = findViewById(R.id.tv_error_msg);
        tvChannelCount   = findViewById(R.id.tv_channel_count);
        tvTotalChannel   = findViewById(R.id.tv_total_channel);
        tvCustomCount    = findViewById(R.id.tv_custom_count);
        tvSectionTitle   = findViewById(R.id.tv_section_title);
        tvActiveCategory = findViewById(R.id.tv_active_category);
        categoryContainer = findViewById(R.id.category_container);
        qualityContainer  = findViewById(R.id.quality_container);
        etSearch          = findViewById(R.id.et_search);

        // Grid 5 kolom di layar lebar (TV), 4 kolom di tablet
        int spanCount = getResources().getConfiguration().screenWidthDp > 700 ? 5 : 4;
        rvChannels.setLayoutManager(new GridLayoutManager(this, spanCount));
        adapter = new ChannelAdapter(this, this);
        rvChannels.setAdapter(adapter);

        // Search
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                    applyFilters();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        // Buttons
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

        // Build quality filter buttons
        buildQualityButtons();
    }

    // ── Data loading ───────────────────────────────────────────────────
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
            if (cat != null && !cat.isEmpty())
                categoryCounts.put(cat, getCount(categoryCounts, cat) + 1);
        }
    }

    private void buildCategorySidebar() {
        if (categoryContainer == null) return;
        categoryContainer.removeAllViews();

        // Tampilkan sesuai urutan prioritas, lalu sisanya
        List<String> orderedCats = new ArrayList<>();
        for (String c : CATEGORY_ORDER) {
            if (categoryCounts.containsKey(c)) orderedCats.add(c);
        }
        for (String c : categoryCounts.keySet()) {
            if (!orderedCats.contains(c)) orderedCats.add(c);
        }

        for (String cat : orderedCats) {
            View item = getLayoutInflater().inflate(
                R.layout.item_category_chip, categoryContainer, false);

            TextView tvIcon  = item.findViewById(R.id.tv_cat_icon);
            TextView tvName  = item.findViewById(R.id.tv_category);
            TextView tvCount = item.findViewById(R.id.tv_cat_count);

            if (tvName  != null) tvName.setText(cat);
            if (tvIcon  != null) tvIcon.setText(CAT_ICONS.containsKey(cat) ? CAT_ICONS.get(cat) : "TV");
            if (tvCount != null) tvCount.setText(String.valueOf(getCount(categoryCounts, cat)));

            item.setSelected(cat.equals(selectedCategory));

            item.setOnClickListener(v -> {
                selectedCategory = cat;
                // Update selected state
                for (int i = 0; i < categoryContainer.getChildCount(); i++) {
                    View child = categoryContainer.getChildAt(i);
                    TextView t = child.findViewById(R.id.tv_category);
                    if (t != null) child.setSelected(t.getText().toString().equals(cat));
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
            TextView tv = btn.findViewById(R.id.tv_quality_label);
            if (tv == null) tv = (TextView) btn;  // TextView itself is the root
            tv.setText(q);
            tv.setSelected(q.equals(selectedQuality));
            final TextView finalTv = tv;
            btn.setOnClickListener(v -> {
                selectedQuality = q;
                // Update selected state on all quality buttons
                for (int i = 0; i < qualityContainer.getChildCount(); i++) {
                    View child = qualityContainer.getChildAt(i);
                    TextView t = child.findViewById(R.id.tv_quality_label);
                    if (t == null) t = (TextView) child;
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
            // Category filter
            boolean catOk = "Semua".equals(selectedCategory) ||
                selectedCategory.equals(ch.getCategory());
            // Search filter
            boolean searchOk = query.isEmpty() ||
                ch.getName().toLowerCase().contains(query.toLowerCase());
            // Quality filter
            boolean qualityOk = "Auto".equals(selectedQuality) ||
                matchQuality(ch.getQuality(), selectedQuality);
            if (catOk && searchOk && qualityOk) filtered.add(ch);
        }
        if (adapter != null) adapter.setChannels(filtered);

        int count = filtered.size();
        if (tvChannelCount != null) tvChannelCount.setText(String.valueOf(count));
        if (tvSectionTitle != null) tvSectionTitle.setText(selectedCategory);
        updateTopBarCategory();
    }

    private boolean matchQuality(String channelQuality, String filter) {
        if (channelQuality == null) return false;
        String q = channelQuality.toUpperCase();
        switch (filter) {
            case "4K":    return q.equals("4K");
            case "1080p": return q.equals("FHD") || q.equals("1080P");
            case "720p":  return q.equals("HD")  || q.equals("720P");
            case "360p":  return q.equals("SD")  || q.equals("360P");
            default:      return true;
        }
    }

    private void updateTopBarCategory() {
        if (tvActiveCategory == null) return;
        String icon = CAT_ICONS.containsKey(selectedCategory)
            ? CAT_ICONS.get(selectedCategory) : "TV";
        tvActiveCategory.setText("[" + icon + "] " + selectedCategory.toUpperCase());
    }

    // ── Info card ──────────────────────────────────────────────────────
    private void updateInfoCard() {
        if (tvTotalChannel != null)
            tvTotalChannel.setText(String.valueOf(allChannels.size()));

        // Hitung channel custom (non-default URL)
        int customCount = 0;
        for (Channel ch : allChannels) {
            if (!ch.getUrl().contains("streamlock.net")) customCount++;
        }
        if (tvCustomCount != null) tvCustomCount.setText(String.valueOf(customCount));
    }

    // ── UI helpers ─────────────────────────────────────────────────────
    private void showLoading(boolean show) {
        if (layoutLoading != null) layoutLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        if (rvChannels    != null) rvChannels.setVisibility(show ? View.GONE : View.VISIBLE);
        if (layoutError   != null) layoutError.setVisibility(View.GONE);
    }

    // ── Channel click → PlayerActivity ────────────────────────────────
    @Override
    public void onChannelClick(Channel channel) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_CHANNEL_NAME,  channel.getName());
        intent.putExtra(PlayerActivity.EXTRA_CHANNEL_URL,   channel.getUrl());
        intent.putExtra(PlayerActivity.EXTRA_CHANNEL_LOGO,  channel.getLogo());
        if (channel.getDrmScheme()     != null)
            intent.putExtra(PlayerActivity.EXTRA_DRM_SCHEME,      channel.getDrmScheme());
        if (channel.getDrmLicenseUrl() != null)
            intent.putExtra(PlayerActivity.EXTRA_DRM_LICENSE_URL,  channel.getDrmLicenseUrl());
        if (channel.getDrmKeyId()      != null)
            intent.putExtra(PlayerActivity.EXTRA_DRM_KEY_ID,       channel.getDrmKeyId());
        if (channel.getDrmKey()        != null)
            intent.putExtra(PlayerActivity.EXTRA_DRM_KEY,          channel.getDrmKey());
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

    private int getCount(java.util.Map<String, Integer> map, String key) {
        Integer v = map.get(key);
        return v != null ? v : 0;
    }
}
