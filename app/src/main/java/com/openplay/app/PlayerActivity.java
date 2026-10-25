
package com.openplay.app;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class PlayerActivity extends Activity {

    private static final String TAG = "OpenPlayPlayer";

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView errorView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        createPlayerScreen();
        startPlayback();
    }

    private void createPlayerScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);

        root.addView(
                playerView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        errorView = new TextView(this);
        errorView.setTextColor(0xFFFFFFFF);
        errorView.setTextSize(16);
        errorView.setGravity(android.view.Gravity.CENTER);
        errorView.setPadding(32, 32, 32, 32);
        errorView.setBackgroundColor(0xFF202020);
        errorView.setVisibility(View.GONE);

        root.addView(
                errorView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        setContentView(root);
    }

    private void startPlayback() {
        String uriString = getIntent().getStringExtra("video_uri");

        if (uriString == null || uriString.trim().isEmpty()) {
            Uri intentUri = getIntent().getData();

            if (intentUri != null) {
                uriString = intentUri.toString();
            }
        }

        if (uriString == null || uriString.trim().isEmpty()) {
            showError("No video was selected. Go back and select a video.");
            return;
        }

        try {
            Uri videoUri = Uri.parse(uriString);

            player = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(player);

            player.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(PlaybackException error) {
                    Log.e(
                            TAG,
                            "Playback failed. Code: "
                                    + error.errorCodeName,
                            error
                    );

                    showError(
                            "Unable to play this video.\n\n"
                                    + "Error: " + error.errorCodeName
                                    + "\n\n"
                                    + "The file format, video codec, or audio codec "
                                    + "may not be supported."
                    );
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        errorView.setVisibility(View.GONE);
                    }
                }
            });

            MediaItem mediaItem = MediaItem.fromUri(videoUri);
            player.setMediaItem(mediaItem);
            player.prepare();
            player.play();

            Log.d(TAG, "Opening video URI: " + videoUri);

        } catch (Exception e) {
            Log.e(TAG, "Could not start playback", e);
            showError("Could not open video.\n\n" + e.getMessage());
        }
    }

    private void showError(String message) {
        if (errorView != null) {
            errorView.setText(message);
            errorView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        if (playerView != null) {
            playerView.setPlayer(null);
        }

        if (player != null) {
            player.release();
            player = null;
        }

        getWindow().clearFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        super.onDestroy();
    }
}
