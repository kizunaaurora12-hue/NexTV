package com.nextv.app.ui.player;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
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

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView tvChannelName, tvStatus;
    private ImageView ivChannelLogo;
    private View overlayInfo;
    private Handler handler = new Handler();
    private Runnable hideOverlayRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        String channelName = getIntent().getStringExtra(EXTRA_CHANNEL_NAME);
        String channelUrl  = getIntent().getStringExtra(EXTRA_CHANNEL_URL);
        String channelLogo = getIntent().getStringExtra(EXTRA_CHANNEL_LOGO);

        initViews(channelName, channelLogo);
        initPlayer(channelUrl, channelName);
    }

    private void initViews(String name, String logo) {
        playerView    = findViewById(R.id.player_view);
        tvChannelName = findViewById(R.id.tv_channel_name);
        tvStatus      = findViewById(R.id.tv_status);
        ivChannelLogo = findViewById(R.id.iv_channel_logo);
        overlayInfo   = findViewById(R.id.overlay_info);

        if (name != null) tvChannelName.setText(name);

        // Back button di overlay
        View btnBack = findViewById(R.id.btn_back_player);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        if (logo != null && !logo.isEmpty()) {
            Glide.with(this).load(logo)
                .placeholder(R.drawable.ic_tv_placeholder)
                .into(ivChannelLogo);
        }

        // Sembunyikan overlay setelah 4 detik
        showOverlay();
    }

    private void initPlayer(String url, String name) {
        if (url == null || url.isEmpty()) {
            tvStatus.setText("❌ URL channel tidak valid");
            return;
        }

        tvStatus.setText("⏳ Memuat stream...");

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerView.setUseController(false); // pakai custom overlay kita

        // Buat MediaItem berdasarkan tipe URL
        MediaItem mediaItem;
        if (url.contains(".m3u8") || url.contains("hls")) {
            mediaItem = new MediaItem.Builder()
                .setUri(Uri.parse(url))
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build();
        } else if (url.contains(".mpd")) {
            mediaItem = new MediaItem.Builder()
                .setUri(Uri.parse(url))
                .setMimeType(MimeTypes.APPLICATION_MPD)
                .build();
        } else {
            mediaItem = MediaItem.fromUri(Uri.parse(url));
        }

        player.setMediaItem(mediaItem);
        player.prepare();
        player.setPlayWhenReady(true);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                switch (state) {
                    case Player.STATE_BUFFERING:
                        tvStatus.setText("⏳ Buffering...");
                        tvStatus.setVisibility(View.VISIBLE);
                        break;
                    case Player.STATE_READY:
                        tvStatus.setVisibility(View.GONE);
                        scheduleHideOverlay();
                        break;
                    case Player.STATE_ENDED:
                        tvStatus.setText("⏹ Stream berakhir");
                        tvStatus.setVisibility(View.VISIBLE);
                        break;
                    case Player.STATE_IDLE:
                        break;
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                String msg = "❌ Error: ";
                switch (error.errorCode) {
                    case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
                        msg += "Tidak ada koneksi internet"; break;
                    case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
                        msg += "Koneksi timeout"; break;
                    case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
                        msg += "Format stream tidak didukung"; break;
                    default:
                        msg += "Gagal memuat channel";
                }
                tvStatus.setText(msg);
                tvStatus.setVisibility(View.VISIBLE);
                Toast.makeText(PlayerActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showOverlay() {
        if (overlayInfo != null) {
            overlayInfo.setVisibility(View.VISIBLE);
            scheduleHideOverlay();
        }
    }

    private void scheduleHideOverlay() {
        if (hideOverlayRunnable != null) handler.removeCallbacks(hideOverlayRunnable);
        hideOverlayRunnable = () -> {
            if (overlayInfo != null) overlayInfo.setVisibility(View.GONE);
        };
        handler.postDelayed(hideOverlayRunnable, 4000);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Tombol BACK = tutup player
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        // Semua tombol lain = tampilkan overlay
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
        if (player != null) player.play();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && hideOverlayRunnable != null) {
            handler.removeCallbacks(hideOverlayRunnable);
        }
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
