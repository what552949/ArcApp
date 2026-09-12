package com.example.arcapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class FloatingService extends Service {

    private WindowManager wm;
    private WebView webView;
    private ImageView lockButton;
    private WindowManager.LayoutParams webParams;
    private WindowManager.LayoutParams lockParams;
    private boolean locked = false;
    private boolean viewAdded = false;

    private ValueCallback<Uri[]> filePathCallback;
    private static final int LOCK_SIZE_DP = 56;

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        createNotification();
        createWebView();
        createLockButton();
        viewAdded = true;
    }

    private int getWindowType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "arc_channel", "圆弧悬浮窗", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
        Notification n = new NotificationCompat.Builder(this, "arc_channel")
                .setContentTitle("圆弧悬浮窗运行中")
                .setContentText("点击锁按钮可穿透操作")
                .setSmallIcon(R.drawable.ic_lock_closed)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
        startForeground(1, n);
    }

    private void createWebView() {
        webView = new WebView(this);

        // ★ 关键：让 WebView 自己那层也透明
        webView.setBackgroundColor(0x00000000);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = cb;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(Intent.createChooser(intent, "选择图片"));
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/index.html?mode=floating");

        webParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                getWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        webParams.gravity = Gravity.TOP | Gravity.START;

        wm.addView(webView, webParams);
    }

    private void createLockButton() {
        int size = (int) (LOCK_SIZE_DP * getResources().getDisplayMetrics().density);

        lockButton = new ImageView(this);
        lockButton.setImageResource(R.drawable.ic_lock_open);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#CC21262D"));
        bg.setStroke((int) (2 * getResources().getDisplayMetrics().density),
                Color.parseColor("#F0883E"));
        lockButton.setBackground(bg);
        lockButton.setAlpha(0.6f);
        lockButton.setPadding(size / 5, size / 5, size / 5, size / 5);
        lockButton.setScaleType(ImageView.ScaleType.FIT_CENTER);

        lockParams = new WindowManager.LayoutParams(
                size,
                size,
                getWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        lockParams.gravity = Gravity.TOP | Gravity.START;
        lockParams.x = 60;
        lockParams.y = 300;

        lockButton.setOnTouchListener(new View.OnTouchListener() {
            float downRawX, downRawY;
            int startX, startY;
            boolean moved;
            long downTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startX = lockParams.x;
                        startY = lockParams.y;
                        moved = false;
                        downTime = System.currentTimeMillis();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        if (Math.abs(dx) > 12 || Math.abs(dy) > 12) moved = true;
                        lockParams.x = startX + (int) dx;
                        lockParams.y = startY + (int) dy;
                        wm.updateViewLayout(lockButton, lockParams);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!moved && System.currentTimeMillis() - downTime < 600) {
                            toggleLock();
                        }
                        return true;
                }
                return false;
            }
        });

        wm.addView(lockButton, lockParams);
    }

    private void toggleLock() {
        locked = !locked;

        lockButton.setImageResource(locked ? R.drawable.ic_lock_closed : R.drawable.ic_lock_open);

        if (locked) {
            webParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            webParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        if (viewAdded) {
            wm.updateViewLayout(webView, webParams);
        }

        // ★ 通知 HTML 折叠 / 展开面板
        if (webView != null) {
            String js = "window.__setPanelCollapsed && window.__setPanelCollapsed(" + locked + ");";
            webView.evaluateJavascript(js, null);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        viewAdded = false;
        if (webView != null) {
            try { wm.removeView(webView); } catch (Exception ignored) {}
            webView.destroy();
            webView = null;
        }
        if (lockButton != null) {
            try { wm.removeView(lockButton); } catch (Exception ignored) {}
            lockButton = null;
        }
    }
}
