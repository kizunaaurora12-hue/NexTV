package com.nextv.app.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements ChannelAdapter.OnChannelClickListener {

    private RecyclerView rvChannels;
    private ChannelAdapter adapter;
    private ProgressBar progressBar;
    private View tvError;           // LinearLayout container
    private TextView tvErrorMsg;    // actual error text
    private TextView tvChannelCount;
    private LinearLayout categoryContainer;
    private EditText etSearch;

    private List<Channel> allChannels = new ArrayList<>();
    private String selectedCategory = "Semua";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        loadChannels();
    }

    private void initViews() {
        rvChannels = findViewById(R.id.rv_channels);
        progressBar = findViewById(R.id.progress_bar);
        tvError    = findViewById(R.id.tv_error);
        tvErrorMsg = findViewById(R.id.tv_error_msg);
        tvChannelCount = findViewById(R.id.tv_channel_count);
        categoryContainer = findViewById(R.id.category_container);
        etSearch = findViewById(R.id.et_search);

        // Setup RecyclerView - 4 kolom untuk TV Box landscape
        int spanCount = getResources().getConfiguration().screenWidthDp > 600 ? 5 : 4;
        rvChannels.setLayoutManager(new GridLayoutManager(this, spanCount));
        adapter = new ChannelAdapter(this, this);
        rvChannels.setAdapter(adapter);

        // Search
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterChannels(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Settings button
        View btnSettings = findViewById(R.id.btn_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        }

        // Refresh button
        View btnRefresh = findViewById(R.id.btn_refresh);
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                ChannelRepository.getInstance(this).clearCache();
                loadChannels();
            });
        }
    }

    private void loadChannels() {
        showLoading(true);
        ChannelRepository.getInstance(this).loadChannels(new ChannelRepository.Callback() {
            @Override
            public void onSuccess(List<Channel> channels) {
                allChannels = channels;
                showLoading(false);
                buildCategories();
                updateChannelList();
                tvChannelCount.setText(channels.size() + " Channel");
            }

            @Override
            public void onError(String message) {
                showLoading(false);
                tvError.setVisibility(View.VISIBLE);
                if (tvErrorMsg != null) tvErrorMsg.setText("❌ " + message);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void buildCategories() {
        Set<String> cats = new LinkedHashSet<>();
        cats.add("Semua");
        for (Channel ch : allChannels) {
            if (ch.getCategory() != null && !ch.getCategory().isEmpty()) {
                cats.add(ch.getCategory());
            }
        }

        categoryContainer.removeAllViews();
        for (String cat : cats) {
            View chip = getLayoutInflater().inflate(R.layout.item_category_chip, categoryContainer, false);
            TextView tv = chip.findViewById(R.id.tv_category);
            tv.setText(cat);
            chip.setSelected(cat.equals(selectedCategory));
            chip.setOnClickListener(v -> {
                selectedCategory = cat;
                // Update semua chip state
                for (int i = 0; i < categoryContainer.getChildCount(); i++) {
                    View c = categoryContainer.getChildAt(i);
                    TextView t = c.findViewById(R.id.tv_category);
                    c.setSelected(t.getText().toString().equals(cat));
                }
                filterChannels(etSearch.getText().toString());
            });
            categoryContainer.addView(chip);
        }
    }

    private void filterChannels(String query) {
        List<Channel> filtered = new ArrayList<>();
        for (Channel ch : allChannels) {
            boolean catMatch = selectedCategory.equals("Semua") ||
                ch.getCategory().equals(selectedCategory);
            boolean searchMatch = query.isEmpty() ||
                ch.getName().toLowerCase().contains(query.toLowerCase());
            if (catMatch && searchMatch) {
                filtered.add(ch);
            }
        }
        adapter.setChannels(filtered);
        tvChannelCount.setText(filtered.size() + " Channel");
    }

    private void updateChannelList() {
        filterChannels(etSearch.getText().toString());
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        rvChannels.setVisibility(show ? View.GONE : View.VISIBLE);
        tvError.setVisibility(View.GONE);
    }

    @Override
    public void onChannelClick(Channel channel) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_CHANNEL_NAME, channel.getName());
        intent.putExtra(PlayerActivity.EXTRA_CHANNEL_URL, channel.getUrl());
        intent.putExtra(PlayerActivity.EXTRA_CHANNEL_LOGO, channel.getLogo());
        startActivity(intent);
    }

    // Handle remote control D-Pad
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
