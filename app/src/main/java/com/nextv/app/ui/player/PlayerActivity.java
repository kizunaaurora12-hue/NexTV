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

    // ── Views dari custom_control (terintegrasi di PlayerView) ─────────
    private TextView  tvCtrlChannelName;
    private TextView  tvCtrlCategoryName;
    private TextView  tvCtrlChNumber;
    private ImageView ivCtrlLogo;
    private TextView  btnCtrlResolution;  // label resolusi aktif

    // ── Views dari activity_player.xml ────────────────────────────────
    private View       panelChannelList;
    private View       popupResolution;
    private View       popupScreenMode;
    private View       overlayStatus;
    private TextView   tvStatus;
    private ProgressBar progressBuffering;
    private TextView   tvChannelSwitchNotif;
    private RecyclerView rvPanelChannels;
    private EditText   etPanelSearch;

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
        // Fullscreen
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
        playerView = findViewById(R.id.player_view);
        panelChannelList     = findViewById(R.id.panel_channel_list);
        popupResolution      = findViewById(R.id.popup_resolution);
        popupScreenMode      = findViewById(R.id.popup_screen_mode);
        overlayStatus        = findViewById(R.id.overlay_status);
        tvStatus             = findViewById(R.id.tv_status);
        progressBuffering    = findViewById(R.id.progress_buffering);
        tvChannelSwitchNotif = findViewById(R.id.tv_channel_switch_notif);
        rvPanelChannels      = findViewById(R.id.rv_panel_channels);
        etPanelSearch        = findViewById(R.id.et_panel_search);

        // Views DALAM custom_control (terintegrasi di PlayerView)
        // Harus dicari setelah PlayerView di-attach
        // Gunakan handler post agar PlayerView sudah inflate controllernya
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

            // Update info channel awal
            String name = getIntent().getStringExtra(EXTRA_CHANNEL_NAME);
            String logo = getIntent().getStringExtra(EXTRA_CHANNEL_LOGO);
            updateControlInfo(name, null, logo, currentIndex);
        });
    }

    private void setupPopupsAndPanel() {
        // Tutup panel
        View btnClosePanel = findViewById(R.id.btn_close_panel);
        if (btnClosePanel != null) btnClosePanel.setOnClickListener(v -> hidePanel());

        // Search di panel
        if (etPanelSearch != null) {
            etPanelSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                    filterPanelChannels(s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        // Resolusi
        setupResOption(R.id.res_auto,  "AUTO",  -1, -1);
        setupResOption(R.id.res_1080,  "1080p", 1920, 1080);
        setupResOption(R.id.res_720,   "720p",  1280, 720);
        setupResOption(R.id.res_480,   "480p",  854,  480);
        setupResOption(R.id.res_360,   "360p",  640,  360);

        // Ukuran layar
        setupScreenMode(R.id.screen_fit,          AspectRatioFrameLayout.RESIZE_MODE_FIT,          "AUTO");
        setupScreenMode(R.id.screen_fixed_width,  AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,  "AUTO");
        setupScreenMode(R.id.screen_fixed_height, AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT, "AUTO");
        setupScreenMode(R.id.screen_fill,         AspectRatioFrameLayout.RESIZE_MODE_FILL,         "AUTO");
        setupScreenMode(R.id.screen_zoom,         AspectRatioFrameLayout.RESIZE_MODE_ZOOM,         "AUTO");
    }

    private void setupResOption(int viewId, String label, int w, int h) {
        TextView tv = findViewById(viewId);
        if (tv == null) return;
        tv.setOnClickListener(v -> {
            applyResolution(w, h, label);
            hidePopups();
        });
    }

    private void setupScreenMode(int viewId, int mode, String ignored) {
        TextView tv = findViewById(viewId);
        if (tv == null) return;
        tv.setOnClickListener(v -> {
            if (playerView != null) playerView.setResizeMode(mode);
            hidePopups();
            String label = tv.getText().toString().replaceAll("^[^A-Za-z]+\\s*", "");
            Toast.makeText(this, "Layar: " + label, Toast.LENGTH_SHORT).show();
        });
    }

    // ── Load channels + play ───────────────────────────────────────────
    private void loadChannelsAndPlay() {
        String url           = getIntent().getStringExtra(EXTRA_CHANNEL_URL);
        String drmScheme     = getIntent().getStringExtra(EXTRA_DRM_SCHEME);
        String drmLicenseUrl = getIntent().getStringExtra(EXTRA_DRM_LICENSE_URL);
        String drmKeyId      = getIntent().getStringExtra(EXTRA_DRM_KEY_ID);
        String drmKey        = getIntent().getStringExtra(EXTRA_DRM_KEY);

        initPlayer();
        playUrl(url, drmScheme, drmLicenseUrl, drmKeyId, drmKey);

        // Load semua channel di background untuk panel navigasi
        ChannelRepository.getInstance(this).loadChannels(new ChannelRepository.Callback() {
            @Override
            public void onSuccess(List<Channel> channels) {
                allChannels  = channels;
                filteredList = new ArrayList<>(channels);
                setupPanelAdapter();
                // Update nomor & kategori dari data channel
                if (currentIndex < channels.size()) {
                    Channel ch = channels.get(currentIndex);
                    updateControlInfo(ch.getName(), ch.getCategory(), ch.getLogo(), currentIndex);
                }
            }
            @Override public void onError(String msg) {}
        });
    }

    // ── ExoPlayer ─────────────────────────────────────────────────────
    private void initPlayer() {
        if (player != null) { player.release(); player = null; }

        player = new ExoPlayer.Builder(this,
                new DefaultRenderersFactory(this)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER))
            .setMediaSourceFactory(
                new DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(
                        new DefaultHttpDataSource.Factory()
                            .setUserAgent("NexTV/1.0")
                            .setConnectTimeoutMs(20_000)
                            .setReadTimeoutMs(20_000)
                            .setAllowCrossProtocolRedirects(true)))
            .build();

        if (playerView != null) {
            playerView.setPlayer(player);
            // Controller show/hide dihandle oleh PlayerView secara otomatis
            playerView.setControllerShowTimeoutMs(4000); // auto-hide 4 detik
            playerView.setControllerHideOnTouch(true);
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            // Listener untuk sinkronisasi popup dengan visibility controller
            playerView.setControllerVisibilityListener(
                (PlayerView.ControllerVisibilityListener) visibility -> {
                    if (visibility != View.VISIBLE) hidePopups();
                });
        }

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                switch (state) {
                    case Player.STATE_BUFFERING:
                        showStatus("Buffering...", true);
                        break;
                    case Player.STATE_READY:
                        hideStatus();
                        playerInitialized = true;
                        break;
                    case Player.STATE_ENDED:
                        showStatus("Stream berakhir", false);
                        break;
                }
            }
            @Override
            public void onPlayerError(PlaybackException error) {
                Log.e(TAG, "Error " + error.errorCode + ": " + error.getMessage());
                showStatus(getErrorMessage(error), false);
            }
        });
    }

    private void playUrl(String url, String drmScheme, String drmLicenseUrl,
                          String drmKeyId, String drmKey) {
        if (url == null || url.trim().isEmpty()) {
            showStatus("URL tidak valid", false); return;
        }
        playerInitialized = false;
        showStatus("Memuat...", true);
        player.setMediaItem(buildMediaItem(url.trim(), drmScheme, drmLicenseUrl, drmKeyId, drmKey));
        player.prepare();
        player.setPlayWhenReady(true);
    }

    // ── Update info di dalam custom_control ───────────────────────────
    private void updateControlInfo(String name, String category, String logo, int idx) {
        if (tvCtrlChannelName != null && name != null)
            tvCtrlChannelName.setText(name);
        if (tvCtrlCategoryName != null)
            tvCtrlCategoryName.setText(category != null ? category.toUpperCase() : "");
        if (tvCtrlChNumber != null && idx < allChannels.size() && !allChannels.isEmpty()) {
            Channel ch = allChannels.get(idx);
            tvCtrlChNumber.setText("CH " + String.format("%03d", ch.getNumber()));
        }
        if (ivCtrlLogo != null) {
            if (logo != null && !logo.isEmpty()) {
                try {
                    Glide.with(this).load(logo)
                        .placeholder(R.drawable.ic_tv_placeholder)
                        .error(R.drawable.ic_tv_placeholder)
                        .into(ivCtrlLogo);
                } catch (Exception e) {
                    ivCtrlLogo.setImageResource(R.drawable.ic_tv_placeholder);
                }
            } else {
                ivCtrlLogo.setImageResource(R.drawable.ic_tv_placeholder);
            }
        }
    }

    // ── Channel switching ─────────────────────────────────────────────
    private void switchToChannel(Channel ch, int newIndex) {
        currentIndex = newIndex;
        updateControlInfo(ch.getName(), ch.getCategory(), ch.getLogo(), newIndex);
        showSwitchNotif(ch.getName());
        player.stop();
        playUrl(ch.getUrl(), ch.getDrmScheme(), ch.getDrmLicenseUrl(),
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
            player.setTrackSelectionParameters(
                player.getTrackSelectionParameters().buildUpon()
                    .clearOverrides()
                    .setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)
                    .build());
        } else {
            player.setTrackSelectionParameters(
                player.getTrackSelectionParameters().buildUpon()
                    .clearOverrides()
                    .setMaxVideoSize(maxW, maxH)
                    .build());
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
        if (query == null || query.trim().isEmpty()) {
            filteredList.addAll(allChannels);
        } else {
            String q = query.toLowerCase();
            for (Channel ch : allChannels) {
                if (ch.getName().toLowerCase().contains(q) ||
                    ch.getCategory().toLowerCase().contains(q))
                    filteredList.add(ch);
            }
        }
        if (panelAdapter != null) panelAdapter.notifyDataSetChanged();
    }

    private void updatePanelSelection(int idx) {
        if (panelAdapter != null) {
            panelAdapter.setActiveIndex(idx);
            if (rvPanelChannels != null)
                rvPanelChannels.scrollToPosition(Math.min(idx, filteredList.size() - 1));
        }
    }

    private void togglePanel() {
        if (panelChannelList == null) return;
        boolean visible = panelChannelList.getVisibility() == View.VISIBLE;
        panelChannelList.setVisibility(visible ? View.GONE : View.VISIBLE);
        if (!visible) playerView.showController();
        hidePopups();
    }

    private void hidePanel() {
        if (panelChannelList != null) panelChannelList.setVisibility(View.GONE);
    }

    private void togglePopup(View popup) {
        if (popup == null) return;
        boolean showing = popup.getVisibility() == View.VISIBLE;
        hidePopups();
        if (!showing) {
            popup.setVisibility(View.VISIBLE);
            playerView.showController(); // pastikan controller tetap muncul saat popup terbuka
        }
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
        hideSwitchNotif = () -> {
            if (tvChannelSwitchNotif != null) tvChannelSwitchNotif.setVisibility(View.GONE);
        };
        handler.postDelayed(hideSwitchNotif, 2500);
    }

    // ── MediaItem ─────────────────────────────────────────────────────
    private MediaItem buildMediaItem(String url, String drmScheme, String drmLicenseUrl,
                                     String drmKeyId, String drmKey) {
        String lower = url.toLowerCase();
        String mimeType;
        if      (lower.contains(".m3u8") || lower.contains("/hls/")) mimeType = MimeTypes.APPLICATION_M3U8;
        else if (lower.contains(".mpd")  || lower.contains("/dash/")) mimeType = MimeTypes.APPLICATION_MPD;
        else if (lower.startsWith("rtsp://"))                          mimeType = MimeTypes.APPLICATION_RTSP;
        else                                                            mimeType = null;

        boolean hasDrm = drmScheme != null && !drmScheme.trim().isEmpty();
        if (hasDrm && !MimeTypes.APPLICATION_MPD.equals(mimeType)) mimeType = MimeTypes.APPLICATION_MPD;

        MediaItem.Builder b = new MediaItem.Builder().setUri(Uri.parse(url));
        if (mimeType != null) b.setMimeType(mimeType);
        if (hasDrm) {
            MediaItem.DrmConfiguration drm = buildDrm(drmScheme, drmLicenseUrl, drmKeyId, drmKey);
            if (drm != null) b.setDrmConfiguration(drm);
        }
        return b.build();
    }

    private MediaItem.DrmConfiguration buildDrm(String scheme, String licenseUrl,
                                                  String keyIdHex, String keyHex) {
        try {
            UUID uuid = getDrmUuid(scheme);
            MediaItem.DrmConfiguration.Builder b = new MediaItem.DrmConfiguration.Builder(uuid);

            if ("widevine".equalsIgnoreCase(scheme) && licenseUrl != null) {
                // ── Widevine: request ke license server ──────────────────
                b.setLicenseUri(licenseUrl);

            } else if ("clearkey".equalsIgnoreCase(scheme)
                    && keyIdHex != null && keyHex != null) {
                // ── ClearKey: inject JSON license langsung (tanpa network) ─
                // Format JSON ClearKey sesuai W3C EME spec
                byte[] kidBytes = hexToBytes(keyIdHex);
                byte[] keyBytes = hexToBytes(keyHex);

                // Base64url encoding (tanpa padding) sesuai spec
                String kidB64 = Base64.encodeToString(kidBytes,
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
                String keyB64 = Base64.encodeToString(keyBytes,
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);

                // JSON body yang akan dikembalikan sebagai "license"
                String json = "{\"keys\":[{\"kty\":\"oct\",\"kid\":\"" + kidB64
                    + "\",\"k\":\"" + keyB64 + "\"}],\"type\":\"temporary\"}";

                // Encode JSON sebagai data URI — Media3 akan "fetch" ini sebagai license
                String dataUri = "data:text/plain;base64,"
                    + Base64.encodeToString(json.getBytes(Charset.forName("UTF-8")),
                        Base64.NO_WRAP);
                b.setLicenseUri(dataUri);

                Log.d(TAG, "ClearKey JSON: " + json);

            } else if ("clearkey".equalsIgnoreCase(scheme) && licenseUrl != null) {
                // ── ClearKey: license URL langsung (format "keyid:key" atau URL server) ──
                // Jika licenseUrl berisi "keyid:key" maka build inline
                if (licenseUrl.contains(":") && !licenseUrl.startsWith("http")) {
                    String[] parts = licenseUrl.split(":");
                    if (parts.length == 2) {
                        byte[] kidBytes = hexToBytes(parts[0].trim());
                        byte[] keyBytes = hexToBytes(parts[1].trim());
                        String kidB64 = Base64.encodeToString(kidBytes,
                            Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
                        String keyB64 = Base64.encodeToString(keyBytes,
                            Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
                        String json = "{\"keys\":[{\"kty\":\"oct\",\"kid\":\"" + kidB64
                            + "\",\"k\":\"" + keyB64 + "\"}],\"type\":\"temporary\"}";
                        String dataUri = "data:text/plain;base64,"
                            + Base64.encodeToString(json.getBytes(Charset.forName("UTF-8")), Base64.NO_WRAP);
                        b.setLicenseUri(dataUri);
                    }
                } else {
                    b.setLicenseUri(licenseUrl);
                }
            }

            return b.build();
        } catch (Exception e) {
            Log.e(TAG, "DRM config error: " + e.getMessage(), e);
            return null;
        }
    }

    private UUID getDrmUuid(String s) {
        if (s == null) return C.WIDEVINE_UUID;
        switch (s.toLowerCase()) {
            case "clearkey":  return C.CLEARKEY_UUID;
            case "playready": return C.PLAYREADY_UUID;
            default:          return C.WIDEVINE_UUID;
        }
    }

    private byte[] hexToBytes(String hex) {
        hex = hex.replaceAll("\\s+", "").replaceAll("^0x", "");
        if (hex.length() % 2 != 0) hex = "0" + hex;
        byte[] d = new byte[hex.length() / 2];
        for (int i = 0; i < d.length; i++)
            d[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return d;
    }

    private String getErrorMessage(PlaybackException e) {
        switch (e.errorCode) {
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:   return "Tidak ada koneksi";
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:  return "Koneksi timeout";
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS:             return "Server tidak merespons";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:  return "Format tidak didukung";
            case PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED:         return "DRM tidak didukung";
            case PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED: return "Gagal ambil lisensi DRM";
            default: return "Error (" + e.errorCode + ")";
        }
    }

    // ── Key events ────────────────────────────────────────────────────
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
                if (playerView != null) playerView.showController();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ── Status overlay ────────────────────────────────────────────────
    private void showStatus(String msg, boolean spin) {
        if (overlayStatus     != null) overlayStatus.setVisibility(View.VISIBLE);
        if (tvStatus          != null) tvStatus.setText(msg);
        if (progressBuffering != null) progressBuffering.setVisibility(spin ? View.VISIBLE : View.GONE);
    }

    private void hideStatus() {
        if (overlayStatus != null) overlayStatus.setVisibility(View.GONE);
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
    // Inner Adapter: Panel Channel List
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
            row.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, 58));
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setClickable(true); row.setFocusable(true);

            TextView tvN = new TextView(parent.getContext());
            tvN.setTextSize(10); tvN.setTextColor(0xFF7A8AAA); tvN.setMinWidth(44); tvN.setTag("n");
            row.addView(tvN);

            TextView tvT = new TextView(parent.getContext());
            android.widget.LinearLayout.LayoutParams lp =
                new android.widget.LinearLayout.LayoutParams(0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvT.setLayoutParams(lp); tvT.setTextSize(13); tvT.setTextColor(0xFFF0F4FF);
            tvT.setSingleLine(true); tvT.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvT.setTag("t"); row.addView(tvT);

            TextView tvC = new TextView(parent.getContext());
            tvC.setTextSize(10); tvC.setTextColor(0xFF7A8AAA); tvC.setTag("c");
            row.addView(tvC);
            return new VH(row);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            Channel ch = list.get(pos);
            boolean active = pos == activeIndex;
            h.itemView.setBackgroundColor(active ? 0x1AFFD700 : 0x00000000);
            TextView tvN = h.itemView.findViewWithTag("n");
            TextView tvT = h.itemView.findViewWithTag("t");
            TextView tvC = h.itemView.findViewWithTag("c");
            if (tvN != null) tvN.setText(String.format("%03d", ch.getNumber()));
            if (tvT != null) {
                tvT.setText(ch.getName());
                tvT.setTextColor(active ? 0xFFFFD700 : 0xFFF0F4FF);
                tvT.setTypeface(null, active ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            }
            if (tvC != null) tvC.setText(ch.getCategory());
            h.itemView.setOnClickListener(v -> { if (listener != null) listener.onSelect(ch, pos); });
        }

        @Override public int getItemCount() { return list.size(); }
        static class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }
    }
}
