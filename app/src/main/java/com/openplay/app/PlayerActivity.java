package com.openplay.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.ArrayList;
import java.util.Locale;

public class PlayerActivity extends Activity {

private LibVLC libVLC;
private MediaPlayer mediaPlayer;
private VLCVideoLayout videoLayout;

private TextView timeText;
private SeekBar seekBar;
private Button playButton;

private SharedPreferences preferences;
private String videoUri;
private String videoName;

private boolean isSeeking = false;
private boolean isPrepared = false;
private boolean restorePending = true;
private long savedPosition = 0;

private final Handler handler =
        new Handler(Looper.getMainLooper());

private final Runnable progressUpdater = new Runnable() {
    @Override
    public void run() {
        if (mediaPlayer != null && isPrepared) {
            updateProgress();
        }
        handler.postDelayed(this, 500);
    }
};

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    preferences = getSharedPreferences(
            "OpenPlayPlayback", MODE_PRIVATE
    );

    videoUri = getIntent().getStringExtra("video_uri");
    videoName = getIntent().getStringExtra("video_name");

    if (videoUri == null && getIntent().getData() != null) {
        videoUri = getIntent().getData().toString();
    }

    if (videoName == null) {
        videoName = "OpenPlay Player";
    }

    if (videoUri == null) {
        Toast.makeText(this, "Video could not be opened",
                Toast.LENGTH_LONG).show();
        finish();
        return;
    }

    getWindow().setStatusBarColor(Color.BLACK);
    getWindow().setNavigationBarColor(Color.BLACK);

    createInterface();
    startPlayer();
}

private void createInterface() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.BLACK);

    TextView title = new TextView(this);
    title.setText(videoName);
    title.setTextColor(Color.WHITE);
    title.setTextSize(16);
    title.setMaxLines(2);
    title.setPadding(16, 12, 16, 12);
    root.addView(title);

    videoLayout = new VLCVideoLayout(this);
    root.addView(videoLayout, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1
    ));

    timeText = new TextView(this);
    timeText.setText("00:00 / 00:00");
    timeText.setTextColor(Color.WHITE);
    timeText.setGravity(Gravity.CENTER);
    timeText.setPadding(8, 8, 8, 0);
    root.addView(timeText);

    seekBar = new SeekBar(this);
    seekBar.setMax(1000);
    root.addView(seekBar);

    LinearLayout controls = new LinearLayout(this);
    controls.setGravity(Gravity.CENTER);

    Button backButton = new Button(this);
    backButton.setText("-10s");
    backButton.setOnClickListener(v -> seekRelative(-10000));
    controls.addView(backButton);

    playButton = new Button(this);
    playButton.setText("LOADING");
    playButton.setOnClickListener(v -> togglePlayback());
    controls.addView(playButton);

    Button forwardButton = new Button(this);
    forwardButton.setText("+10s");
    forwardButton.setOnClickListener(v -> seekRelative(10000));
    controls.addView(forwardButton);

    root.addView(controls);
    setContentView(root);

    seekBar.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onStartTrackingTouch(SeekBar bar) {
                    isSeeking = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar bar) {
                    if (mediaPlayer != null && isPrepared) {
                        long length = mediaPlayer.getLength();
                        if (length > 0) {
                            long target =
                                    length * bar.getProgress() / 1000L;
                            mediaPlayer.setTime(target);
                            savePositionAt(target);
                        }
                    }
                    isSeeking = false;
                }

                @Override
                public void onProgressChanged(
                        SeekBar bar, int progress, boolean fromUser) {
                }
            }
    );
}

private void startPlayer() {
    try {
        ArrayList<String> options = new ArrayList<>();
        options.add("--no-video-title-show");

        libVLC = new LibVLC(this, options);
        mediaPlayer = new MediaPlayer(libVLC);

        mediaPlayer.setEventListener(event ->
                runOnUiThread(() -> {
                    switch (event.type) {
                        case MediaPlayer.Event.Playing:
                            isPrepared = true;
                            playButton.setText("PAUSE");

                            if (restorePending) {
                                restorePending = false;
                                if (savedPosition > 0) {
                                    mediaPlayer.setTime(savedPosition);
                                }
                            }

                            updateProgress();
                            break;

                        case MediaPlayer.Event.LengthChanged:
                        case MediaPlayer.Event.TimeChanged:
                            updateProgress();
                            break;

                        case MediaPlayer.Event.EncounteredError:
                            isPrepared = false;
                            playButton.setText("RETRY");
                            Toast.makeText(
                                    PlayerActivity.this,
                                    "VLC could not decode this video",
                                    Toast.LENGTH_LONG
                            ).show();
                            break;

                        case MediaPlayer.Event.Stopped:
                            playButton.setText("PLAY");
                            break;
                    }
                })
        );

        mediaPlayer.attachViews(videoLayout, null, false, false);

        savedPosition = preferences.getLong(positionKey(), 0L);

        Media media = new Media(libVLC, Uri.parse(videoUri));
        mediaPlayer.setMedia(media);
        media.release();

        mediaPlayer.play();
        handler.post(progressUpdater);

    } catch (Exception e) {
        Toast.makeText(this,
                "Player error: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
    }
}

private String positionKey() {
    return "position_" + videoUri;
}

private void togglePlayback() {
    if (mediaPlayer == null) {
        return;
    }

    if (mediaPlayer.isPlaying()) {
        mediaPlayer.pause();
        savePosition();
        playButton.setText("PLAY");
    } else {
        mediaPlayer.play();
        playButton.setText("LOADING");
    }
}

private void seekRelative(long offset) {
    if (mediaPlayer == null || !isPrepared) {
        return;
    }

    long current = Math.max(0, mediaPlayer.getTime());
    long length = mediaPlayer.getLength();
    long target = Math.max(0, current + offset);

    if (length > 0) {
        target = Math.min(target, length);
    }

    mediaPlayer.setTime(target);
    savePositionAt(target);
    updateProgress();
}

private void updateProgress() {
    if (mediaPlayer == null) {
        return;
    }

    long current = Math.max(0, mediaPlayer.getTime());
    long length = Math.max(0, mediaPlayer.getLength());

    if (!isSeeking && length > 0) {
        seekBar.setProgress((int) Math.min(
                1000, current * 1000L / length
        ));
    }

    timeText.setText(formatTime(current) + " / " + formatTime(length));

    if (length > 0 && current >= length - 1500) {
        preferences.edit().remove(positionKey()).apply();
    }

    if (isPrepared) {
        playButton.setText(
                mediaPlayer.isPlaying() ? "PAUSE" : "PLAY"
        );
    }
}

private void savePosition() {
    if (mediaPlayer != null && isPrepared) {
        long current = mediaPlayer.getTime();
        long length = mediaPlayer.getLength();

        if (current > 0 && (length <= 0 || current < length - 1500)) {
            savePositionAt(current);
        }
    }
}

private void savePositionAt(long position) {
    if (position > 0) {
        preferences.edit().putLong(positionKey(), position).apply();
    }
}

private String formatTime(long milliseconds) {
    long seconds = Math.max(0, milliseconds / 1000);
    long hours = seconds / 3600;
    long minutes = (seconds % 3600) / 60;
    long remainingSeconds = seconds % 60;

    if (hours > 0) {
        return String.format(Locale.getDefault(),
                "%d:%02d:%02d", hours, minutes, remainingSeconds);
    }

    return String.format(Locale.getDefault(),
            "%02d:%02d", minutes, remainingSeconds);
}

@Override
protected void onPause() {
    savePosition();

    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
        mediaPlayer.pause();
    }

    super.onPause();
}

@Override
protected void onDestroy() {
    handler.removeCallbacks(progressUpdater);

    savePosition();

    if (mediaPlayer != null) {
        mediaPlayer.stop();
        mediaPlayer.detachViews();
        mediaPlayer.release();
        mediaPlayer = null;
    }

    if (libVLC != null) {
        libVLC.release();
        libVLC = null;
    }

    super.onDestroy();
}

}