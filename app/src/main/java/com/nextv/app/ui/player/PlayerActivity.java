package com.nextv.app.ui.player;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
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
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import com.bumptech.glide.Glide;
import com.nextv.app.R;
import com.nextv.app.data.Channel;

import java.util.HashMap;
import java.util.Map;

public class PlayerActivity extends AppCompatActivity {

    private static final String TAG = "PlayerActivity";

    public static final String EXTRA_CHANNEL_NAME        = "channel_name";
    public static final String EXTRA_CHANNEL_URL         = "channel_url";
    public static final String EXTRA_CHANNEL_LOGO        = "channel_logo";
    public static final String EXTRA_DRM_SCHEME          = "drm_scheme";
    public static final String EXTRA_DRM_LICENSE_URL     = "drm_license_url";
    public static final String EXTRA_DRM_KEY_ID          = "drm_key_id";
    public static final String EXTRA_DRM_KEY             = "drm_key";

    private ExoPlayer    player;
    private PlayerView   playerView;
    private View         overlayInfo;
    private View         overlayStatus;
    private TextView     tvChannelName;
    private TextView     tvStatus;
    private ImageView    ivChannelLogo;
    private ProgressBar  progressBuffering;

    private Handler  handler;
    private Runnable hideOverlay;
    private boolean  playerInitialized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_player);

        handler = new Handler(Looper.getMainLooper());

        String name          = getIntent().getStringExtra(EXTRA_CHANNEL_NAME);
        String url           = getIntent().getStringExtra(EXTRA_CHANNEL_URL);
        String logo          = getIntent().getStringExtra(EXTRA_CHANNEL_LOGO);
        String drmScheme     = getIntent().getStringExtra(EXTRA_DRM_SCHEME);
        String drmLicenseUrl = getIntent().getStringExtra(EXTRA_DRM_LICENSE_URL);
        String drmKeyId      = getIntent().getStringExtra(EXTRA_DRM_KEY_ID);
        String drmKey        = getIntent().getStringExtra(EXTRA_DRM_KEY);

        bindViews();
        setupInfo(name, logo);
        startPlayer(url, drmScheme, drmLicenseUrl, drmKeyId, drmKey);
    }

    private void bindViews() {
        playerView        = findViewById(R.id.player_view);
        overlayInfo       = findViewById(R.id.overlay_info);
        overlayStatus     = findViewById(R.id.overlay_status);
        tvChannelName     = findViewById(R.id.tv_channel_name);
        tvStatus          = findViewById(R.id.tv_status);
        ivChannelLogo     = findViewById(R.id.iv_channel_logo);
        progressBuffering = findViewById(R.id.progress_buffering);

        View btnBack = findViewById(R.id.btn_back_player);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }

    private void setupInfo(String name, String logo) {
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

    private void startPlayer(String url, String drmScheme, String drmLicenseUrl,
                              String drmKeyId, String drmKey) {
        if (url == null || url.trim().isEmpty()) {
            showStatus("URL channel tidak valid", false);
            return;
        }

        showStatus("Memuat stream...", true);

        try {
            // ── Data source factory (mendukung header kustom) ──────────────
            DefaultHttpDataSource.Factory httpFactory =
                new DefaultHttpDataSource.Factory()
                    .setUserAgent("NexTV/1.0")
                    .setConnectTimeoutMs(15_000)
                    .setReadTimeoutMs(20_000)
                    .setAllowCrossProtocolRedirects(true);

            // ── Player builder ─────────────────────────────────────────────
            DefaultRenderersFactory renderersFactory =
                new DefaultRenderersFactory(this)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);

            DefaultMediaSourceFactory mediaSourceFactory =
                new DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(httpFactory);

            // ── Terapkan DRM jika ada ──────────────────────────────────────
            boolean hasDrm = drmScheme != null && !drmScheme.trim().isEmpty();

            ExoPlayer.Builder builder = new ExoPlayer.Builder(this, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory);

            player = builder.build();

            if (playerView != null) {
                playerView.setPlayer(player);
                playerView.setUseController(false);
            }

            // ── Bangun MediaItem ───────────────────────────────────────────
            MediaItem mediaItem = buildMediaItem(
                url.trim(), drmScheme, drmLicenseUrl, drmKeyId, drmKey);

            player.setMediaItem(mediaItem);
            player.prepare();
            player.setPlayWhenReady(true);

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
                    Log.e(TAG, "Player error: " + error.getMessage(), error);
                    showStatus(msg, false);
                    Toast.makeText(PlayerActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Init error", e);
            showStatus("Gagal inisialisasi player: " + e.getMessage(), false);
        }
    }

    /**
     * Bangun MediaItem dengan dukungan HLS, DASH, dan DASH+DRM.
     *
     * Format channels.json untuk DRM:
     *   "drm_scheme": "widevine"            → Widevine
     *   "drm_license_url": "https://..."    → License server URL
     *
     *   "drm_scheme": "clearkey"            → ClearKey (no server needed)
     *   "drm_key_id": "aabbccdd..."         → hex key ID
     *   "drm_key":    "11223344..."         → hex key value
     */
    private MediaItem buildMediaItem(String url, String drmScheme,
                                     String drmLicenseUrl, String drmKeyId, String drmKey) {
        String lower = url.toLowerCase();

        // Tentukan MIME type
        String mimeType;
        if (lower.contains(".m3u8") || lower.contains("/hls/") || lower.contains("hls")) {
            mimeType = MimeTypes.APPLICATION_M3U8;
        } else if (lower.contains(".mpd") || lower.contains("/dash/")
                || lower.contains("dash") || lower.contains("manifest")) {
            mimeType = MimeTypes.APPLICATION_MPD;
        } else if (lower.contains("rtsp://")) {
            mimeType = MimeTypes.APPLICATION_RTSP;
        } else {
            mimeType = null; // ExoPlayer detect otomatis
        }

        MediaItem.Builder itemBuilder = new MediaItem.Builder()
            .setUri(Uri.parse(url));

        if (mimeType != null) {
            itemBuilder.setMimeType(mimeType);
        }

        // Tambahkan konfigurasi DRM
        boolean hasDrm = drmScheme != null && !drmScheme.trim().isEmpty();
        if (hasDrm) {
            MediaItem.DrmConfiguration.Builder drmBuilder =
                new MediaItem.DrmConfiguration.Builder(getDrmUuid(drmScheme));

            if ("widevine".equalsIgnoreCase(drmScheme) && drmLicenseUrl != null) {
                drmBuilder.setLicenseUri(drmLicenseUrl);
                // Force L3 agar berjalan di semua perangkat
                drmBuilder.forceDefaultLicenseUri();
            } else if ("clearkey".equalsIgnoreCase(drmScheme)
                    && drmKeyId != null && drmKey != null) {
                // ClearKey: encode sebagai JSON inline URI
                String clearKeyJson = buildClearKeyJson(drmKeyId, drmKey);
                drmBuilder.setLicenseUri("data:text/plain;base64,"
                    + Base64.encodeToString(clearKeyJson.getBytes(), Base64.NO_WRAP));
            }

            itemBuilder.setDrmConfiguration(drmBuilder.build());
            // Pastikan pakai DASH jika belum di-set
            if (mimeType == null || mimeType.equals(MimeTypes.APPLICATION_M3U8)) {
                itemBuilder.setMimeType(MimeTypes.APPLICATION_MPD);
            }
        }

        return itemBuilder.build();
    }

    /** Konversi nama scheme ke UUID yang dikenal ExoPlayer */
    private java.util.UUID getDrmUuid(String scheme) {
        if (scheme == null) return C.WIDEVINE_UUID;
        switch (scheme.toLowerCase()) {
            case "widevine":  return C.WIDEVINE_UUID;
            case "clearkey":  return C.CLEARKEY_UUID;
            case "playready": return C.PLAYREADY_UUID;
            default:          return C.WIDEVINE_UUID;
        }
    }

    /** Bangun JSON ClearKey dari key ID dan key hex */
    private String buildClearKeyJson(String keyIdHex, String keyHex) {
        String kidB64 = Base64.encodeToString(hexToBytes(keyIdHex),
            Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING);
        String keyB64 = Base64.encodeToString(hexToBytes(keyHex),
            Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING);
        return "{\"keys\":[{\"kty\":\"oct\",\"k\":\"" + keyB64
            + "\",\"kid\":\"" + kidB64 + "\"}],\"type\":\"temporary\"}";
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private String getErrorMessage(PlaybackException error) {
        switch (error.errorCode) {
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
                return "Tidak ada koneksi internet";
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
                return "Koneksi timeout, coba lagi";
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS:
                return "Server channel tidak merespons (HTTP error)";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
                return "Format stream tidak didukung";
            case PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED:
                return "DRM tidak didukung perangkat ini";
            case PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED:
                return "Gagal provisioning DRM";
            case PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR:
                return "Konten DRM bermasalah";
            case PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED:
                return "Gagal mendapatkan lisensi DRM";
            default:
                return "Gagal memutar channel (kode: " + error.errorCode + ")";
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────

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
        };
        handler.postDelayed(hideOverlay, 4000);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true; }
        showOverlay();
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onPause()   { super.onPause();   if (player != null) player.pause(); }
    @Override protected void onResume()  { super.onResume();  if (player != null && playerInitialized) player.play(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && hideOverlay != null) handler.removeCallbacks(hideOverlay);
        if (player != null) { player.stop(); player.release(); player = null; }
        if (playerView != null) playerView.setPlayer(null);
    }
}
