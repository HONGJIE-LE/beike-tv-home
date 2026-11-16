package com.local.signingdisplay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;

public class MainActivity extends Activity {
    private static GeckoRuntime runtime;
    private GeckoSession session;
    private GeckoView web;
    private Pointer pointer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String URL;
    private final Runnable retry = () -> { if (session != null) session.loadUri(URL); };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        URL = getString(com.local.signingdisplay.R.string.display_url);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        fullscreen();
        GeckoView view = new GeckoView(this);
        web = view;
        FrameLayout root = new FrameLayout(this);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        pointer = new Pointer();
        root.addView(pointer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        if (runtime == null) runtime = GeckoRuntime.create(this);
        GeckoSessionSettings settings = new GeckoSessionSettings.Builder()
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
            .build();
        session = new GeckoSession(settings);
        session.setContentDelegate(new GeckoSession.ContentDelegate() {});
        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onPageStop(GeckoSession current, boolean success) {
                handler.removeCallbacks(retry);
                if (!success) handler.postDelayed(retry, 30000);
            }
        });
        session.open(runtime);
        view.setSession(session);
        session.loadUri(URL);
        view.requestFocus();
    }

    private void fullscreen() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) fullscreen();
    }
    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_MENU && session != null) {
            session.reload();
            return true;
        }
        return super.onKeyDown(code, event);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (pointer != null && (code == KeyEvent.KEYCODE_DPAD_LEFT
                || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_UP
                || code == KeyEvent.KEYCODE_DPAD_DOWN)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                float step = 14 + Math.min(46, event.getRepeatCount() * 3);
                pointer.move(code == KeyEvent.KEYCODE_DPAD_LEFT ? -step : code == KeyEvent.KEYCODE_DPAD_RIGHT ? step : 0,
                    code == KeyEvent.KEYCODE_DPAD_UP ? -step : code == KeyEvent.KEYCODE_DPAD_DOWN ? step : 0);
            }
            return true;
        }
        if (pointer != null && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                pointer.move(0, 0);
                long now = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, pointer.x, pointer.y, 0);
                MotionEvent up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, pointer.x, pointer.y, 0);
                web.dispatchTouchEvent(down);
                web.dispatchTouchEvent(up);
                down.recycle(); up.recycle();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    private class Pointer extends View {
        float x = -1, y = -1;
        boolean visible;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Runnable hide = () -> { visible = false; invalidate(); };
        Pointer() { super(MainActivity.this); setFocusable(false); setClickable(false); }
        void move(float dx, float dy) {
            if (getWidth() == 0 || getHeight() == 0) return;
            if (x < 0) { x = getWidth() / 2f; y = getHeight() / 2f; }
            x = Math.max(2, Math.min(getWidth() - 3, x + dx));
            y = Math.max(2, Math.min(getHeight() - 3, y + dy));
            visible = true;
            handler.removeCallbacks(hide); handler.postDelayed(hide, 5000);
            invalidate();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!visible) return;
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.rgb(0, 102, 255));
            canvas.drawCircle(x, y, 10, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3); paint.setColor(Color.WHITE);
            canvas.drawCircle(x, y, 11, paint);
            canvas.drawLine(x - 18, y, x + 18, y, paint);
            canvas.drawLine(x, y - 18, x, y + 18, paint);
        }
    }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (session != null) session.close();
        super.onDestroy();
    }
    @Override public void onBackPressed() {
        new AlertDialog.Builder(this)
            .setTitle("返回电视首页")
            .setMessage("返回贝壳首页；再次打开时若登录过期，请重新扫码登录。")
            .setNegativeButton("继续展示", (dialog, which) -> fullscreen())
            .setPositiveButton("返回首页", (dialog, which) -> {
                try {
                    startActivity(new Intent(Intent.ACTION_MAIN)
                        .setComponent(new ComponentName("com.local.beikehome", "com.local.beikehome.HomeActivity"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (android.content.ActivityNotFoundException e) {
                    startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                }
            }).show();
    }
}
