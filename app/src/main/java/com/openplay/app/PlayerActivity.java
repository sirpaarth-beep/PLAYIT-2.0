
package com.openplay.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
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

    private TextView titleText;
    private TextView timeText;
    private TextView statusText;

    private SeekBar seekBar;
    private Button playButton;
    private Button settingsButton;

    private SharedPreferences preferences;
    private String videoUri;
    private String videoName;

    private boolean prepared = false;
    private boolean seeking = false;
    private boolean controlsLocked = false;
    private boolean landscape = false;
    private boolean nightMode = false;

    private int ratioIndex = 0;
    private int displayIndex = 0;
    private int speedIndex = 1;

    private final String[] ratios = {
            "Original", "4:3", "16:9", "18:9"
    };

    private final String[] displayModes = {
            "Fit", "Fill", "Stretch", "Zoom"
    };

    private final float[] speeds = {
            0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f
    };

    private LinearLayout root;
    private LinearLayout controls;
    private LinearLayout extraControls;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && prepared) {
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
            videoName = "OpenPlay";
        }

        if (videoUri == null) {
            Toast.makeText(
                    this, "Video could not be opened",
                    Toast.LENGTH_LONG
            ).show();
            finish();
            return;
        }

        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        createInterface();
        startPlayer();
    }

    private void createInterface() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        titleText = new TextView(this);
        titleText.setText(videoName);
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(15);
        titleText.setMaxLines(2);
        titleText.setPadding(12, 12, 12, 12);
        root.addView(titleText);

        videoLayout = new VLCVideoLayout(this);
        root.addView(
                videoLayout,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0, 1
                )
        );

        statusText = new TextView(this);
        statusText.setText("Loading video...");
        statusText.setTextColor(Color.LTGRAY);
        statusText.setTextSize(12);
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText);

        timeText = new TextView(this);
        timeText.setText("00:00 / 00:00");
        timeText.setTextColor(Color.WHITE);
        timeText.setGravity(Gravity.CENTER);
        timeText.setPadding(8, 5, 8, 2);
        root.addView(timeText);

        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        root.addView(seekBar);

        controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(4, 2, 4, 2);

        Button back10 = makeButton("-10s");
        back10.setOnClickListener(v -> seekRelative(-10000));
        controls.addView(back10);

        playButton = makeButton("PLAY");
        playButton.setOnClickListener(v -> togglePlayback());
        controls.addView(playButton);

        Button forward10 = makeButton("+10s");
        forward10.setOnClickListener(v -> seekRelative(10000));
        controls.addView(forward10);

        root.addView(controls);

        extraControls = new LinearLayout(this);
        extraControls.setGravity(Gravity.CENTER);
        extraControls.setPadding(4, 2, 4, 8);

        Button rotateButton = makeButton("ROTATE");
        rotateButton.setOnClickListener(v -> rotateScreen());
        extraControls.addView(rotateButton);

        Button ratioButton = makeButton("RATIO");
        ratioButton.setOnClickListener(v -> chooseRatio());
        extraControls.addView(ratioButton);

        Button displayButton = makeButton("FIT / ZOOM");
        displayButton.setOnClickListener(v -> chooseDisplayMode());
        extraControls.addView(displayButton);

        settingsButton = makeButton("SETTINGS");
        settingsButton.setOnClickListener(v -> showSettings());
        extraControls.addView(settingsButton);

        root.addView(extraControls);
        setContentView(root);

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
                            SeekBar bar, int progress,
                            boolean fromUser
                    ) {
                    }
                }
        );

        videoLayout.setOnClickListener(v -> toggleControls());
    }

    private Button makeButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(10);
        button.setAllCaps(false);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                );

        params.setMargins(2, 2, 2, 2);
        button.setLayoutParams(params);
        return button;
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
                                prepared = true;
                                playButton.setText("PAUSE");
                                statusText.setText("Playing");
                                updateProgress();
                                break;

                            case MediaPlayer.Event.Paused:
                                playButton.setText("PLAY");
                                saveCurrentPosition();
                                break;

                            case MediaPlayer.Event.Stopped:
                                playButton.setText("PLAY");
                                break;

                            case MediaPlayer.Event.LengthChanged:
                            case MediaPlayer.Event.TimeChanged:
                                updateProgress();
                                break;

                            case MediaPlayer.Event.EncounteredError:
                                prepared = false;
                                statusText.setText("Playback failed");
                                playButton.setText("RETRY");
                                Toast.makeText(
                                        PlayerActivity.this,
                                        "VLC could not decode this video. Try another decoder or file.",
                                        Toast.LENGTH_LONG
                                ).show();
                                break;
                        }
                    })
            );

            mediaPlayer.attachViews(
                    videoLayout, null, false, false
            );

            long savedPosition = preferences.getLong(
                    positionKey(), 0L
            );

            Media media = new Media(
                    libVLC, Uri.parse(videoUri)
            );

            mediaPlayer.setMedia(media);
            media.release();
            mediaPlayer.play();

            if (savedPosition > 0) {
                handler.postDelayed(() -> {
                    if (mediaPlayer != null && prepared) {
                        mediaPlayer.setTime(savedPosition);
                    }
                }, 1200);
            }

            handler.post(progressUpdater);

        } catch (Exception e) {
            statusText.setText("Unable to start player");
            Toast.makeText(
                    this,
                    "Player error: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void togglePlayback() {
        if (mediaPlayer == null) return;

        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            } else if (prepared) {
                mediaPlayer.play();
            } else {
                startPlayer();
            }
        } catch (Exception e) {
            Toast.makeText(
                    this, "Unable to change playback",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void updateProgress() {
        if (mediaPlayer == null) return;

        long current = Math.max(0, mediaPlayer.getTime());
        long length = Math.max(0, mediaPlayer.getLength());

        if (!seeking && length > 0) {
            seekBar.setProgress((int) Math.min(
                    1000, current * 1000L / length
            ));
        }

        timeText.setText(
                formatTime(current) + " / " + formatTime(length)
        );

        if (length > 0 && current >= length - 1500) {
            preferences.edit().remove(positionKey()).apply();
        }

        if (mediaPlayer.isPlaying()) {
            playButton.setText("PAUSE");
        }
    }

    private void seekRelative(long offset) {
        if (mediaPlayer == null || !prepared) return;

        long current = Math.max(0, mediaPlayer.getTime());
        long length = mediaPlayer.getLength();
        long target = Math.max(0, current + offset);

        if (length > 0) target = Math.min(target, length);

        mediaPlayer.setTime(target);
        savePosition(target);
        updateProgress();
    }

    private void chooseRatio() {
        new AlertDialog.Builder(this)
                .setTitle("Aspect ratio")
                .setSingleChoiceItems(
                        ratios, ratioIndex,
                        (dialog, which) -> {
                            ratioIndex = which;
                            applyRatio();
                            dialog.dismiss();
                        }
                )
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyRatio() {
        if (mediaPlayer == null) return;

        switch (ratios[ratioIndex]) {
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

        statusText.setText("Ratio: " + ratios[ratioIndex]);
    }

    private void chooseDisplayMode() {
        new AlertDialog.Builder(this)
                .setTitle("Display mode")
                .setSingleChoiceItems(
                        displayModes, displayIndex,
                        (dialog, which) -> {
                            displayIndex = which;
                            applyDisplayMode();
                            dialog.dismiss();
                        }
                )
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyDisplayMode() {
        if (mediaPlayer == null) return;

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

        statusText.setText(
                "Display: " + displayModes[displayIndex]
        );
    }

    private void rotateScreen() {
        landscape = !landscape;

        setRequestedOrientation(
                landscape
                        ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        );
    }

    private void chooseSpeed() {
        String[] labels = {
                "0.5x", "0.75x", "1.0x",
                "1.25x", "1.5x", "2.0x"
        };

        new AlertDialog.Builder(this)
                .setTitle("Playback speed")
                .setSingleChoiceItems(
                        labels, speedIndex,
                        (dialog, which) -> {
                            speedIndex = which;
                            if (mediaPlayer != null) {
                                mediaPlayer.setRate(speeds[which]);
                            }
                            dialog.dismiss();
                        }
                )
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSettings() {
        String[] items = {
                "Playback speed",
                "Aspect ratio",
                "Display mode",
                "Brightness",
                "Mute / unmute",
                "Screen lock",
                "Night mode",
                "Cut video (open editor)",
                "File information"
        };

        new AlertDialog.Builder(this)
                .setTitle("OpenPlay settings")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            chooseSpeed();
                            break;
                        case 1:
                            chooseRatio();
                            break;
                        case 2:
                            chooseDisplayMode();
                            break;
                        case 3:
                            chooseBrightness();
                            break;
                        case 4:
                            toggleMute();
                            break;
                        case 5:
                            toggleLock();
                            break;
                        case 6:
                            toggleNightMode();
                            break;
                        case 7:
                            openVideoEditor();
                            break;
                        case 8:
                            showFileInfo();
                            break;
                    }
                })
                .show();
    }

    private void chooseBrightness() {
        SeekBar bar = new SeekBar(this);
        bar.setMax(100);

        WindowManager.LayoutParams params =
                getWindow().getAttributes();

        float initial = params.screenBrightness < 0
                ? 0.5f : params.screenBrightness;

        bar.setProgress((int) (initial * 100));

        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(24, 20, 24, 10);
        layout.addView(
                bar,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Brightness")
                .setView(layout)
                .setPositiveButton("Done", null)
                .create();

        bar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar, int progress,
                            boolean fromUser
                    ) {
                        WindowManager.LayoutParams p =
                                getWindow().getAttributes();
                        p.screenBrightness =
                                Math.max(0.01f, progress / 100f);
                        getWindow().setAttributes(p);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                }
        );

        dialog.show();
    }

    private void toggleMute() {
        if (mediaPlayer == null) return;

        boolean muted = mediaPlayer.isMute();
        mediaPlayer.setMute(!muted);

        Toast.makeText(
                this, !muted ? "Muted" : "Sound on",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void toggleLock() {
        controlsLocked = !controlsLocked;
        controls.setVisibility(
                controlsLocked ? View.GONE : View.VISIBLE
        );
        extraControls.setVisibility(
                controlsLocked ? View.GONE : View.VISIBLE
        );
        settingsButton.setEnabled(!controlsLocked);

        Toast.makeText(
                this,
                controlsLocked ? "Controls locked" : "Controls unlocked",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void toggleNightMode() {
        nightMode = !nightMode;

        if (nightMode) {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
            Toast.makeText(
                    this,
                    "Night mode enabled (screen stays awake)",
                    Toast.LENGTH_SHORT
            ).show();
        } else {
            getWindow().clearFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
            Toast.makeText(
                    this, "Night mode disabled",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void openVideoEditor() {
        try {
            Intent intent = new Intent(Intent.ACTION_EDIT);
            intent.setDataAndType(
                    Uri.parse(videoUri), "video/*"
            );
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(
                    intent, "Open video editor"
            ));
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "No compatible video editor is installed",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void showFileInfo() {
        long length = mediaPlayer == null
                ? 0 : Math.max(0, mediaPlayer.getLength());

        String info =
                "Name: " + videoName +
                "\nDuration: " + formatTime(length) +
                "\nURI: " + videoUri +
                "\n\nResolution and codec information are not available yet.";

        new AlertDialog.Builder(this)
                .setTitle("File information")
                .setMessage(info)
                .setPositiveButton("OK", null)
                .show();
    }

    private void toggleControls() {
        if (controlsLocked) return;

        int visibility = controls.getVisibility() == View.VISIBLE
                ? View.GONE : View.VISIBLE;

        controls.setVisibility(visibility);
        extraControls.setVisibility(visibility);
        timeText.setVisibility(visibility);
        seekBar.setVisibility(visibility);
        titleText.setVisibility(visibility);
        statusText.setVisibility(visibility);
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
            return String.format(
                    Locale.getDefault(),
                    "%d:%02d:%02d",
                    hours, minutes, remaining
            );
        }

        return String.format(
                Locale.getDefault(),
                "%02d:%02d",
                minutes, remaining
        );
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
        handler.removeCallbacks(progressUpdater);
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
