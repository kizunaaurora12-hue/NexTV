package com.nextv.app.ui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.nextv.app.R;
import com.nextv.app.data.ChannelRepository;

public class SettingsActivity extends AppCompatActivity {

    public static final String PREFS_SETTINGS   = "nextv_settings";
    public static final String KEY_CHANNELS_URL = "channels_url";
    public static final String DEFAULT_URL =
        "https://raw.githubusercontent.com/aurorasekai15-hub/SymphogearTV/main/channels.json";

    private EditText etSourceUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        etSourceUrl = findViewById(R.id.et_source_url);

        SharedPreferences prefs = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);
        String savedUrl = prefs.getString(KEY_CHANNELS_URL, DEFAULT_URL);
        etSourceUrl.setText(savedUrl);

        TextView tvVersion = findViewById(R.id.tv_version);
        if (tvVersion != null) tvVersion.setText("NexTV v1.0.0");

        View btnSave = findViewById(R.id.btn_save_url);
        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                String url = etSourceUrl.getText().toString().trim();
                if (url.isEmpty()) {
                    Toast.makeText(this, "URL tidak boleh kosong", Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.edit().putString(KEY_CHANNELS_URL, url).apply();
                ChannelRepository.getInstance(this).clearCache();
                Toast.makeText(this, "URL disimpan, channel akan di-refresh", Toast.LENGTH_SHORT).show();
            });
        }

        View btnClear = findViewById(R.id.btn_clear_cache);
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                ChannelRepository.getInstance(this).clearCache();
                Toast.makeText(this, "Cache dihapus", Toast.LENGTH_SHORT).show();
            });
        }

        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }
}
