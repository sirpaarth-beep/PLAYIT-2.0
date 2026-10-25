package com.openplay.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
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

/** Native player built on libVLC: plays MKV, AC3, EAC3, DTS, HEVC and almost every other format. */
public class PlayerActivity extends Activity {
    private LibVLC lib;
    private MediaPlayer mp;
    private VLCVideoLayout vl;
    private LinearLayout top, bottom;
    private SeekBar seek;
    private TextView cur, dur, title, info;
    private Button playBtn, speedBtn;
    private String[] uris, names;
    private int idx;
    private long len = 0;
    private long resumePos = 0, lastSaved = 0;
    private boolean userSeeking = false, landscape = true, opened = false;
    private int scaleI = 0, rateI = 0;
    private final float[] rates = {1f, 1.25f, 1.5f, 2f, 0.5f, 0.75f};
    private final MediaPlayer.ScaleType[] scales = {
            MediaPlayer.ScaleType.SURFACE_BEST_FIT, MediaPlayer.ScaleType.SURFACE_FIT_SCREEN,
            MediaPlayer.ScaleType.SURFACE_FILL, MediaPlayer.ScaleType.SURFACE_16_9,
            MediaPlayer.ScaleType.SURFACE_4_3};
    private final String[] scaleNames = {"Fit", "Crop", "Stretch", "16:9", "4:3"};
    private final Handler h = new Handler(Looper.getMainLooper());
    private SharedPreferences sp;
    private AudioManager am;
    private int gMode = 0;
    private long gStartT = 0, pendingSeek = -1;
    private float gBr = 0.5f, gVol = 0f;
    private final Runnable hideCtl = new Runnable() {
        @Override
        public void run() {
            setCtl(false);
        }
    };
    private final Runnable hideInfo = new Runnable() {
        @Override
        public void run() {
            info.setVisibility(View.GONE);
        }
    };

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        long hh = s / 3600, mm = (s % 3600) / 60, ss = s % 60;
        return hh > 0 ? String.format(Locale.US, "%d:%02d:%02d", hh, mm, ss)
                : String.format(Locale.US, "%d:%02d", mm, ss);
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    private Button btn(String t, View.OnClickListener l) {
        Button x = new Button(this);
        x.setText(t);
        x.setTextColor(Color.WHITE);
        x.setBackgroundColor(Color.TRANSPARENT);
        x.setAllCaps(false);
        x.setMinWidth(0);
        x.setMinimumWidth(0);
        x.setPadding(dp(6), 0, dp(6), 0);
        x.setOnClickListener(l);
        return x;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sp = getSharedPreferences("pos", MODE_PRIVATE);
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        uris = getIntent().getStringArrayExtra("uris");
        names = getIntent().getStringArrayExtra("names");
        idx = getIntent().getIntExtra("index", 0);
        if (uris == null || uris.length == 0) {
            finish();
            return;
        }
        buildUi();

        ArrayList<String> args = new ArrayList<>();
        args.add("--audio-time-stretch");
        lib = new LibVLC(this, args);
        mp = new MediaPlayer(lib);
        mp.setEventListener(new MediaPlayer.EventListener() {
            @Override
            public void onEvent(MediaPlayer.Event e) {
                final int type = e.type;
                final long tc = e.getTimeChanged();
                final long lc = e.getLengthChanged();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onPlayerEvent(type, tc, lc);
                    }
                });
            }
        });
    }

    private void onPlayerEvent(int type, long tc, long lc) {
        switch (type) {
            case MediaPlayer.Event.LengthChanged:
                len = lc;
                dur.setText(fmt(len));
                applyResume();
                break;
            case MediaPlayer.Event.TimeChanged:
                applyResume();
                if (Math.abs(tc - lastSaved) > 5000) {
                    lastSaved = tc;
                    saveCur();
                }
                if (!userSeeking && len > 0) {
                    cur.setText(fmt(tc));
                    seek.setProgress((int) (tc * 1000 / len));
                }
                break;
            case MediaPlayer.Event.Playing:
                applyResume();
                playBtn.setText("⏸");
                sched();
                break;
            case MediaPlayer.Event.Paused:
                playBtn.setText("▶");
                break;
            case MediaPlayer.Event.EndReached:
                sp.edit().remove("pos:" + uris[idx]).apply();
                if (idx < uris.length - 1) open(idx + 1);
                else finish();
                break;
            case MediaPlayer.Event.EncounteredError:
                toast("Can’t play this file");
                break;
            default:
                break;
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        vl = new VLCVideoLayout(this);
        root.addView(vl, new FrameLayout.LayoutParams(-1, -1));

        final View touch = new View(this);
        root.addView(touch, new FrameLayout.LayoutParams(-1, -1));

        final GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                gMode = 0;
                gStartT = mp.getTime();
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                gBr = lp.screenBrightness < 0 ? 0.5f : lp.screenBrightness;
                gVol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (e1 == null) return false;
                float tx = e2.getX() - e1.getX(), ty = e2.getY() - e1.getY();
                float w = touch.getWidth(), hh = touch.getHeight();
                if (gMode == 0 && (Math.abs(tx) > 30 || Math.abs(ty) > 30)) {
                    gMode = Math.abs(tx) > Math.abs(ty) ? 1 : (e1.getX() < w / 2 ? 2 : 3);
                }
                if (gMode == 1 && len > 0) {
                    pendingSeek = Math.max(0, Math.min(len, gStartT + (long) (tx / w * 90000)));
                    showInfo(fmt(pendingSeek) + " / " + fmt(len));
                } else if (gMode == 2) {
                    float nb = Math.max(0.02f, Math.min(1f, gBr - ty / hh * 1.2f));
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = nb;
                    getWindow().setAttributes(lp);
                    showInfo("Brightness " + Math.round(nb * 100) + "%");
                } else if (gMode == 3) {
                    int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    float nv = Math.max(0, Math.min(max, gVol - ty / hh * max * 1.2f));
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(nv), 0);
                    showInfo("Volume " + Math.round(nv * 100f / max) + "%");
                }
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                setCtl(top.getVisibility() != View.VISIBLE);
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                float x = e.getX() / touch.getWidth();
                if (x < 0.4f) seekBy(-10000);
                else if (x > 0.6f) seekBy(10000);
                else togglePlay();
                return true;
            }
        });
        touch.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent e) {
                gd.onTouchEvent(e);
                int a = e.getAction();
                if (a == MotionEvent.ACTION_UP && gMode == 1 && pendingSeek >= 0) {
                    mp.setTime(pendingSeek);
                    pendingSeek = -1;
                }
                if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) gMode = 0;
                return true;
            }
        });

        info = new TextView(this);
        info.setTextColor(Color.WHITE);
        info.setTextSize(16);
        info.setBackgroundColor(0x99000000);
        info.setPadding(dp(14), dp(6), dp(14), dp(6));
        info.setVisibility(View.GONE);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        ilp.topMargin = dp(40);
        root.addView(info, ilp);

        top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(0x99000000);
        top.setPadding(dp(8), dp(4), dp(8), dp(4));
        top.addView(btn("←", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        }));
        title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setSingleLine(true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setBackgroundColor(0x99000000);
        bottom.setPadding(dp(12), dp(6), dp(12), dp(6));
        LinearLayout r1 = new LinearLayout(this);
        r1.setGravity(Gravity.CENTER_VERTICAL);
        cur = new TextView(this);
        cur.setTextColor(Color.WHITE);
        cur.setText("0:00");
        dur = new TextView(this);
        dur.setTextColor(Color.WHITE);
        dur.setText("0:00");
        seek = new SeekBar(this);
        seek.setMax(1000);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser && len > 0) cur.setText(fmt(len * p / 1000));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                userSeeking = true;
                h.removeCallbacks(hideCtl);
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                if (len > 0) mp.setTime(len * s.getProgress() / 1000);
                userSeeking = false;
                sched();
            }
        });
        r1.addView(cur);
        r1.addView(seek, new LinearLayout.LayoutParams(0, -2, 1f));
        r1.addView(dur);
        bottom.addView(r1);

        LinearLayout r2 = new LinearLayout(this);
        r2.setGravity(Gravity.CENTER);
        playBtn = btn("⏸", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlay();
            }
        });
        speedBtn = btn("1×", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rateI = (rateI + 1) % rates.length;
                mp.setRate(rates[rateI]);
                speedBtn.setText(rates[rateI] + "×");
                sched();
            }
        });
        Button[] bs = {
                btn("⏮", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (idx > 0) open(idx - 1);
                    }
                }),
                playBtn,
                btn("⏭", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (idx < uris.length - 1) open(idx + 1);
                    }
                }),
                speedBtn,
                btn("Audio", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pickTrack(true);
                    }
                }),
                btn("Subs", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pickTrack(false);
                    }
                }),
                btn("Fit", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        scaleI = (scaleI + 1) % scales.length;
                        mp.setVideoScale(scales[scaleI]);
                        showInfo(scaleNames[scaleI]);
                    }
                }),
                btn("⟳", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        landscape = !landscape;
                        setRequestedOrientation(landscape
                                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
                    }
                })};
        for (Button x : bs) r2.addView(x, new LinearLayout.LayoutParams(0, -2, 1f));
        bottom.addView(r2);
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        setContentView(root);
    }

    private void togglePlay() {
        if (mp.isPlaying()) mp.pause();
        else mp.play();
    }

    private void seekBy(long ms) {
        mp.setTime(Math.max(0, Math.min(len > 0 ? len : Long.MAX_VALUE, mp.getTime() + ms)));
        showInfo((ms > 0 ? "+" : "−") + Math.abs(ms / 1000) + " s");
    }

    private void showInfo(String t) {
        info.setText(t);
        info.setVisibility(View.VISIBLE);
        h.removeCallbacks(hideInfo);
        h.postDelayed(hideInfo, 900);
    }

    private void setCtl(boolean show) {
        int v = show ? View.VISIBLE : View.GONE;
        top.setVisibility(v);
        bottom.setVisibility(v);
        if (show) sched();
    }

    private void sched() {
        h.removeCallbacks(hideCtl);
        if (mp != null && mp.isPlaying()) h.postDelayed(hideCtl, 4000);
    }

    private void pickTrack(final boolean audio) {
        final MediaPlayer.TrackDescription[] t = audio ? mp.getAudioTracks() : mp.getSpuTracks();
        if (t == null || t.length == 0) {
            toast(audio ? "No audio tracks" : "No subtitles");
            return;
        }
        String[] n = new String[t.length];
        for (int i = 0; i < t.length; i++) n[i] = t[i].name;
        new AlertDialog.Builder(this).setTitle(audio ? "Audio track" : "Subtitles")
                .setItems(n, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        if (audio) mp.setAudioTrack(t[w].id);
                        else mp.setSpuTrack(t[w].id);
                    }
                }).show();
    }

    private void open(int i) {
        saveCur();
        idx = i;
        len = 0;
        Media m = new Media(lib, Uri.parse(uris[i]));
        m.setHWDecoderEnabled(true, false);
        long p = sp.getLong("pos:" + uris[i], 0);
        resumePos = p > 5000 ? p : 0;
        lastSaved = 0;
        m.addOption(":file-caching=300");
        mp.setMedia(m);
        m.release();
        mp.play();
        mp.setRate(rates[rateI]);
        title.setText(names != null && i < names.length ? names[i] : "");
        seek.setProgress(0);
        cur.setText("0:00");
    }

    private void saveCur() {
        if (mp == null || uris == null || !opened || resumePos > 0) return;
        long t = mp.getTime(), l = mp.getLength();
        String k = "pos:" + uris[idx];
        if (l > 0 && t > 5000 && l - t > 10000) sp.edit().putLong(k, t).apply();
        else if (l > 0 && t > 0 && l - t <= 10000) sp.edit().remove(k).apply();
    }

    private void applyResume() {
        if (resumePos > 0 && mp.isSeekable()) {
            long rp = resumePos;
            resumePos = 0;
            mp.setTime(rp);
            showInfo("Resumed at " + fmt(rp));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveCur();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mp == null) return;
        mp.attachViews(vl, null, true, false);
        if (!opened) {
            open(idx);
            opened = true;
        } else {
            mp.play();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mp == null) return;
        saveCur();
        mp.pause();
        mp.detachViews();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        h.removeCallbacksAndMessages(null);
        if (mp != null) {
            mp.stop();
            mp.release();
        }
        if (lib != null) lib.release();
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }
}
