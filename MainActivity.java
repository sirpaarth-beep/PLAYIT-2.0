package com.openplay.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

/** Native OpenPlay library screen. Playback is handled by PlayerActivity/LibVLC. */
public class MainActivity extends Activity {
    private static final int REQUEST_MEDIA = 41;
    private final int bg = Color.rgb(15, 17, 21);
    private final int panel = Color.rgb(28, 31, 37);
    private final int accent = Color.rgb(40, 205, 125);
    private LinearLayout list;
    private TextView status;
    private final ArrayList<VideoItem> videos = new ArrayList<>();

    private static class VideoItem {
        long id, duration, size;
        String name;
        Uri uri;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildScreen();
        if (hasVideoPermission()) loadVideos(); else requestVideoPermission();
    }

    private String[] mediaPermissions() {
        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14+ can grant access to only user-selected videos.
            return new String[]{
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            };
        }
        if (Build.VERSION.SDK_INT >= 33) {
            return new String[]{Manifest.permission.READ_MEDIA_VIDEO};
        }
        if (Build.VERSION.SDK_INT >= 23) {
            return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        }
        return new String[0];
    }

    private boolean hasVideoPermission() {
        if (Build.VERSION.SDK_INT < 23) return true;
        if (Build.VERSION.SDK_INT >= 34) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED;
        }
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_VIDEO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestVideoPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(mediaPermissions(), REQUEST_MEDIA);
        } else {
            loadVideos();
        }
    }

    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(20), dp(20), dp(12));
        header.setBackgroundColor(bg);
        TextView brand = text("OPENPLAY", 12, accent);
        brand.setLetterSpacing(0.18f);
        header.addView(brand);
        TextView title = text("Your videos", 28, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(5);
        header.addView(title, tp);
        TextView subtitle = text("Local library · Private by design", 13, Color.LTGRAY);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(4);
        header.addView(subtitle, sp);
        root.addView(header);

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(16), dp(4), dp(16), dp(10));
        Button refresh = new Button(this);
        refresh.setText("Refresh");
        refresh.setTextColor(Color.WHITE);
        refresh.setAllCaps(false);
        refresh.setOnClickListener(v -> { if (hasVideoPermission()) loadVideos(); else requestVideoPermission(); });
        actions.addView(refresh, new LinearLayout.LayoutParams(-2, dp(44)));
        Button privacy = new Button(this);
        privacy.setText("🔒 Privacy Folder");
        privacy.setTextColor(Color.WHITE);
        privacy.setAllCaps(false);
        privacy.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Privacy Folder")
                .setMessage("The encrypted vault is planned for the next development stage. Your original videos have not been moved or deleted.")
                .setPositiveButton("OK", null).show());
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-2, dp(44));
        pp.leftMargin = dp(8);
        actions.addView(privacy, pp);
        root.addView(actions);

        status = text("Looking for videos…", 14, Color.LTGRAY);
        status.setPadding(dp(20), dp(10), dp(20), dp(10));
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(4), dp(12), dp(20));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private void loadVideos() {
        videos.clear();
        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE
        };
        try (Cursor c = getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                MediaStore.Video.Media.DATE_ADDED + " DESC")) {
            if (c != null) {
                int idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                int durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                int sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
                while (c.moveToNext()) {
                    VideoItem item = new VideoItem();
                    item.id = c.getLong(idCol);
                    item.name = c.getString(nameCol);
                    item.duration = c.getLong(durCol);
                    item.size = c.getLong(sizeCol);
                    item.uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, item.id);
                    videos.add(item);
                }
            }
        } catch (SecurityException e) {
            status.setText("Video permission is required to show your library.");
            return;
        } catch (Exception e) {
            status.setText("Could not read videos: " + e.getMessage());
            return;
        }
        renderVideos();
    }

    private void renderVideos() {
        list.removeAllViews();
        status.setText(videos.size() + (videos.size() == 1 ? " video" : " videos"));
        if (videos.isEmpty()) {
            TextView empty = text("No videos found. Add a video to your device, then tap Refresh.", 15, Color.LTGRAY);
            empty.setPadding(dp(12), dp(22), dp(12), dp(22));
            list.addView(empty);
            return;
        }
        for (int i = 0; i < videos.size(); i++) {
            final int index = i;
            VideoItem item = videos.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(12), dp(12));
            row.setBackgroundColor(panel);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.bottomMargin = dp(8);
            list.addView(row, rp);

            TextView play = text("▶", 22, accent);
            play.setGravity(Gravity.CENTER);
            row.addView(play, new LinearLayout.LayoutParams(dp(48), dp(54)));

            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setPadding(dp(10), 0, dp(4), 0);
            row.addView(details, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView name = text(item.name == null ? "Video" : item.name, 15, Color.WHITE);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            details.addView(name);
            TextView meta = text(formatDuration(item.duration) + "  ·  " + formatSize(item.size), 12, Color.LTGRAY);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
            mp.topMargin = dp(5);
            details.addView(meta, mp);
            row.setOnClickListener(v -> playFrom(index));
        }
    }

    private void playFrom(int index) {
        ArrayList<String> uriList = new ArrayList<>();
        ArrayList<String> nameList = new ArrayList<>();
        for (VideoItem item : videos) {
            uriList.add(item.uri.toString());
            nameList.add(item.name == null ? "Video" : item.name);
        }
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("uris", uriList.toArray(new String[0]));
        intent.putExtra("names", nameList.toArray(new String[0]));
        intent.putExtra("index", index);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open this video", Toast.LENGTH_SHORT).show();
        }
    }

    private String formatDuration(long ms) {
        long s = Math.max(0, ms / 1000), h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
                : String.format(Locale.US, "%d:%02d", m, sec);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024L * 1024L) return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MEDIA) {
            if (hasVideoPermission()) {
                loadVideos();
            } else {
                status.setText("Video access is needed to display your library. Tap Refresh to try again.");
            }
        }
    }
}
