package com.nextv.app.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
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

    // Views
    private RecyclerView   rvChannels;
    private RecyclerView   rvSidebar;
    private ChannelAdapter adapter;
    private SidebarAdapter sidebarAdapter;
    private View           layoutLoading;
    private View           layoutError;
    private TextView       tvErrorMsg;
    private TextView       tvChannelCount;
    private TextView       tvTotalChannel;
    private TextView       tvCustomCount;
    private TextView       tvSectionTitle;
    private TextView       tvActiveCategory;
    private EditText       etSearch;
    // Quality buttons
    private TextView       btnQ360, btnQ720, btnQ1080, btnQ4k, btnQAuto;

    // State
    private List<Channel>        allChannels     = new ArrayList<>();
    private String               selectedCategory = "Semua";
    private String               selectedQuality  = "Auto";
    private Map<String, Integer> categoryCounts  = new LinkedHashMap<>();

    private static final List<String> PRIORITY_CATS = Arrays.asList(
        "Semua", "Nasional", "Berita", "Vision+", "IndiHome",
        "Internasional", "Olahraga", "Film", "Anak-anak", "Musik", "Dokumenter"
    );

    private static final String[][] QUALITY_BTNS = {
        {"360p","btn360"}, {"720p","btn720"}, {"1080p","btn1080"}, {"4K","btn4k"}, {"Auto","btnAuto"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        loadChannels();
    }

    private void initViews() {
        rvChannels       = findViewById(R.id.rv_channels);
        rvSidebar        = findViewById(R.id.rv_sidebar);
        layoutLoading    = findViewById(R.id.progress_bar);
        layoutError      = findViewById(R.id.tv_error);
        tvErrorMsg       = findViewById(R.id.tv_error_msg);
        tvChannelCount   = findViewById(R.id.tv_channel_count);
        tvTotalChannel   = findViewById(R.id.tv_total_channel);
        tvCustomCount    = findViewById(R.id.tv_custom_count);
        tvSectionTitle   = findViewById(R.id.tv_section_title);
        tvActiveCategory = findViewById(R.id.tv_active_category);
        etSearch         = findViewById(R.id.et_search);

        // Quality filter buttons
        btnQ360  = findViewById(R.id.btn_q360);
        btnQ720  = findViewById(R.id.btn_q720);
        btnQ1080 = findViewById(R.id.btn_q1080);
        btnQ4k   = findViewById(R.id.btn_q4k);
        btnQAuto = findViewById(R.id.btn_qauto);
        setupQualityButtons();

        // Channel grid
        int span = getResources().getConfiguration().screenWidthDp > 700 ? 5 : 4;
        rvChannels.setLayoutManager(new GridLayoutManager(this, span));
        adapter = new ChannelAdapter(this, this);
        rvChannels.setAdapter(adapter);

        // Sidebar RecyclerView
        sidebarAdapter = new SidebarAdapter((name, pos) -> {
            selectedCategory = name;
            updateTopBar(name);
            applyFilters();
        });
        rvSidebar.setLayoutManager(new LinearLayoutManager(this));
        rvSidebar.setAdapter(sidebarAdapter);

        // Search
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) { applyFilters(); }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        // Buttons
        View btnSettings = findViewById(R.id.btn_settings);
        if (btnSettings != null)
            btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

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
        if (btnRetry != null) btnRetry.setOnClickListener(v -> loadChannels());
    }

    private void setupQualityButtons() {
        TextView[] btns = {btnQ360, btnQ720, btnQ1080, btnQ4k, btnQAuto};
        String[]   lbls = {"360p", "720p", "1080p", "4K", "Auto"};
        for (int i = 0; i < btns.length; i++) {
            if (btns[i] == null) continue;
            final String lbl = lbls[i];
            btns[i].setSelected("Auto".equals(lbl));
            btns[i].setOnClickListener(v -> {
                selectedQuality = lbl;
                for (TextView b : btns) if (b != null) b.setSelected(false);
                ((TextView) v).setSelected(true);
                applyFilters();
            });
        }
    }

    // Load
    private void loadChannels() {
        showLoading(true);
        ChannelRepository.getInstance(this).loadChannels(new ChannelRepository.Callback() {
            @Override
            public void onSuccess(List<Channel> channels) {
                allChannels = channels;
                showLoading(false);
                buildSidebar();
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

    // Build sidebar dari channels
    private void buildSidebar() {
        categoryCounts.clear();
        categoryCounts.put("Semua", allChannels.size());
        for (Channel ch : allChannels) {
            String cat = ch.getCategory();
            if (cat == null || cat.trim().isEmpty()) cat = "Umum";
            Integer v = categoryCounts.get(cat);
            categoryCounts.put(cat, v != null ? v + 1 : 1);
        }

        // Urutan prioritas + sisanya
        List<String> ordered = new ArrayList<>();
        for (String c : PRIORITY_CATS) { if (categoryCounts.containsKey(c)) ordered.add(c); }
        for (String c : categoryCounts.keySet()) { if (!ordered.contains(c)) ordered.add(c); }

        // Buat data untuk SidebarAdapter
        List<String[]> sidebarData = new ArrayList<>();
        for (String cat : ordered) {
            Integer cnt = categoryCounts.get(cat);
            sidebarData.add(new String[]{cat, String.valueOf(cnt != null ? cnt : 0)});
        }
        sidebarAdapter.setItems(sidebarData);

        // Set selected ke posisi "Semua"
        int selPos = ordered.indexOf(selectedCategory);
        if (selPos >= 0) sidebarAdapter.selectPosition(selPos);
    }

    // Filter
    private void applyFilters() {
        String query = etSearch != null ? etSearch.getText().toString().trim() : "";
        List<Channel> filtered = new ArrayList<>();
        for (Channel ch : allChannels) {
            String cat = ch.getCategory();
            if (cat == null || cat.trim().isEmpty()) cat = "Umum";
            boolean catOk    = "Semua".equals(selectedCategory) || selectedCategory.equals(cat);
            boolean searchOk = query.isEmpty() || ch.getName().toLowerCase().contains(query.toLowerCase());
            boolean qualOk   = matchQuality(ch.getQuality(), selectedQuality);
            if (catOk && searchOk && qualOk) filtered.add(ch);
        }
        if (adapter != null) adapter.setChannels(filtered);
        int cnt = filtered.size();
        if (tvChannelCount != null) tvChannelCount.setText(String.valueOf(cnt));
        if (tvSectionTitle != null) tvSectionTitle.setText(selectedCategory);
        updateTopBar(selectedCategory);
    }

    private boolean matchQuality(String q, String filter) {
        if (q == null || "Auto".equals(filter)) return true;
        switch (filter) {
            case "4K":    return q.equalsIgnoreCase("4K");
            case "1080p": return q.equalsIgnoreCase("FHD") || q.equalsIgnoreCase("1080p");
            case "720p":  return q.equalsIgnoreCase("HD")  || q.equalsIgnoreCase("720p");
            case "360p":  return q.equalsIgnoreCase("SD")  || q.equalsIgnoreCase("360p");
            default:      return true;
        }
    }

    private void updateTopBar(String cat) {
        if (tvActiveCategory == null) return;
        String key = cat.toLowerCase().trim();
        String ico;
        if      (key.contains("semua"))        ico = "ALL";
        else if (key.contains("nasional"))     ico = "TV";
        else if (key.contains("berita"))       ico = "NWS";
        else if (key.contains("vision"))       ico = "VIS";
        else if (key.contains("indihome"))     ico = "IND";
        else if (key.contains("internasional"))ico = "INT";
        else if (key.contains("olahraga"))     ico = "SPT";
        else if (key.contains("film"))         ico = "MOV";
        else if (key.contains("anak"))         ico = "KID";
        else if (key.contains("musik"))        ico = "MUS";
        else ico = cat.length() >= 3 ? cat.substring(0, 3).toUpperCase() : cat.toUpperCase();
        tvActiveCategory.setText("[" + ico + "] " + cat.toUpperCase());
    }

    private void updateInfoCard() {
        if (tvTotalChannel != null) tvTotalChannel.setText(String.valueOf(allChannels.size()));
        int drmCount = 0;
        for (Channel ch : allChannels) if (ch.hasDrm()) drmCount++;
        if (tvCustomCount != null) tvCustomCount.setText(String.valueOf(drmCount));
    }

    private void showLoading(boolean show) {
        if (layoutLoading != null) layoutLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        if (rvChannels    != null) rvChannels.setVisibility(show ? View.GONE : View.VISIBLE);
        if (layoutError   != null) layoutError.setVisibility(View.GONE);
    }

    @Override
    public void onChannelClick(Channel channel) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_CHANNEL_NAME,  channel.getName());
        intent.putExtra(PlayerActivity.EXTRA_CHANNEL_URL,   channel.getUrl());
        intent.putExtra(PlayerActivity.EXTRA_CHANNEL_LOGO,  channel.getLogo());
        int idx = allChannels.indexOf(channel);
        intent.putExtra(PlayerActivity.EXTRA_CHANNEL_INDEX, idx >= 0 ? idx : 0);
        if (channel.getDrmScheme()     != null) intent.putExtra(PlayerActivity.EXTRA_DRM_SCHEME,     channel.getDrmScheme());
        if (channel.getDrmLicenseUrl() != null) intent.putExtra(PlayerActivity.EXTRA_DRM_LICENSE_URL, channel.getDrmLicenseUrl());
        if (channel.getDrmKeyId()      != null) intent.putExtra(PlayerActivity.EXTRA_DRM_KEY_ID,      channel.getDrmKeyId());
        if (channel.getDrmKey()        != null) intent.putExtra(PlayerActivity.EXTRA_DRM_KEY,         channel.getDrmKey());
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
