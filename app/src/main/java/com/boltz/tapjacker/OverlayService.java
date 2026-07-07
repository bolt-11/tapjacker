package com.boltz.tapjacker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class OverlayService extends Service {

    private static final String CHANNEL_ID = "tapjacker_overlay_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final String PREFS_NAME = "tapjacker_prefs";

    private final IBinder binder = new LocalBinder();

    private WindowManager windowManager;
    private View overlayView;
    private TextView overlayText;
    private WindowManager.LayoutParams params;
    private boolean isAdded = false;
    private boolean isLocked = false;

    public class LocalBinder extends Binder {
        OverlayService getService() {
            return OverlayService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        startForegroundWithNotification();
    }

    private void startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
        }

        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    public boolean isShowing() {
        return isAdded;
    }

    public void showOverlay(OverlayConfig config) {
        if (isAdded) {
            updateConfig(config);
            return;
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.layout_overlay_button, null);
        overlayText = overlayView.findViewById(R.id.overlayText);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                dpToPx(config.widthDp),
                dpToPx(config.heightDp),
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        params.x = prefs.getInt("overlay_x", 200);
        params.y = prefs.getInt("overlay_y", 400);

        applyConfigToView(config);
        applyLockFlags(config.locked);

        overlayView.setOnTouchListener(new DragTouchListener());

        windowManager.addView(overlayView, params);
        isAdded = true;
    }

    public void updateConfig(OverlayConfig config) {
        if (!isAdded) {
            showOverlay(config);
            return;
        }
        params.width = dpToPx(config.widthDp);
        params.height = dpToPx(config.heightDp);
        applyConfigToView(config);
        applyLockFlags(config.locked);
        windowManager.updateViewLayout(overlayView, params);
    }

    public void hideOverlay() {
        if (isAdded && overlayView != null) {
            windowManager.removeView(overlayView);
            isAdded = false;
            overlayView = null;
        }
    }

    private void applyConfigToView(OverlayConfig config) {
        overlayText.setText(config.text);
        overlayView.setAlpha(config.opacityPercent / 100f);
        android.graphics.drawable.Drawable background = overlayView.getBackground();
        if (background instanceof GradientDrawable) {
            ((GradientDrawable) background.mutate()).setColor(config.color);
        }
    }

    private void applyLockFlags(boolean locked) {
        isLocked = locked;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (locked) {
            // Click-through: taps land on whatever window is beneath this overlay,
            // reproducing the real tapjacking effect instead of just visually mimicking it.
            flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        if (params != null) {
            params.flags = flags;
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private class DragTouchListener implements View.OnTouchListener {
        private int initialX;
        private int initialY;
        private float initialTouchX;
        private float initialTouchY;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (isLocked) {
                return false;
            }
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (event.getRawX() - initialTouchX);
                    int dy = (int) (event.getRawY() - initialTouchY);
                    params.x = initialX + dx;
                    params.y = initialY + dy;
                    windowManager.updateViewLayout(overlayView, params);
                    return true;
                case MotionEvent.ACTION_UP:
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .edit()
                            .putInt("overlay_x", params.x)
                            .putInt("overlay_y", params.y)
                            .apply();
                    return true;
                default:
                    return false;
            }
        }
    }

    @Override
    public void onDestroy() {
        hideOverlay();
        super.onDestroy();
    }
}
