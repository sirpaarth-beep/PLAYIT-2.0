
package com.openplay.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends Activity {

    private static final int MEDIA_PERMISSION_REQUEST = 100;
    private LinearLayout videoList;
    private TextView statusText;

    private final ArrayList<VideoItem> videos = new ArrayList<>();

    private static class VideoItem {
        long id;
        String name;
        long duration;
        Uri uri;

        VideoItem(long id, String name, long duration, Uri uri) {
            this.id = id;
            this.name = name;
            this.duration = duration;
            this.uri = uri;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(18, 18, 18));
        getWindow().setNavigationBarColor(Color.BLACK);

        createInterface();

        if (hasMediaPermission()) {
            loadVideos();
        } else {
            requestMediaPermission();
        }
    }

    private void createInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 18, 18));
        root.setPadding(18, 16, 18, 8);

        TextView title = new TextView(this);
        title.setText("OpenPlay");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(0, 8, 0, 16);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Your videos");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(16);
        root.addView(subtitle);

        statusText = new TextView(this);
        statusText.setTextColor(Color.LTGRAY);
        statusText.setTextSize(14);
        statusText.setPadding(0, 12, 0, 12);
        root.addView(statusText);

        Button refreshButton = new Button(this);
        refreshButton.setText("Refresh videos");
        refreshButton.setOnClickListener(v -> {
            if (hasMediaPermission()) {
                loadVideos();
            } else {
                requestMediaPermission();
            }
        });
        root.addView(refreshButton);

        ScrollView scrollView = new ScrollView(this);

        videoList = new LinearLayout(this);
        videoList.setOrientation(LinearLayout.VERTICAL);
        videoList.setPadding(0, 8, 0, 16);

        scrollView.addView(videoList);

        root.addView(
                scrollView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        setContentView(root);
    }

    private boolean hasMediaPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(
                    Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED;
        }

        return checkSelfPermission(
                Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestMediaPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.READ_MEDIA_VIDEO
                    },
                    MEDIA_PERMISSION_REQUEST
            );
        } else {
            requestPermissions(
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    },
                    MEDIA_PERMISSION_REQUEST
            );
        }
    }

    private void loadVideos() {
        videos.clear();
        videoList.removeAllViews();

        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION
        };

        try (Cursor cursor = getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Video.Media.DATE_ADDED + " DESC"
        )) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(
                        MediaStore.Video.Media._ID
                );
                int nameColumn = cursor.getColumnIndexOrThrow(
                        MediaStore.Video.Media.DISPLAY_NAME
                );
                int durationColumn = cursor.getColumnIndexOrThrow(
                        MediaStore.Video.Media.DURATION
                );

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String name = cursor.getString(nameColumn);
                    long duration = cursor.getLong(durationColumn);

                    Uri uri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id
                    );

                    videos.add(new VideoItem(id, name, duration, uri));
                }
            }
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Unable to load videos",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        statusText.setText(videos.size() + " videos found");

        if (videos.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No videos found on this device.");
            empty.setTextColor(Color.LTGRAY);
            empty.setPadding(0, 24, 0, 24);
            videoList.addView(empty);
            return;
        }

        for (VideoItem item : videos) {
            addVideoRow(item);
        }
    }

    private void addVideoRow(VideoItem item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(12, 14, 12, 14);
        row.setBackgroundColor(Color.rgb(32, 32, 32));

        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        rowParams.setMargins(0, 0, 0, 8);
        row.setLayoutParams(rowParams);

        TextView name = new TextView(this);
        name.setText(item.name);
        name.setTextColor(Color.WHITE);
        name.setTextSize(16);
        name.setMaxLines(2);
        row.addView(name);

        TextView duration = new TextView(this);
        duration.setText("Duration: " + formatDuration(item.duration));
        duration.setTextColor(Color.LTGRAY);
        duration.setTextSize(13);
        duration.setPadding(0, 6, 0, 0);
        row.addView(duration);

        row.setOnClickListener(v -> openVideo(item));

        videoList.addView(row);
    }

    private String formatDuration(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format(
                    java.util.Locale.getDefault(),
                    "%d:%02d:%02d",
                    hours, minutes, seconds
            );
        }

        return String.format(
                java.util.Locale.getDefault(),
                "%d:%02d",
                minutes, seconds
        );
    }

    private void openVideo(VideoItem item) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.setData(item.uri);
        intent.putExtra("video_uri", item.uri.toString());
        intent.putExtra("video_name", item.name);
        intent.putExtra("video_id", item.id);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults
        );

        if (requestCode == MEDIA_PERMISSION_REQUEST) {
            if (hasMediaPermission()) {
                loadVideos();
            } else {
                statusText.setText(
                        "Media permission is needed to show your videos."
                );
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (videoList != null && hasMediaPermission()) {
            loadVideos();
        }
    }
}
