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

    // ── Views ──────────────────────────────────────────────────────────
    private ExoPlayer    player;
    private PlayerView   playerView;
    private View         overlayInfo;
    private View         overlayStatus;
    private View         overlayChannelSwitch;
    private View         panelChannelList;
    private View         popupResolution;
    private View         popupAspect;
    private View         btnPrev, btnNext;
    private TextView     tvChannelName;
    private TextView     tvChNumber;
    private TextView     tvStatus;
    private TextView     tvResolutionLabel;
    private TextView     tvSwitchName;
    private ImageView    ivChannelLogo;
    private ProgressBar  progressBuffering;
    private RecyclerView rvPanelChannels;
    private EditText     etPanelSearch;

    // ── State ──────────────────────────────────────────────────────────
    private Handler  handler;
    private Runnable hideOverlay;
    private Runnable hideSwitchNotif;
    private boolean  playerInitialized = false;

    private List<Channel> allChannels   = new ArrayList<>();
    private List<Channel> filteredList  = new ArrayList<>();
    private int           currentIndex  = 0;   // index channel aktif di allChannels
    private String        currentAspect = "fit";

    // Adapter ringan untuk panel channel
    private PanelChannelAdapter panelAdapter;

    // ── Lifecycle ──────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_player);

        handler = new Handler(Looper.getMainLooper());

        currentIndex = getIntent().getIntExtra(EXTRA_CHANNEL_INDEX, 0);

        bindViews();
        setupButtons();
        setupPanelSearch();
        loadChannelsAndPlay();
    }

    private void bindViews() {
        playerView           = findViewById(R.id.player_view);
        overlayInfo          = findViewById(R.id.overlay_info);
        overlayStatus        = findViewById(R.id.overlay_status);
        overlayChannelSwitch = findViewById(R.id.overlay_channel_switch);
        panelChannelList     = findViewById(R.id.panel_channel_list);
        popupResolution      = findViewById(R.id.popup_resolution);
        popupAspect          = findViewById(R.id.popup_aspect);
        btnPrev              = findViewById(R.id.btn_prev_channel);
        btnNext              = findViewById(R.id.btn_next_channel);
        tvChannelName        = findViewById(R.id.tv_channel_name);
        tvChNumber           = findViewById(R.id.tv_ch_number);
        tvStatus             = findViewById(R.id.tv_status);
        tvResolutionLabel    = findViewById(R.id.tv_resolution_label);
        tvSwitchName         = findViewById(R.id.tv_switch_name);
        ivChannelLogo        = findViewById(R.id.iv_channel_logo);
        progressBuffering    = findViewById(R.id.progress_buffering);
        rvPanelChannels      = findViewById(R.id.rv_panel_channels);
        etPanelSearch        = findViewById(R.id.et_panel_search);
    }

    private void setupButtons() {
        // Kembali
        View btnBack = findViewById(R.id.btn_back_player);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // Resolusi
        View btnRes = findViewById(R.id.btn_resolution);
        if (btnRes != null) btnRes.setOnClickListener(v -> togglePopup(popupResolution));

        // Aspect ratio
        View btnAspect = findViewById(R.id.btn_aspect);
        if (btnAspect != null) btnAspect.setOnClickListener(v -> togglePopup(popupAspect));

        // Channel list panel
        View btnList = findViewById(R.id.btn_channel_list);
        if (btnList != null) btnList.setOnClickListener(v -> togglePanel());

        // Tutup panel
        View btnClosePanel = findViewById(R.id.btn_close_panel);
        if (btnClosePanel != null) btnClosePanel.setOnClickListener(v -> hidePanel());

        // Prev / Next channel
        if (btnPrev != null) btnPrev.setOnClickListener(v -> switchChannelRelative(-1));
        if (btnNext != null) btnNext.setOnClickListener(v -> switchChannelRelative(1));

        // ── Resolusi options ──
        setupResOption(R.id.res_auto,  "AUTO",  -1, -1);
        setupResOption(R.id.res_1080,  "1080p", 1920, 1080);
        setupResOption(R.id.res_720,   "720p",  1280, 720);
        setupResOption(R.id.res_480,   "480p",  854,  480);
        setupResOption(R.id.res_360,   "360p",  640,  360);

        // ── Aspect ratio options ──
        setupAspectOption(R.id.aspect_fit,     "fit",     "▣  Fit");
        setupAspectOption(R.id.aspect_fill,    "fill",    "⬛  Fill");
        setupAspectOption(R.id.aspect_stretch, "stretch", "↔  Stretch");
        setupAspectOption(R.id.aspect_zoom,    "zoom",    "🔍  Zoom");
    }

    private void setupResOption(int viewId, String label, int w, int h) {
        TextView tv = findViewById(viewId);
        if (tv == null) return;
        tv.setOnClickListener(v -> {
            applyResolution(w, h, label);
            hidePopups();
        });
    }

    private void setupAspectOption(int viewId, String mode, String label) {
        TextView tv = findViewById(viewId);
        if (tv == null) return;
        tv.setOnClickListener(v -> {
            applyAspect(mode);
            hidePopups();
            // Update ikon tombol aspect
            TextView icon = findViewById(R.id.tv_aspect_icon);
            if (icon != null) icon.setText(label.substring(0, 1));
        });
    }

    private void setupPanelSearch() {
        if (etPanelSearch == null) return;
        etPanelSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                filterPanelChannels(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    // ── Load semua channel lalu mulai play ────────────────────────────
    private void loadChannelsAndPlay() {
        showStatus("Memuat channel...", true);

        // Ambil data channel dari intent dulu (langsung play)
        String name          = getIntent().getStringExtra(EXTRA_CHANNEL_NAME);
        String url           = getIntent().getStringExtra(EXTRA_CHANNEL_URL);
        String logo          = getIntent().getStringExtra(EXTRA_CHANNEL_LOGO);
        String drmScheme     = getIntent().getStringExtra(EXTRA_DRM_SCHEME);
        String drmLicenseUrl = getIntent().getStringExtra(EXTRA_DRM_LICENSE_URL);
        String drmKeyId      = getIntent().getStringExtra(EXTRA_DRM_KEY_ID);
        String drmKey        = getIntent().getStringExtra(EXTRA_DRM_KEY);

        setupPlayerUI(name, logo);
        initPlayer();
        playUrl(url, drmScheme, drmLicenseUrl, drmKeyId, drmKey);

        // Load semua channel di background untuk panel & navigasi
        ChannelRepository.getInstance(this).loadChannels(new ChannelRepository.Callback() {
            @Override
            public void onSuccess(List<Channel> channels) {
                allChannels = channels;
                filteredList = new ArrayList<>(channels);
                setupPanelAdapter();
                updateNavButtons();
                // Update nomor channel
                if (tvChNumber != null && currentIndex < channels.size()) {
                    Channel ch = channels.get(currentIndex);
                    tvChNumber.setText("CH " + String.format("%03d", ch.getNumber()));
                }
            }
            @Override public void onError(String msg) { /* abaikan, channel sudah diplay */ }
        });
    }

    private void setupPlayerUI(String name, String logo) {
        if (tvChannelName != null && name != null) tvChannelName.setText(name);
        if (ivChannelLogo != null && logo != null && !logo.isEmpty()) {
            try {
                Glide.with(this).load(logo)
                    .placeholder(R.drawable.ic_tv_placeholder)
                    .error(R.drawable.ic_tv_placeholder)
                    .into(ivChannelLogo);
            } catch (Exception e) { /* abaikan */ }
        }
        showOverlay();
    }

    // ── ExoPlayer ─────────────────────────────────────────────────────
    private void initPlayer() {
        if (player != null) { player.stop(); player.release(); player = null; }

        DefaultHttpDataSource.Factory httpFactory =
            new DefaultHttpDataSource.Factory()
                .setUserAgent("NexTV/1.0")
                .setConnectTimeoutMs(20_000)
                .setReadTimeoutMs(20_000)
                .setAllowCrossProtocolRedirects(true);

        DefaultRenderersFactory renderersFactory =
            new DefaultRenderersFactory(this)
                .setExtensionRendererMode(
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);

        player = new ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(
                new DefaultMediaSourceFactory(this).setDataSourceFactory(httpFactory))
            .build();

        if (playerView != null) {
            playerView.setPlayer(player);
            playerView.setUseController(false);
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
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
                        scheduleHideOverlay();
                        playerInitialized = true;
                        break;
                    case Player.STATE_ENDED:
                        showStatus("Stream berakhir", false);
                        break;
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                String msg = getErrorMessage(error);
                Log.e(TAG, "Error " + error.errorCode + ": " + error.getMessage());
                showStatus(msg, false);
            }
        });
    }

    private void playUrl(String url, String drmScheme, String drmLicenseUrl,
                          String drmKeyId, String drmKey) {
        if (url == null || url.trim().isEmpty()) {
            showStatus("URL tidak valid", false); return;
        }
        showStatus("Memuat stream...", true);
        playerInitialized = false;
        MediaItem item = buildMediaItem(url.trim(), drmScheme, drmLicenseUrl, drmKeyId, drmKey);
        player.setMediaItem(item);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    // ── Channel switching ─────────────────────────────────────────────
    private void switchToChannel(Channel ch, int newIndex) {
        currentIndex = newIndex;

        // Update UI header
        if (tvChannelName != null) tvChannelName.setText(ch.getName());
        if (tvChNumber != null)
            tvChNumber.setText("CH " + String.format("%03d", ch.getNumber()));
        if (ivChannelLogo != null) {
            if (!ch.getLogo().isEmpty()) {
                try {
                    Glide.with(this).load(ch.getLogo())
                        .placeholder(R.drawable.ic_tv_placeholder)
                        .error(R.drawable.ic_tv_placeholder)
                        .into(ivChannelLogo);
                } catch (Exception e) { ivChannelLogo.setImageResource(R.drawable.ic_tv_placeholder); }
            } else {
                ivChannelLogo.setImageResource(R.drawable.ic_tv_placeholder);
            }
        }

        // Notifikasi switch
        showSwitchNotif(ch.getName());

        // Play channel baru
        playUrl(ch.getUrl(), ch.getDrmScheme(), ch.getDrmLicenseUrl(),
                ch.getDrmKeyId(), ch.getDrmKey());

        updateNavButtons();
        updatePanelSelection(newIndex);
        hidePanel();
    }

    private void switchChannelRelative(int delta) {
        if (allChannels.isEmpty()) return;
        int newIndex = currentIndex + delta;
        if (newIndex < 0) newIndex = allChannels.size() - 1;
        if (newIndex >= allChannels.size()) newIndex = 0;
        switchToChannel(allChannels.get(newIndex), newIndex);
    }

    private void updateNavButtons() {
        if (allChannels.isEmpty()) return;
        if (btnPrev != null) btnPrev.setVisibility(View.VISIBLE);
        if (btnNext != null) btnNext.setVisibility(View.VISIBLE);
    }

    // ── Resolusi (video track selection) ─────────────────────────────
    private void applyResolution(int maxW, int maxH, String label) {
        if (tvResolutionLabel != null) tvResolutionLabel.setText(label);
        if (player == null) return;

        if (maxW < 0) {
            // AUTO: hapus override → ExoPlayer pilih sendiri
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

    // ── Aspect ratio / ukuran layar ───────────────────────────────────
    private void applyAspect(String mode) {
        if (playerView == null) return;
        currentAspect = mode;
        switch (mode) {
            case "fill":
                playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
                break;
            case "stretch":
                playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
                break;
            case "zoom":
                playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT);
                break;
            default: // "fit"
                playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                break;
        }
        String label = mode.substring(0, 1).toUpperCase() + mode.substring(1);
        Toast.makeText(this, "Ukuran layar: " + label, Toast.LENGTH_SHORT).show();
    }

    // ── Panel channel list ────────────────────────────────────────────
    private void setupPanelAdapter() {
        panelAdapter = new PanelChannelAdapter(filteredList, currentIndex, (ch, idx) -> {
            // Cari index asli di allChannels
            int realIndex = allChannels.indexOf(ch);
            switchToChannel(ch, realIndex >= 0 ? realIndex : idx);
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
                    ch.getCategory().toLowerCase().contains(q)) {
                    filteredList.add(ch);
                }
            }
        }
        if (panelAdapter != null) panelAdapter.notifyDataSetChanged();
    }

    private void updatePanelSelection(int newIndex) {
        if (panelAdapter != null) {
            panelAdapter.setActiveIndex(newIndex);
            if (rvPanelChannels != null)
                rvPanelChannels.scrollToPosition(
                    Math.min(newIndex, filteredList.size() - 1));
        }
    }

    private void togglePanel() {
        if (panelChannelList == null) return;
        boolean visible = panelChannelList.getVisibility() == View.VISIBLE;
        panelChannelList.setVisibility(visible ? View.GONE : View.VISIBLE);
        hidePopups();
        if (!visible) showOverlay();
    }

    private void hidePanel() {
        if (panelChannelList != null) panelChannelList.setVisibility(View.GONE);
    }

    // ── Popup helpers ─────────────────────────────────────────────────
    private void togglePopup(View popup) {
        if (popup == null) return;
        boolean showing = popup.getVisibility() == View.VISIBLE;
        hidePopups();
        if (!showing) popup.setVisibility(View.VISIBLE);
        showOverlay();
    }

    private void hidePopups() {
        if (popupResolution != null) popupResolution.setVisibility(View.GONE);
        if (popupAspect     != null) popupAspect.setVisibility(View.GONE);
    }

    // ── Notifikasi pindah channel ─────────────────────────────────────
    private void showSwitchNotif(String name) {
        if (overlayChannelSwitch == null || tvSwitchName == null) return;
        tvSwitchName.setText("▶  " + name);
        overlayChannelSwitch.setVisibility(View.VISIBLE);
        if (hideSwitchNotif != null) handler.removeCallbacks(hideSwitchNotif);
        hideSwitchNotif = () -> {
            if (overlayChannelSwitch != null)
                overlayChannelSwitch.setVisibility(View.GONE);
        };
        handler.postDelayed(hideSwitchNotif, 2500);
    }

    // ── MediaItem builder ─────────────────────────────────────────────
    private MediaItem buildMediaItem(String url, String drmScheme, String drmLicenseUrl,
                                     String drmKeyId, String drmKey) {
        String lower = url.toLowerCase();
        String mimeType;
        if (lower.contains(".m3u8") || lower.contains("/hls/"))
            mimeType = MimeTypes.APPLICATION_M3U8;
        else if (lower.contains(".mpd") || lower.contains("/dash/"))
            mimeType = MimeTypes.APPLICATION_MPD;
        else if (lower.startsWith("rtsp://"))
            mimeType = MimeTypes.APPLICATION_RTSP;
        else
            mimeType = null;

        boolean hasDrm = drmScheme != null && !drmScheme.trim().isEmpty();
        if (hasDrm && !MimeTypes.APPLICATION_MPD.equals(mimeType))
            mimeType = MimeTypes.APPLICATION_MPD;

        MediaItem.Builder b = new MediaItem.Builder().setUri(Uri.parse(url));
        if (mimeType != null) b.setMimeType(mimeType);

        if (hasDrm) {
            MediaItem.DrmConfiguration drm = buildDrmConfig(
                getDrmUuid(drmScheme), drmScheme, drmLicenseUrl, drmKeyId, drmKey);
            if (drm != null) b.setDrmConfiguration(drm);
        }
        return b.build();
    }

    private MediaItem.DrmConfiguration buildDrmConfig(UUID uuid, String scheme,
            String licenseUrl, String keyIdHex, String keyHex) {
        try {
            MediaItem.DrmConfiguration.Builder b = new MediaItem.DrmConfiguration.Builder(uuid);
            if ("widevine".equalsIgnoreCase(scheme) && licenseUrl != null) {
                b.setLicenseUri(licenseUrl);
            } else if ("clearkey".equalsIgnoreCase(scheme)
                    && keyIdHex != null && keyHex != null) {
                byte[] kid = hexToBytes(keyIdHex);
                byte[] key = hexToBytes(keyHex);
                String kidB64 = Base64.encodeToString(kid,
                    Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING);
                String keyB64 = Base64.encodeToString(key,
                    Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING);
                String json = "{\"keys\":[{\"kty\":\"oct\",\"k\":\""
                    + keyB64 + "\",\"kid\":\"" + kidB64 + "\"}],\"type\":\"temporary\"}";
                String dataUri = "data:text/plain;base64,"
                    + Base64.encodeToString(
                        json.getBytes(Charset.forName("UTF-8")), Base64.NO_WRAP);
                b.setLicenseUri(dataUri);
            }
            return b.build();
        } catch (Exception e) {
            Log.e(TAG, "DRM config error", e);
            return null;
        }
    }

    private UUID getDrmUuid(String scheme) {
        if (scheme == null) return C.WIDEVINE_UUID;
        switch (scheme.toLowerCase()) {
            case "widevine":  return C.WIDEVINE_UUID;
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
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:  return "Tidak ada koneksi internet";
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT: return "Koneksi timeout";
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS:            return "Server tidak merespons";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED: return "Format tidak didukung";
            case PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED:        return "DRM tidak didukung";
            case PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED:       return "Gagal provisioning DRM";
            case PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR:             return "Konten DRM error";
            case PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED:return "Gagal ambil lisensi DRM";
            default: return "Gagal memutar (kode: " + e.errorCode + ")";
        }
    }

    // ── Key events (D-Pad TV) ─────────────────────────────────────────
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        showOverlay();
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (panelChannelList != null &&
                    panelChannelList.getVisibility() == View.VISIBLE) {
                    hidePanel(); return true;
                }
                if (popupResolution != null &&
                    popupResolution.getVisibility() == View.VISIBLE) {
                    hidePopups(); return true;
                }
                if (popupAspect != null &&
                    popupAspect.getVisibility() == View.VISIBLE) {
                    hidePopups(); return true;
                }
                finish(); return true;

            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                switchChannelRelative(-1); return true;

            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                switchChannelRelative(1); return true;

            case KeyEvent.KEYCODE_CHANNEL_UP:
                switchChannelRelative(-1); return true;

            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                switchChannelRelative(1); return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ── UI helpers ────────────────────────────────────────────────────
    private void showStatus(String msg, boolean spinning) {
        if (overlayStatus     != null) overlayStatus.setVisibility(View.VISIBLE);
        if (tvStatus          != null) tvStatus.setText(msg);
        if (progressBuffering != null)
            progressBuffering.setVisibility(spinning ? View.VISIBLE : View.GONE);
    }

    private void hideStatus() {
        if (overlayStatus != null) overlayStatus.setVisibility(View.GONE);
    }

    private void showOverlay() {
        if (overlayInfo != null) overlayInfo.setVisibility(View.VISIBLE);
        scheduleHideOverlay();
    }

    private void scheduleHideOverlay() {
        if (hideOverlay != null) handler.removeCallbacks(hideOverlay);
        hideOverlay = () -> {
            if (overlayInfo != null) overlayInfo.setVisibility(View.GONE);
            hidePopups();
        };
        handler.postDelayed(hideOverlay, 5000);
    }

    @Override protected void onPause()  { super.onPause();  if (player != null) player.pause(); }
    @Override protected void onResume() { super.onResume(); if (player != null && playerInitialized) player.play(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            if (hideOverlay    != null) handler.removeCallbacks(hideOverlay);
            if (hideSwitchNotif != null) handler.removeCallbacks(hideSwitchNotif);
        }
        if (player != null) { player.stop(); player.release(); player = null; }
        if (playerView != null) playerView.setPlayer(null);
    }

    // ══════════════════════════════════════════════════════════════════
    // Inner Adapter: Panel Channel List
    // ══════════════════════════════════════════════════════════════════
    static class PanelChannelAdapter
            extends RecyclerView.Adapter<PanelChannelAdapter.VH> {

        interface OnChannelSelect { void onSelect(Channel ch, int idx); }

        private final List<Channel>   list;
        private int                   activeIndex;
        private final OnChannelSelect listener;

        PanelChannelAdapter(List<Channel> list, int activeIndex, OnChannelSelect l) {
            this.list        = list;
            this.activeIndex = activeIndex;
            this.listener    = l;
        }

        void setActiveIndex(int idx) {
            int old = activeIndex;
            activeIndex = idx;
            notifyItemChanged(old);
            notifyItemChanged(idx);
        }

        @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(parent.getContext());
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setPadding(16, 0, 16, 0);
            row.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, 56));
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setClickable(true);
            row.setFocusable(true);

            TextView tvNum = new TextView(parent.getContext());
            tvNum.setTextSize(10);
            tvNum.setTextColor(0xFF7A8AAA);
            tvNum.setMinWidth(40);
            tvNum.setTag("num");
            row.addView(tvNum);

            TextView tvName = new TextView(parent.getContext());
            android.widget.LinearLayout.LayoutParams lp =
                new android.widget.LinearLayout.LayoutParams(0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvName.setLayoutParams(lp);
            tvName.setTextSize(13);
            tvName.setTextColor(0xFFF0F4FF);
            tvName.setSingleLine(true);
            tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvName.setTag("name");
            row.addView(tvName);

            TextView tvCat = new TextView(parent.getContext());
            tvCat.setTextSize(10);
            tvCat.setTextColor(0xFF7A8AAA);
            tvCat.setTag("cat");
            row.addView(tvCat);

            return new VH(row);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            Channel ch = list.get(position);
            boolean active = position == activeIndex;

            holder.itemView.setBackgroundColor(active ? 0x1AFFD700 : 0x00000000);

            TextView tvNum  = holder.itemView.findViewWithTag("num");
            TextView tvName = holder.itemView.findViewWithTag("name");
            TextView tvCat  = holder.itemView.findViewWithTag("cat");

            if (tvNum  != null) tvNum.setText(String.format("%03d", ch.getNumber()));
            if (tvName != null) {
                tvName.setText(ch.getName());
                tvName.setTextColor(active ? 0xFFFFD700 : 0xFFF0F4FF);
                tvName.setTypeface(null, active
                    ? android.graphics.Typeface.BOLD
                    : android.graphics.Typeface.NORMAL);
            }
            if (tvCat  != null) tvCat.setText(ch.getCategory());

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onSelect(ch, position);
            });
        }

        @Override public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            VH(View v) { super(v); }
        }
    }
}
