package com.nextv.app.ui.player;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback;
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.nextv.app.R;
import com.nextv.app.data.Channel;
import com.nextv.app.data.ChannelRepository;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerActivity extends AppCompatActivity {

    private static final String TAG = "PlayerActivity";

    public static final String EXTRA_CHANNEL_NAME    = "channel_name";
    public static final String EXTRA_CHANNEL_URL     = "channel_url";
    public static final String EXTRA_CHANNEL_LOGO    = "channel_logo";
    public static final String EXTRA_DRM_SCHEME      = "drm_scheme";
    public static final String EXTRA_DRM_LICENSE_URL = "drm_license_url";
    public static final String EXTRA_DRM_KEY_ID      = "drm_key_id";
    public static final String EXTRA_DRM_KEY         = "drm_key";
    public static final String EXTRA_CHANNEL_INDEX   = "channel_index";

    // ── Player ─────────────────────────────────────────────────────────
    private ExoPlayer  player;
    private PlayerView playerView;

    // ── Views custom_control (dalam PlayerView) ────────────────────────
    private TextView  tvCtrlChannelName;
    private TextView  tvCtrlCategoryName;
    private TextView  tvCtrlChNumber;
    private ImageView ivCtrlLogo;
    private TextView  btnCtrlResolution;

    // ── Views activity_player.xml ──────────────────────────────────────
    private View         panelChannelList;
    private View         popupResolution;
    private View         popupScreenMode;
    private View         overlayStatus;
    private TextView     tvStatus;
    private ProgressBar  progressBuffering;
    private TextView     tvChannelSwitchNotif;
    private RecyclerView rvPanelChannels;
    private EditText     etPanelSearch;

    // ── State ──────────────────────────────────────────────────────────
    private Handler  handler;
    private Runnable hideSwitchNotif;
    private boolean  playerInitialized = false;

    private List<Channel> allChannels  = new ArrayList<>();
    private List<Channel> filteredList = new ArrayList<>();
    private int           currentIndex = 0;

    private PanelChannelAdapter panelAdapter;

    // ── Lifecycle ──────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_player);

        handler = new Handler(Looper.getMainLooper());
        currentIndex = getIntent().getIntExtra(EXTRA_CHANNEL_INDEX, 0);

        bindViews();
        setupPopupsAndPanel();
        loadChannelsAndPlay();
    }

    private void bindViews() {
        playerView           = findViewById(R.id.player_view);
        panelChannelList     = findViewById(R.id.panel_channel_list);
        popupResolution      = findViewById(R.id.popup_resolution);
        popupScreenMode      = findViewById(R.id.popup_screen_mode);
        overlayStatus        = findViewById(R.id.overlay_status);
        tvStatus             = findViewById(R.id.tv_status);
        progressBuffering    = findViewById(R.id.progress_buffering);
        tvChannelSwitchNotif = findViewById(R.id.tv_channel_switch_notif);
        rvPanelChannels      = findViewById(R.id.rv_panel_channels);
        etPanelSearch        = findViewById(R.id.et_panel_search);

        // Views di dalam custom_control — dicari setelah PlayerView selesai inflate
        playerView.post(() -> {
            tvCtrlChannelName  = playerView.findViewById(R.id.tv_ctrl_channel_name);
            tvCtrlCategoryName = playerView.findViewById(R.id.tv_category_name);
            tvCtrlChNumber     = playerView.findViewById(R.id.tv_ctrl_ch_number);
            ivCtrlLogo         = playerView.findViewById(R.id.iv_ctrl_logo);
            btnCtrlResolution  = playerView.findViewById(R.id.btn_ctrl_resolution);

            // Tombol dalam custom_control
            View btnExit = playerView.findViewById(R.id.btn_ctrl_exit);
            if (btnExit != null) btnExit.setOnClickListener(v -> finish());

            View btnPrev = playerView.findViewById(R.id.btn_ctrl_prev);
            if (btnPrev != null) btnPrev.setOnClickListener(v -> switchChannelRelative(-1));

            View btnNext = playerView.findViewById(R.id.btn_ctrl_next);
            if (btnNext != null) btnNext.setOnClickListener(v -> switchChannelRelative(1));

            if (btnCtrlResolution != null)
                btnCtrlResolution.setOnClickListener(v -> togglePopup(popupResolution));

            View btnScreenMode = playerView.findViewById(R.id.btn_ctrl_screen_mode);
            if (btnScreenMode != null)
                btnScreenMode.setOnClickListener(v -> togglePopup(popupScreenMode));

            View btnList = playerView.findViewById(R.id.btn_ctrl_channel_list);
            if (btnList != null) btnList.setOnClickListener(v -> togglePanel());

            // Update info awal
            String name = getIntent().getStringExtra(EXTRA_CHANNEL_NAME);
            String logo = getIntent().getStringExtra(EXTRA_CHANNEL_LOGO);
            updateControlInfo(name, null, logo, currentIndex);
        });
    }

    private void setupPopupsAndPanel() {
        View btnClosePanel = findViewById(R.id.btn_close_panel);
        if (btnClosePanel != null) btnClosePanel.setOnClickListener(v -> hidePanel());

        if (etPanelSearch != null) {
            etPanelSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) { filterPanelChannels(s.toString()); }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        setupResOption(R.id.res_auto,  "AUTO",  -1,   -1);
        setupResOption(R.id.res_1080,  "1080p", 1920, 1080);
        setupResOption(R.id.res_720,   "720p",  1280, 720);
        setupResOption(R.id.res_480,   "480p",  854,  480);
        setupResOption(R.id.res_360,   "360p",  640,  360);

        setupScreenMode(R.id.screen_fit,          AspectRatioFrameLayout.RESIZE_MODE_FIT);
        setupScreenMode(R.id.screen_fixed_width,  AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH);
        setupScreenMode(R.id.screen_fixed_height, AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT);
        setupScreenMode(R.id.screen_fill,         AspectRatioFrameLayout.RESIZE_MODE_FILL);
        setupScreenMode(R.id.screen_zoom,         AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
    }

    private void setupResOption(int id, String label, int w, int h) {
        TextView tv = findViewById(id);
        if (tv == null) return;
        tv.setOnClickListener(v -> { applyResolution(w, h, label); hidePopups(); });
    }

    private void setupScreenMode(int id, int mode) {
        TextView tv = findViewById(id);
        if (tv == null) return;
        tv.setOnClickListener(v -> {
            if (playerView != null) playerView.setResizeMode(mode);
            hidePopups();
            String lbl = tv.getText().toString().replaceAll("^[^A-Za-z]+\\s*","");
            Toast.makeText(this, "Layar: " + lbl, Toast.LENGTH_SHORT).show();
        });
    }

    // ── Load channels + play ───────────────────────────────────────────
    private void loadChannelsAndPlay() {
        String url           = getIntent().getStringExtra(EXTRA_CHANNEL_URL);
        String drmScheme     = getIntent().getStringExtra(EXTRA_DRM_SCHEME);
        String drmLicenseUrl = getIntent().getStringExtra(EXTRA_DRM_LICENSE_URL);
        String drmKeyId      = getIntent().getStringExtra(EXTRA_DRM_KEY_ID);
        String drmKey        = getIntent().getStringExtra(EXTRA_DRM_KEY);

        playChannel(url, drmScheme, drmLicenseUrl, drmKeyId, drmKey);

        ChannelRepository.getInstance(this).loadChannels(new ChannelRepository.Callback() {
            @Override
            public void onSuccess(List<Channel> channels) {
                allChannels  = channels;
                filteredList = new ArrayList<>(channels);
                setupPanelAdapter();
                if (currentIndex < channels.size()) {
                    Channel ch = channels.get(currentIndex);
                    updateControlInfo(ch.getName(), ch.getCategory(), ch.getLogo(), currentIndex);
                }
            }
            @Override public void onError(String msg) {}
        });
    }

    // ── ExoPlayer — dibuat ulang setiap ganti channel ─────────────────
    private void playChannel(String url, String drmScheme, String drmLicenseUrl,
                              String drmKeyId, String drmKey) {
        if (url == null || url.trim().isEmpty()) {
            showStatus("URL tidak valid", false); return;
        }
        showStatus("Memuat stream...", true);
        playerInitialized = false;

        // Release player lama
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }

        // ── HTTP factory ───────────────────────────────────────────────
        DefaultHttpDataSource.Factory httpFactory =
            new DefaultHttpDataSource.Factory()
                .setUserAgent("NexTV/1.0")
                .setConnectTimeoutMs(20_000)
                .setReadTimeoutMs(20_000)
                .setAllowCrossProtocolRedirects(true);

        // ── DRM Session Manager ────────────────────────────────────────
        DefaultDrmSessionManager drmSessionManager = buildDrmSessionManager(
            drmScheme, drmLicenseUrl, drmKeyId, drmKey, httpFactory);

        // ── Media source factory ───────────────────────────────────────
        final DefaultDrmSessionManager finalDrm = drmSessionManager;
        DefaultMediaSourceFactory mediaSourceFactory =
            new DefaultMediaSourceFactory(this)
                .setDataSourceFactory(httpFactory)
                .setDrmSessionManagerProvider(mediaItem -> finalDrm);

        // ── Player ─────────────────────────────────────────────────────
        player = new ExoPlayer.Builder(this,
                new DefaultRenderersFactory(this)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER))
            .setMediaSourceFactory(mediaSourceFactory)
            .build();

        if (playerView != null) {
            playerView.setPlayer(player);
            playerView.setControllerShowTimeoutMs(4000);
            playerView.setControllerHideOnTouch(true);
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            playerView.setControllerVisibilityListener(
                (PlayerView.ControllerVisibilityListener) visibility -> {
                    if (visibility != View.VISIBLE) hidePopups();
                });
        }

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                switch (state) {
                    case Player.STATE_BUFFERING: showStatus("Buffering...", true); break;
                    case Player.STATE_READY:     hideStatus(); playerInitialized = true; break;
                    case Player.STATE_ENDED:     showStatus("Stream berakhir", false); break;
                }
            }
            @Override
            public void onPlayerError(PlaybackException error) {
                Log.e(TAG, "Error " + error.errorCode + ": " + error.getMessage(), error);
                showStatus(getErrorMessage(error), false);
            }
        });

        // ── MediaItem (tidak perlu set DRM config karena sudah lewat DrmSessionManager) ──
        String cleanUrl = url.trim();
        String lower    = cleanUrl.toLowerCase();
        String mimeType;
        if      (lower.contains(".m3u8") || lower.contains("/hls/")) mimeType = MimeTypes.APPLICATION_M3U8;
        else if (lower.contains(".mpd")  || lower.contains("/dash/")) mimeType = MimeTypes.APPLICATION_MPD;
        else if (lower.startsWith("rtsp://"))                          mimeType = MimeTypes.APPLICATION_RTSP;
        else                                                            mimeType = null;

        boolean hasDrm = drmScheme != null && !drmScheme.trim().isEmpty();
        // DRM butuh DASH
        if (hasDrm && !MimeTypes.APPLICATION_MPD.equals(mimeType)) mimeType = MimeTypes.APPLICATION_MPD;

        MediaItem.Builder itemBuilder = new MediaItem.Builder().setUri(Uri.parse(cleanUrl));
        if (mimeType != null) itemBuilder.setMimeType(mimeType);

        player.setMediaItem(itemBuilder.build());
        player.prepare();
        player.setPlayWhenReady(true);
    }

    /**
     * Bangun DrmSessionManager yang tepat:
     *
     * ClearKey → LocalMediaDrmCallback (inject JSON langsung ke memory, TANPA network)
     *   Format JSON: {"keys":[{"kty":"oct","kid":"<base64url>","k":"<base64url>"}],"type":"temporary"}
     *
     * Widevine → HttpMediaDrmCallback (request ke license server)
     *
     * Tidak ada DRM → DRM_UNSUPPORTED (pakai ini agar tidak crash)
     */
    private DefaultDrmSessionManager buildDrmSessionManager(
            String drmScheme, String drmLicenseUrl, String drmKeyId, String drmKey,
            DefaultHttpDataSource.Factory httpFactory) {

        boolean hasClearKey = "clearkey".equalsIgnoreCase(drmScheme);
        boolean hasWidevine  = "widevine".equalsIgnoreCase(drmScheme);

        try {
            if (hasClearKey && drmKeyId != null && drmKey != null) {
                // ── ClearKey: build JSON dan inject langsung ───────────
                byte[] kidBytes = hexToBytes(drmKeyId);
                byte[] keyBytes = hexToBytes(drmKey);
                String kidB64 = Base64.encodeToString(kidBytes,
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
                String keyB64 = Base64.encodeToString(keyBytes,
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);

                // JSON sesuai W3C ClearKey spec
                String json = "{\"keys\":[{\"kty\":\"oct\",\"kid\":\""
                    + kidB64 + "\",\"k\":\"" + keyB64
                    + "\"}],\"type\":\"temporary\"}";

                byte[] jsonBytes = json.getBytes(Charset.forName("UTF-8"));
                Log.d(TAG, "ClearKey JSON: " + json);

                // LocalMediaDrmCallback: tidak perlu network request sama sekali
                LocalMediaDrmCallback drmCallback = new LocalMediaDrmCallback(jsonBytes);

                return new DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(
                        C.CLEARKEY_UUID,
                        FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .setMultiSession(false)
                    .build(drmCallback);

            } else if (hasClearKey && drmLicenseUrl != null && drmLicenseUrl.contains(":")) {
                // ── ClearKey format "keyid:key" di licUrl ─────────────
                String[] parts = drmLicenseUrl.trim().split(":");
                if (parts.length == 2) {
                    byte[] kidBytes = hexToBytes(parts[0].trim());
                    byte[] keyBytes = hexToBytes(parts[1].trim());
                    String kidB64 = Base64.encodeToString(kidBytes,
                        Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
                    String keyB64 = Base64.encodeToString(keyBytes,
                        Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
                    String json = "{\"keys\":[{\"kty\":\"oct\",\"kid\":\""
                        + kidB64 + "\",\"k\":\"" + keyB64
                        + "\"}],\"type\":\"temporary\"}";
                    byte[] jsonBytes = json.getBytes(Charset.forName("UTF-8"));

                    LocalMediaDrmCallback drmCallback = new LocalMediaDrmCallback(jsonBytes);
                    return new DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID,
                            FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .setMultiSession(false)
                        .build(drmCallback);
                }

            } else if (hasWidevine && drmLicenseUrl != null) {
                // ── Widevine: request ke license server ───────────────
                HttpMediaDrmCallback drmCallback =
                    new HttpMediaDrmCallback(drmLicenseUrl, httpFactory);

                return new DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID,
                        FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .setMultiSession(true)
                    .build(drmCallback);
            }

        } catch (Exception e) {
            Log.e(TAG, "DRM build error: " + e.getMessage(), e);
        }

        return DefaultDrmSessionManager.DRM_UNSUPPORTED;
    }

    // ── Hex utility ───────────────────────────────────────────────────
    private byte[] hexToBytes(String hex) {
        hex = hex.replaceAll("\\s+", "").replaceAll("^0[xX]", "");
        if (hex.length() % 2 != 0) hex = "0" + hex;
        byte[] d = new byte[hex.length() / 2];
        for (int i = 0; i < d.length; i++)
            d[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return d;
    }

    // ── Update info di custom_control ─────────────────────────────────
    private void updateControlInfo(String name, String category, String logo, int idx) {
        if (tvCtrlChannelName  != null && name != null) tvCtrlChannelName.setText(name);
        if (tvCtrlCategoryName != null) tvCtrlCategoryName.setText(category != null ? category.toUpperCase() : "");
        if (tvCtrlChNumber != null && !allChannels.isEmpty() && idx < allChannels.size())
            tvCtrlChNumber.setText("CH " + String.format("%03d", allChannels.get(idx).getNumber()));
        if (ivCtrlLogo != null) {
            if (logo != null && !logo.isEmpty()) {
                try { Glide.with(this).load(logo).placeholder(R.drawable.ic_tv_placeholder).error(R.drawable.ic_tv_placeholder).into(ivCtrlLogo); }
                catch (Exception e) { ivCtrlLogo.setImageResource(R.drawable.ic_tv_placeholder); }
            } else { ivCtrlLogo.setImageResource(R.drawable.ic_tv_placeholder); }
        }
    }

    // ── Channel switching ─────────────────────────────────────────────
    private void switchToChannel(Channel ch, int newIndex) {
        currentIndex = newIndex;
        updateControlInfo(ch.getName(), ch.getCategory(), ch.getLogo(), newIndex);
        showSwitchNotif(ch.getName());
        playChannel(ch.getUrl(), ch.getDrmScheme(), ch.getDrmLicenseUrl(),
                    ch.getDrmKeyId(), ch.getDrmKey());
        updatePanelSelection(newIndex);
        hidePanel();
        hidePopups();
    }

    private void switchChannelRelative(int delta) {
        if (allChannels.isEmpty()) return;
        int newIdx = currentIndex + delta;
        if (newIdx < 0) newIdx = allChannels.size() - 1;
        if (newIdx >= allChannels.size()) newIdx = 0;
        switchToChannel(allChannels.get(newIdx), newIdx);
    }

    // ── Resolusi ──────────────────────────────────────────────────────
    private void applyResolution(int maxW, int maxH, String label) {
        if (btnCtrlResolution != null) btnCtrlResolution.setText(label);
        if (player == null) return;
        if (maxW < 0) {
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .clearOverrides().setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE).build());
        } else {
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .clearOverrides().setMaxVideoSize(maxW, maxH).build());
        }
        Toast.makeText(this, "Resolusi: " + label, Toast.LENGTH_SHORT).show();
    }

    // ── Panel ─────────────────────────────────────────────────────────
    private void setupPanelAdapter() {
        panelAdapter = new PanelChannelAdapter(filteredList, currentIndex, (ch, idx) -> {
            int realIdx = allChannels.indexOf(ch);
            switchToChannel(ch, realIdx >= 0 ? realIdx : idx);
        });
        if (rvPanelChannels != null) {
            rvPanelChannels.setLayoutManager(new LinearLayoutManager(this));
            rvPanelChannels.setAdapter(panelAdapter);
        }
    }

    private void filterPanelChannels(String query) {
        filteredList.clear();
        if (query == null || query.trim().isEmpty()) filteredList.addAll(allChannels);
        else { String q = query.toLowerCase();
            for (Channel ch : allChannels)
                if (ch.getName().toLowerCase().contains(q) || ch.getCategory().toLowerCase().contains(q))
                    filteredList.add(ch); }
        if (panelAdapter != null) panelAdapter.notifyDataSetChanged();
    }

    private void updatePanelSelection(int idx) {
        if (panelAdapter != null) {
            panelAdapter.setActiveIndex(idx);
            if (rvPanelChannels != null)
                rvPanelChannels.scrollToPosition(Math.min(idx, filteredList.size()-1));
        }
    }

    private void togglePanel() {
        if (panelChannelList == null) return;
        boolean vis = panelChannelList.getVisibility() == View.VISIBLE;
        panelChannelList.setVisibility(vis ? View.GONE : View.VISIBLE);
        if (!vis) playerView.showController();
        hidePopups();
    }

    private void hidePanel() { if (panelChannelList != null) panelChannelList.setVisibility(View.GONE); }

    private void togglePopup(View popup) {
        if (popup == null) return;
        boolean showing = popup.getVisibility() == View.VISIBLE;
        hidePopups();
        if (!showing) { popup.setVisibility(View.VISIBLE); playerView.showController(); }
    }

    private void hidePopups() {
        if (popupResolution != null) popupResolution.setVisibility(View.GONE);
        if (popupScreenMode != null) popupScreenMode.setVisibility(View.GONE);
    }

    private void showSwitchNotif(String name) {
        if (tvChannelSwitchNotif == null) return;
        tvChannelSwitchNotif.setText("▶  " + name);
        tvChannelSwitchNotif.setVisibility(View.VISIBLE);
        if (hideSwitchNotif != null) handler.removeCallbacks(hideSwitchNotif);
        hideSwitchNotif = () -> { if (tvChannelSwitchNotif != null) tvChannelSwitchNotif.setVisibility(View.GONE); };
        handler.postDelayed(hideSwitchNotif, 2500);
    }

    // ── Error messages ─────────────────────────────────────────────────
    private String getErrorMessage(PlaybackException e) {
        switch (e.errorCode) {
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:   return "Tidak ada koneksi";
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:  return "Koneksi timeout";
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS:             return "Server tidak merespons";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:  return "Format tidak didukung";
            case PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED:         return "DRM tidak didukung perangkat";
            case PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED:        return "Gagal provisioning DRM";
            case PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR:              return "Konten DRM error — cek key";
            case PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED: return "Gagal ambil lisensi DRM";
            default: return "Error memutar channel (kode: " + e.errorCode + ")";
        }
    }

    // ── Status overlay ─────────────────────────────────────────────────
    private void showStatus(String msg, boolean spin) {
        if (overlayStatus != null) overlayStatus.setVisibility(View.VISIBLE);
        if (tvStatus != null) tvStatus.setText(msg);
        if (progressBuffering != null) progressBuffering.setVisibility(spin ? View.VISIBLE : View.GONE);
    }
    private void hideStatus() { if (overlayStatus != null) overlayStatus.setVisibility(View.GONE); }

    // ── Key events ─────────────────────────────────────────────────────
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (panelChannelList != null && panelChannelList.getVisibility() == View.VISIBLE) { hidePanel(); return true; }
                if (popupResolution  != null && popupResolution.getVisibility()  == View.VISIBLE) { hidePopups(); return true; }
                if (popupScreenMode  != null && popupScreenMode.getVisibility()  == View.VISIBLE) { hidePopups(); return true; }
                finish(); return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                switchChannelRelative(-1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                switchChannelRelative(1); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (playerView != null) playerView.showController(); return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onPause()  { super.onPause();  if (player != null) player.pause(); }
    @Override protected void onResume() { super.onResume(); if (player != null && playerInitialized) player.play(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && hideSwitchNotif != null) handler.removeCallbacks(hideSwitchNotif);
        if (player != null) { player.release(); player = null; }
        if (playerView != null) playerView.setPlayer(null);
    }

    // ══════════════════════════════════════════════════════════════════
    // Inner class: Panel Channel List Adapter
    // ══════════════════════════════════════════════════════════════════
    static class PanelChannelAdapter extends RecyclerView.Adapter<PanelChannelAdapter.VH> {
        interface OnSelect { void onSelect(Channel ch, int idx); }
        private final List<Channel> list;
        private int activeIndex;
        private final OnSelect listener;

        PanelChannelAdapter(List<Channel> list, int activeIndex, OnSelect l) {
            this.list = list; this.activeIndex = activeIndex; this.listener = l;
        }
        void setActiveIndex(int idx) {
            int old = activeIndex; activeIndex = idx;
            notifyItemChanged(old); notifyItemChanged(idx);
        }
        @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int vt) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(parent.getContext());
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setPadding(16, 0, 16, 0);
            row.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, 58));
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setClickable(true); row.setFocusable(true);
            TextView tvN = new TextView(parent.getContext()); tvN.setTextSize(10); tvN.setTextColor(0xFF7A8AAA); tvN.setMinWidth(44); tvN.setTag("n"); row.addView(tvN);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            TextView tvT = new TextView(parent.getContext()); tvT.setLayoutParams(lp); tvT.setTextSize(13); tvT.setTextColor(0xFFF0F4FF); tvT.setSingleLine(true); tvT.setEllipsize(android.text.TextUtils.TruncateAt.END); tvT.setTag("t"); row.addView(tvT);
            TextView tvC = new TextView(parent.getContext()); tvC.setTextSize(10); tvC.setTextColor(0xFF7A8AAA); tvC.setTag("c"); row.addView(tvC);
            return new VH(row);
        }
        @Override
        public void onBindViewHolder(VH h, int pos) {
            Channel ch = list.get(pos); boolean active = pos == activeIndex;
            h.itemView.setBackgroundColor(active ? 0x1AFFD700 : 0x00000000);
            TextView tvN = h.itemView.findViewWithTag("n"); TextView tvT = h.itemView.findViewWithTag("t"); TextView tvC = h.itemView.findViewWithTag("c");
            if (tvN != null) tvN.setText(String.format("%03d", ch.getNumber()));
            if (tvT != null) { tvT.setText(ch.getName()); tvT.setTextColor(active ? 0xFFFFD700 : 0xFFF0F4FF); tvT.setTypeface(null, active ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL); }
            if (tvC != null) tvC.setText(ch.getCategory());
            h.itemView.setOnClickListener(v -> { if (listener != null) listener.onSelect(ch, pos); });
        }
        @Override public int getItemCount() { return list.size(); }
        static class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }
    }
}
