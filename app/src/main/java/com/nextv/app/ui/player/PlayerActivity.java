package com.nextv.app.ui.player;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.bumptech.glide.Glide;
import com.nextv.app.R;

public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_CHANNEL_NAME = "channel_name";
    public static final String EXTRA_CHANNEL_URL  = "channel_url";
    public static final String EXTRA_CHANNEL_LOGO = "channel_logo";

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

        // Jaga layar tetap nyala saat streaming
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_player);

        handler = new Handler(Looper.getMainLooper());

        String name = getIntent().getStringExtra(EXTRA_CHANNEL_NAME);
        String url  = getIntent().getStringExtra(EXTRA_CHANNEL_URL);
        String logo = getIntent().getStringExtra(EXTRA_CHANNEL_LOGO);

        bindViews();
        setupInfo(name, logo);
        startPlayer(url);
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
            } catch (Exception e) {
                // Glide error - abaikan, tidak crash
            }
        }

        showOverlay();
    }

    private void startPlayer(String url) {
        if (url == null || url.trim().isEmpty()) {
            showStatus("URL channel tidak valid", false);
            return;
        }

        showStatus("Memuat stream...", true);

        try {
            player = new ExoPlayer.Builder(this).build();

            if (playerView != null) {
                playerView.setPlayer(player);
                playerView.setUseController(false);
            }

            // Tentukan tipe stream otomatis
            MediaItem mediaItem = buildMediaItem(url.trim());

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
                        case Player.STATE_IDLE:
                            break;
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    String msg = getErrorMessage(error);
                    showStatus(msg, false);
                    Toast.makeText(PlayerActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            });

        } catch (Exception e) {
            showStatus("Gagal inisialisasi player: " + e.getMessage(), false);
        }
    }

    private MediaItem buildMediaItem(String url) {
        String lower = url.toLowerCase();
        if (lower.contains(".m3u8") || lower.contains("hls")) {
            return new MediaItem.Builder()
                .setUri(Uri.parse(url))
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build();
        } else if (lower.contains(".mpd") || lower.contains("dash")) {
            return new MediaItem.Builder()
                .setUri(Uri.parse(url))
                .setMimeType(MimeTypes.APPLICATION_MPD)
                .build();
        } else if (lower.contains("rtsp://")) {
            return new MediaItem.Builder()
                .setUri(Uri.parse(url))
                .setMimeType(MimeTypes.APPLICATION_RTSP)
                .build();
        } else {
            // Biarkan ExoPlayer detect otomatis
            return MediaItem.fromUri(Uri.parse(url));
        }
    }

    private String getErrorMessage(PlaybackException error) {
        switch (error.errorCode) {
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
                return "Tidak ada koneksi internet";
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
                return "Koneksi timeout, coba lagi";
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS:
                return "Server channel tidak merespons";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
                return "Format stream tidak didukung";
            default:
                return "Gagal memutar channel ini";
        }
    }

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
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        showOverlay();
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null && playerInitialized) player.play();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && hideOverlay != null) {
            handler.removeCallbacks(hideOverlay);
        }
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
        if (playerView != null) {
            playerView.setPlayer(null);
        }
    }
}
