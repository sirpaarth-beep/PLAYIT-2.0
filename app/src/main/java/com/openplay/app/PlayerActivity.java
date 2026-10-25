package com.openplay.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
private TextView modeText;
private Button playButton;
private SeekBar seekBar;
private SharedPreferences preferences;

private String videoUri;
private String videoName;

private boolean prepared = false;
private boolean seeking = false;
private boolean restoring = true;
private long savedPosition = 0;

private int rotation = 0;
private int aspectIndex = 0;
private int displayIndex = 0;

private final String[] aspects = {
        "Original", "4:3", "16:9", "18:9"
};

private final String[] displayModes = {
        "Fit", "Fill", "Stretch", "Zoom"
};

private final Handler handler =
        new Handler(Looper.getMainLooper());

private final Runnable updater = new Runnable() {
    @Override
    public void run() {
        if (mediaPlayer != null && prepared) {
            updateProgress();
        }
        handler.postDelayed(this, 500);
    }
};

@Override
protected void onCreate(Bundle state) {
    super.onCreate(state);

    preferences = getSharedPreferences(
            "OpenPlayPlayback", MODE_PRIVATE);

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
    title.setPadding(12, 8, 12, 8);
    root.addView(title);

    videoLayout = new VLCVideoLayout(this);
    root.addView(videoLayout, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

    timeText = new TextView(this);
    timeText.setText("00:00 / 00:00");
    timeText.setTextColor(Color.WHITE);
    timeText.setGravity(Gravity.CENTER);
    root.addView(timeText);

    seekBar = new SeekBar(this);
    seekBar.setMax(1000);
    root.addView(seekBar);

    seekBar.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onStartTrackingTouch(SeekBar bar) {
                    seeking = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar bar) {
                    if (mediaPlayer != null && prepared) {
                        long length = mediaPlayer.getLength();
                        if (length > 0) {
                            long target =
                                    length * bar.getProgress() / 1000L;
                            mediaPlayer.setTime(target);
                            savePosition(target);
                        }
                    }
                    seeking = false;
                }

                @Override
                public void onProgressChanged(
                        SeekBar bar, int progress, boolean fromUser) {
                }
            });

    LinearLayout controls = new LinearLayout(this);
    controls.setGravity(Gravity.CENTER);

    Button back = new Button(this);
    back.setText("-10s");
    back.setOnClickListener(v -> seekBy(-10000));
    controls.addView(back);

    playButton = new Button(this);
    playButton.setText("LOADING");
    playButton.setOnClickListener(v -> togglePlayback());
    controls.addView(playButton);

    Button forward = new Button(this);
    forward.setText("+10s");
    forward.setOnClickListener(v -> seekBy(10000));
    controls.addView(forward);

    root.addView(controls);

    LinearLayout options = new LinearLayout(this);
    options.setGravity(Gravity.CENTER);

    Button rotate = new Button(this);
    rotate.setText("Rotate");
    rotate.setOnClickListener(v -> rotateVideo());
    options.addView(rotate);

    Button ratio = new Button(this);
    ratio.setText("Ratio");
    ratio.setOnClickListener(v -> changeAspect());
    options.addView(ratio);

    Button display = new Button(this);
    display.setText("Fit / Zoom");
    display.setOnClickListener(v -> changeDisplayMode());
    options.addView(display);

    root.addView(options);

    modeText = new TextView(this);
    modeText.setText("Ratio: Original | Display: Fit | Rotation: 0°");
    modeText.setTextColor(Color.LTGRAY);
    modeText.setGravity(Gravity.CENTER);
    root.addView(modeText);

    setContentView(root);
}

private void startPlayer() {
    try {
        ArrayList<String> options = new ArrayList<>();
        options.add("--no-video-title-show");

        libVLC = new LibVLC(this, options);
        mediaPlayer = new MediaPlayer(libVLC);

        mediaPlayer.setEventListener(event ->
                runOnUiThread(() -> handleEvent(event)));

        mediaPlayer.attachViews(videoLayout, null, false, false);

        savedPosition = preferences.getLong(positionKey(), 0L);

        Media media = new Media(libVLC, Uri.parse(videoUri));
        mediaPlayer.setMedia(media);
        media.release();

        mediaPlayer.play();
        handler.removeCallbacks(updater);
        handler.post(updater);

    } catch (Exception e) {
        Log.e("OpenPlay", "Player initialization failed", e);
        Toast.makeText(this, "Unable to initialize player",
                Toast.LENGTH_LONG).show();
    }
}

private void handleEvent(MediaPlayer.Event event) {
    switch (event.type) {
        case MediaPlayer.Event.Playing:
            prepared = true;
            playButton.setText("Pause");

            if (restoring) {
                restoring = false;
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

        case MediaPlayer.Event.Paused:
        case MediaPlayer.Event.Stopped:
            playButton.setText("Play");
            break;

        case MediaPlayer.Event.EncounteredError:
            prepared = false;
            playButton.setText("Retry");
            Log.e("OpenPlay", "VLC playback error: " + videoUri);
            Toast.makeText(this,
                    "Playback failed. Check file or codec compatibility.",
                    Toast.LENGTH_LONG).show();
            break;

        case MediaPlayer.Event.EndReached:
            prepared = false;
            preferences.edit().remove(positionKey()).apply();
            playButton.setText("Play");
            break;

        default:
            break;
    }
}

private void togglePlayback() {
    if (mediaPlayer == null) {
        return;
    }

    if (mediaPlayer.isPlaying()) {
        mediaPlayer.pause();
        saveCurrentPosition();
    } else {
        mediaPlayer.play();
    }
}

private void seekBy(long offset) {
    if (mediaPlayer == null || !prepared) {
        return;
    }

    long current = Math.max(0, mediaPlayer.getTime());
    long length = Math.max(0, mediaPlayer.getLength());
    long target = Math.max(0, current + offset);

    if (length > 0) {
        target = Math.min(target, length);
    }

    mediaPlayer.setTime(target);
    savePosition(target);
    updateProgress();
}

private void updateProgress() {
    if (mediaPlayer == null) {
        return;
    }

    long current = Math.max(0, mediaPlayer.getTime());
    long length = Math.max(0, mediaPlayer.getLength());

    if (!seeking && length > 0) {
        seekBar.setProgress((int) Math.min(
                1000, current * 1000L / length));
    }

    timeText.setText(formatTime(current) + " / " + formatTime(length));

    if (length > 0 && current >= length - 1500) {
        preferences.edit().remove(positionKey()).apply();
    }

    playButton.setText(
            mediaPlayer.isPlaying() ? "Pause" : "Play");
}

private void rotateVideo() {
    rotation = (rotation + 90) % 360;

    if (mediaPlayer != null) {
        mediaPlayer.setVideoRotation(
                MediaPlayer.Rotation.values()[rotation / 90]);
    }

    updateModeText();
}

private void changeAspect() {
    aspectIndex = (aspectIndex + 1) % aspects.length;

    if (mediaPlayer != null) {
        switch (aspects[aspectIndex]) {
            case "4:3":
                mediaPlayer.setAspectRatio("4:3");
                break;
            case "16:9":
                mediaPlayer.setAspectRatio("16:9");
                break;
            case "18:9":
                mediaPlayer.setAspectRatio("2:1");
                break;
            default:
                mediaPlayer.setAspectRatio(null);
                break;
        }
    }

    updateModeText();
}

private void changeDisplayMode() {
    displayIndex = (displayIndex + 1) % displayModes.length;

    if (mediaPlayer != null) {
        switch (displayModes[displayIndex]) {
            case "Fit":
                mediaPlayer.setScale(0f);
                mediaPlayer.setAspectRatio(null);
                break;
            case "Fill":
                mediaPlayer.setScale(0f);
                mediaPlayer.setAspectRatio("16:9");
                break;
            case "Stretch":
                mediaPlayer.setScale(0f);
                mediaPlayer.setAspectRatio(null);
                break;
            case "Zoom":
                mediaPlayer.setScale(1.5f);
                break;
        }
    }

    updateModeText();
}

private void updateModeText() {
    modeText.setText("Ratio: " + aspects[aspectIndex]
            + " | Display: " + displayModes[displayIndex]
            + " | Rotation: " + rotation + "°");
}

private String positionKey() {
    return "position_" + videoUri;
}

private void saveCurrentPosition() {
    if (mediaPlayer != null && prepared) {
        savePosition(mediaPlayer.getTime());
    }
}

private void savePosition(long position) {
    if (position > 0) {
        preferences.edit()
                .putLong(positionKey(), position)
                .apply();
    }
}

private String formatTime(long milliseconds) {
    long seconds = Math.max(0, milliseconds / 1000);
    long hours = seconds / 3600;
    long minutes = (seconds % 3600) / 60;
    long remaining = seconds % 60;

    if (hours > 0) {
        return String.format(Locale.getDefault(),
                "%d:%02d:%02d", hours, minutes, remaining);
    }

    return String.format(Locale.getDefault(),
            "%02d:%02d", minutes, remaining);
}

@Override
protected void onPause() {
    saveCurrentPosition();

    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
        mediaPlayer.pause();
    }

    super.onPause();
}

@Override
protected void onDestroy() {
    handler.removeCallbacks(updater);
    saveCurrentPosition();

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