
package com.openplay.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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

    private static final String TAG = "OpenPlayVLC";

    private LibVLC libVLC;
    private MediaPlayer player;
    private VLCVideoLayout videoLayout;

    private TextView titleText;
    private TextView timeText;
    private TextView statusText;
    private SeekBar seekBar;
    private Button playButton;
    private LinearLayout controls;
    private LinearLayout extraControls;

    private SharedPreferences preferences;
    private String videoUri;
    private String videoName;

    private boolean prepared = false;
    private boolean seeking = false;
    private boolean controlsLocked = false;
    private boolean landscape = false;
    private boolean muted = false;
    private boolean keepScreenAwake = false;
    private boolean released = false;

    private int speedIndex = 2;
    private int ratioIndex = 0;
    private int displayIndex = 0;

    private final float[] speeds = {
            0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f
    };

    private final String[] speedNames = {
            "0.5x", "0.75x", "1.0x",
            "1.25x", "1.5x", "2.0x"
    };

    private final String[] ratios = {
            "Original", "4:3", "16:9", "18:9"
    };

    private final String[] displayModes = {
            "Fit", "Fill", "Stretch", "Zoom"
    };

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final Runnable progressTask = new Runnable() {
        @Override
        public void run() {
            if (released) {
                return;
            }

            if (player != null && prepared) {
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
                    this, "No video URI received",
                    Toast.LENGTH_LONG
            ).show();
            finish();
            return;
        }

        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        createInterface();
        handler.post(progressTask);
        startPlayback();
    }

    private void createInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        titleText = new TextView(this);
        titleText.setText(videoName);
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(15);
        titleText.setMaxLines(2);
        titleText.setPadding(12, 10, 12, 10);
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
        statusText.setText("Opening video...");
        statusText.setTextColor(Color.LTGRAY);
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText);

        timeText = new TextView(this);
        timeText.setText("00:00 / 00:00");
        timeText.setTextColor(Color.WHITE);
        timeText.setGravity(Gravity.CENTER);
        root.addView(timeText);

        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        root.addView(seekBar);

        controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);

        Button back = makeButton("-10s");
        back.setOnClickListener(v -> seekBy(-10000));
        controls.addView(back);

        playButton = makeButton("PLAY");
        playButton.setOnClickListener(v -> togglePlayback());
        controls.addView(playButton);

        Button forward = makeButton("+10s");
        forward.setOnClickListener(v -> seekBy(10000));
        controls.addView(forward);

        root.addView(controls);

        extraControls = new LinearLayout(this);
        extraControls.setGravity(Gravity.CENTER);

        Button rotate = makeButton("ROTATE");
        rotate.setOnClickListener(v -> rotateScreen());
        extraControls.addView(rotate);

        Button ratio = makeButton("RATIO");
        ratio.setOnClickListener(v -> chooseRatio());
        extraControls.addView(ratio);

        Button display = makeButton("FIT/ZOOM");
        display.setOnClickListener(v -> chooseDisplayMode());
        extraControls.addView(display);

        Button audio = makeButton("AUDIO");
        audio.setOnClickListener(v -> chooseAudioTrack());
        extraControls.addView(audio);

        Button subtitles = makeButton("SUBTITLES");
        subtitles.setOnClickListener(v -> chooseSubtitleTrack());
        extraControls.addView(subtitles);

        Button settings = makeButton("SETTINGS");
        settings.setOnClickListener(v -> showSettings());
        extraControls.addView(settings);

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
                        if (player != null && prepared) {
                            long length = player.getLength();
                            if (length > 0) {
                                long target =
                                        length * bar.getProgress() / 1000L;
                                player.setTime(target);
                                savePosition(target);
                            }
                        }
                        seeking = false;
                    }

                    @Override
                    public void onProgressChanged(
                            SeekBar bar, int progress,
                            boolean fromUser) {
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
        params.setMargins(1, 1, 1, 1);
        button.setLayoutParams(params);
        return button;
    }

    private void startPlayback() {
        try {
            released = false;
            prepared = false;
            statusText.setText("Starting VLC...");

            ArrayList<String> options = new ArrayList<>();
            options.add("--no-video-title-show");
            options.add("--avcodec-hw=none");
            options.add("--verbose=2");

            Log.i(TAG, "Starting VLC with hardware decoding disabled");
            Log.i(TAG, "Video URI: " + videoUri);

            libVLC = new LibVLC(this, options);
            player = new MediaPlayer(libVLC);

            player.setEventListener(event ->
                    runOnUiThread(() -> {
                        if (released || player == null) {
                            return;
                        }

                        Log.d(TAG, "VLC event: " + event.type);

                        switch (event.type) {
                            case MediaPlayer.Event.Opening:
                                statusText.setText("Opening video...");
                                Log.i(TAG, "Opening video");
                                break;

                            case MediaPlayer.Event.Buffering:
                                statusText.setText("Buffering...");
                                break;

                            case MediaPlayer.Event.Playing:
                                prepared = true;
                                playButton.setText("PAUSE");
                                statusText.setText("Playing");
                                Log.i(TAG, "Playback started");
                                updateProgress();
                                break;

                            case MediaPlayer.Event.Paused:
                                playButton.setText("PLAY");
                                saveCurrentPosition();
                                statusText.setText("Paused");
                                break;

                            case MediaPlayer.Event.Stopped:
                                playButton.setText("PLAY");
                                Log.i(TAG, "Playback stopped");
                                break;

                            case MediaPlayer.Event.EncounteredError:
                                prepared = false;
                                playButton.setText("RETRY");
                                statusText.setText("VLC playback error");

                                Log.e(
                                        TAG,
                                        "Playback failed for URI: " + videoUri
                                );

                                Toast.makeText(
                                        PlayerActivity.this,
                                        "Playback failed. Check file access and VLC logs.",
                                        Toast.LENGTH_LONG
                                ).show();
                                break;

                            case MediaPlayer.Event.LengthChanged:
                            case MediaPlayer.Event.TimeChanged:
                                updateProgress();
                                break;
                        }
                    })
            );

            player.attachViews(videoLayout, null, false, false);

            Uri uri = Uri.parse(videoUri);
            Media media = new Media(libVLC, uri);

            mediaPlayerSetMedia(media);
            media.release();

            long saved = preferences.getLong(positionKey(), 0L);
            if (saved > 0) {
                handler.postDelayed(() -> {
                    if (player != null && prepared) {
                        player.setTime(saved);
                    }
                }, 1200);
            }

            player.play();

        } catch (Exception e) {
            prepared = false;

            Log.e(TAG, "Unable to start playback", e);

            statusText.setText("Unable to start playback");
            Toast.makeText(
                    this,
                    "Player error: " + e.getClass().getSimpleName()
                            + ": " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void mediaPlayerSetMedia(Media media) {
        player.setMedia(media);
    }

    private void togglePlayback() {
        if (player == null) {
            return;
        }

        try {
            if (player.isPlaying()) {
                player.pause();
            } else if (prepared) {
                player.play();
            } else {
                Toast.makeText(
                        this,
                        "Video is not ready. Reopen the video.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Playback control failed", e);
            Toast.makeText(
                    this, "Playback control failed",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void updateProgress() {
        if (player == null || !prepared) {
            return;
        }

        long current = Math.max(0, player.getTime());
        long length = Math.max(0, player.getLength());

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
        } else if (current > 0) {
            savePosition(current);
        }

        playButton.setText(player.isPlaying() ? "PAUSE" : "PLAY");
    }

    private void seekBy(long offset) {
        if (player == null || !prepared) {
            return;
        }

        long current = Math.max(0, player.getTime());
        long length = player.getLength();
        long target = Math.max(0, current + offset);

        if (length > 0) {
            target = Math.min(target, length);
        }

        player.setTime(target);
        savePosition(target);
        updateProgress();
    }

    private void chooseAudioTrack() {
        if (player == null || !prepared) {
            showNotReady();
            return;
        }

        MediaPlayer.TrackDescription[] tracks = player.getAudioTracks();

        if (tracks == null || tracks.length == 0) {
            showMessage("No audio tracks found in this video.");
            return;
        }

        String[] names = new String[tracks.length];
        int selected = -1;

        for (int i = 0; i < tracks.length; i++) {
            names[i] = tracks[i].name == null
                    ? "Audio track " + (i + 1)
                    : tracks[i].name;

            if (tracks[i].id == player.getAudioTrack()) {
                selected = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Audio track")
                .setSingleChoiceItems(names, selected, (dialog, which) -> {
                    player.setAudioTrack(tracks[which].id);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void chooseSubtitleTrack() {
        if (player == null || !prepared) {
            showNotReady();
            return;
        }

        MediaPlayer.TrackDescription[] tracks = player.getSpuTracks();
        int current = player.getSpuTrack();

        int count = tracks == null ? 0 : tracks.length;
        String[] names = new String[count + 1];
        names[0] = "Subtitles Off";

        int selected = current == -1 ? 0 : -1;

        for (int i = 0; i < count; i++) {
            names[i + 1] = tracks[i].name == null
                    ? "Subtitle " + (i + 1)
                    : tracks[i].name;

            if (tracks[i].id == current) {
                selected = i + 1;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Subtitles")
                .setSingleChoiceItems(names, selected, (dialog, which) -> {
                    if (which == 0) {
                        player.setSpuTrack(-1);
                    } else if (tracks != null && which - 1 < tracks.length) {
                        player.setSpuTrack(tracks[which - 1].id);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void chooseRatio() {
        new AlertDialog.Builder(this)
                .setTitle("Aspect ratio")
                .setSingleChoiceItems(ratios, ratioIndex, (dialog, which) -> {
                    ratioIndex = which;
                    if (player != null) {
                        switch (ratios[which]) {
                            case "4:3":
                                player.setAspectRatio("4:3");
                                break;
                            case "16:9":
                                player.setAspectRatio("16:9");
                                break;
                            case "18:9":
                                player.setAspectRatio("2:1");
                                break;
                            default:
                                player.setAspectRatio(null);
                                break;
                        }
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void chooseDisplayMode() {
        new AlertDialog.Builder(this)
                .setTitle("Display mode")
                .setSingleChoiceItems(displayModes, displayIndex, (dialog, which) -> {
                    displayIndex = which;
                    if (player != null) {
                        switch (displayModes[which]) {
                            case "Fit":
                                player.setScale(0f);
                                player.setAspectRatio(null);
                                break;
                            case "Fill":
                                player.setScale(0f);
                                player.setAspectRatio("16:9");
                                break;
                            case "Stretch":
                                player.setScale(0f);
                                player.setAspectRatio(null);
                                break;
                            case "Zoom":
                                player.setScale(1.5f);
                                break;
                        }
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
        new AlertDialog.Builder(this)
                .setTitle("Playback speed")
                .setSingleChoiceItems(speedNames, speedIndex, (dialog, which) -> {
                    speedIndex = which;
                    if (player != null) {
                        player.setRate(speeds[which]);
                    }
                    dialog.dismiss();
                })
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
                "Keep screen awake",
                "File information"
        };

        new AlertDialog.Builder(this)
                .setTitle("OpenPlay settings")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: chooseSpeed(); break;
                        case 1: chooseRatio(); break;
                        case 2: chooseDisplayMode(); break;
                        case 3: chooseBrightness(); break;
                        case 4: toggleMute(); break;
                        case 5: toggleLock(); break;
                        case 6: toggleKeepScreenAwake(); break;
                        case 7: showFileInfo(); break;
                    }
                })
                .show();
    }

    private void chooseBrightness() {
        SeekBar bar = new SeekBar(this);
        bar.setMax(100);

        float initial = getWindow().getAttributes().screenBrightness;
        if (initial < 0) {
            initial = 0.5f;
        }
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

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                WindowManager.LayoutParams p = getWindow().getAttributes();
                p.screenBrightness = Math.max(0.01f, progress / 100f);
                getWindow().setAttributes(p);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        dialog.show();
    }

    private void toggleMute() {
        if (player == null) {
            return;
        }

        muted = !muted;
        player.setVolume(muted ? 0 : 100);

        showMessage(muted ? "Muted" : "Sound on");
    }

    private void toggleLock() {
        controlsLocked = !controlsLocked;
        controls.setVisibility(controlsLocked ? View.GONE : View.VISIBLE);
        extraControls.setVisibility(controlsLocked ? View.GONE : View.VISIBLE);

        showMessage(controlsLocked
                ? "Controls locked. Tap the video to unlock."
                : "Controls unlocked");
    }

    private void toggleKeepScreenAwake() {
        keepScreenAwake = !keepScreenAwake;

        if (keepScreenAwake) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        showMessage(keepScreenAwake
                ? "Screen will stay awake"
                : "Keep screen awake disabled");
    }

    private void showFileInfo() {
        long length = player == null ? 0 : Math.max(0, player.getLength());

        new AlertDialog.Builder(this)
                .setTitle("File information")
                .setMessage(
                        "Name: " + videoName
                                + "\nDuration: " + formatTime(length)
                                + "\nURI: " + videoUri
                )
                .setPositiveButton("OK", null)
                .show();
    }

    private void toggleControls() {
        if (controlsLocked) {
            controlsLocked = false;
            controls.setVisibility(View.VISIBLE);
            extraControls.setVisibility(View.VISIBLE);
            timeText.setVisibility(View.VISIBLE);
            seekBar.setVisibility(View.VISIBLE);
            titleText.setVisibility(View.VISIBLE);
            statusText.setVisibility(View.VISIBLE);
            return;
        }

        int visibility = controls.getVisibility() == View.VISIBLE
                ? View.GONE : View.VISIBLE;

        controls.setVisibility(visibility);
        extraControls.setVisibility(visibility);
        timeText.setVisibility(visibility);
        seekBar.setVisibility(visibility);
        titleText.setVisibility(visibility);
        statusText.setVisibility(visibility);
    }

    private void showNotReady() {
        showMessage("Wait until playback starts.");
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String positionKey() {
        return "position_" + videoUri;
    }

    private void saveCurrentPosition() {
        if (player != null && prepared) {
            savePosition(player.getTime());
        }
    }

    private void savePosition(long position) {
        if (position > 0) {
            preferences.edit().putLong(positionKey(), position).apply();
        }
    }

    private String formatTime(long milliseconds) {
        long seconds = Math.max(0, milliseconds / 1000);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remaining = seconds % 60;

        if (hours > 0) {
            return String.format(
                    Locale.getDefault(), "%d:%02d:%02d",
                    hours, minutes, remaining
            );
        }

        return String.format(
                Locale.getDefault(), "%02d:%02d",
                minutes, remaining
        );
    }

    @Override
    protected void onPause() {
        saveCurrentPosition();

        if (player != null && player.isPlaying()) {
            player.pause();
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        released = true;
        handler.removeCallbacks(progressTask);
        saveCurrentPosition();

        if (player != null) {
            player.stop();
            player.detachViews();
            player.release();
            player = null;
        }

        if (libVLC != null) {
            libVLC.release();
            libVLC = null;
        }

        super.onDestroy();
    }
}
