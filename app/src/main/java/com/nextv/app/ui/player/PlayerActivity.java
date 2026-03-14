package com.nextv.app.ui.player;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
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

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView tvChannelName, tvStatus;
    private ImageView ivChannelLogo;
    private View overlayInfo;
    private View overlayStatus;
    private ProgressBar progressBuffering;

    private final Handler handler = new Handler();
    private Runnable hideOverlayRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        String name = getIntent().getStringExtra(EXTRA_CHANNEL_NAME);
        String url  = getIntent().getStringExtra(EXTRA_CHANNEL_URL);
        String logo = getIntent().getStringExtra(EXTRA_CHANNEL_LOGO);

        initViews(name, logo);
        initPlayer(url);
    }

    private void initViews(String name, String logo) {
        playerView        = findViewById(R.id.player_view);
        tvChannelName     = findViewById(R.id.tv_channel_name);
        tvStatus          = findViewById(R.id.tv_status);
        ivChannelLogo     = findViewById(R.id.iv_channel_logo);
        overlayInfo       = findViewById(R.id.overlay_info);
        overlayStatus     = findViewById(R.id.overlay_status);
        progressBuffering = findViewById(R.id.progress_buffering);

        if (tvChannelName != null && name != null) tvChannelName.setText(name);

        if (ivChannelLogo != null && logo != null && !logo.isEmpty()) {
            Glide.with(this).load(logo)
                .placeholder(R.drawable.ic_tv_placeholder)
                .error(R.drawable.ic_tv_placeholder)
                .into(ivChannelLogo);
        }

        View btnBack = findViewById(R.id.btn_back_player);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        showOverlay();
    }

    private void initPlayer(String url) {
        if (url == null || url.isEmpty()) {
            setStatus("URL channel tidak valid", false);
            return;
        }

        setStatus("Memuat stream...", true);

        player = new ExoPlayer.Builder(this).build();
        if (playerView != null) {
            playerView.setPlayer(player);
            playerView.setUseController(false);
        }

        MediaItem mediaItem;
        if (url.contains(".m3u8")) {
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
                        setStatus("Buffering...", true);
                        break;
                    case Player.STATE_READY:
                        hideStatus();
                        scheduleHideOverlay();
                        break;
                    case Player.STATE_ENDED:
                        setStatus("Stream berakhir", false);
                        break;
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                String msg;
                switch (error.errorCode) {
                    case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
                        msg = "Tidak ada koneksi internet"; break;
                    case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
                        msg = "Koneksi timeout"; break;
                    default:
                        msg = "Gagal memuat channel";
                }
                setStatus(msg, false);
                Toast.makeText(PlayerActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setStatus(String msg, boolean showProgress) {
        if (overlayStatus    != null) overlayStatus.setVisibility(View.VISIBLE);
        if (tvStatus         != null) tvStatus.setText(msg);
        if (progressBuffering!= null)
            progressBuffering.setVisibility(showProgress ? View.VISIBLE : View.GONE);
    }

    private void hideStatus() {
        if (overlayStatus != null) overlayStatus.setVisibility(View.GONE);
    }

    private void showOverlay() {
        if (overlayInfo != null) overlayInfo.setVisibility(View.VISIBLE);
        scheduleHideOverlay();
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
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true; }
        showOverlay();
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onPause()   { super.onPause();   if (player != null) player.pause(); }
    @Override protected void onResume()  { super.onResume();  if (player != null) player.play(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (hideOverlayRunnable != null) handler.removeCallbacks(hideOverlayRunnable);
        if (player != null) { player.release(); player = null; }
    }
}
