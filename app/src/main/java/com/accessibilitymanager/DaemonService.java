package com.accessibilitymanager;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

public class DaemonService extends Service {
    private static final String TAG = "DaemonService";
    private SettingsObserver mContentOb;
    private HandlerThread mWorkerThread;
    private Handler mHandler; // 后台线程Handler
    private Handler mMainHandler; // 主线程Handler用于Toast
    private SharedPreferences sp;
    private boolean isSelfModification = false; // 防止死循环标志位

    @Override
    public void onCreate() {
        super.onCreate();
        sp = getSharedPreferences("data", 0);
        mMainHandler = new Handler(getMainLooper());

        // 1. 启动后台线程
        mWorkerThread = new HandlerThread("DaemonWorker");
        mWorkerThread.start();
        mHandler = new Handler(mWorkerThread.getLooper());

        // 2. 注册监听
        mContentOb = new SettingsObserver(mHandler);
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                true,
                mContentOb
        );

        // 3. 开启前台服务通知
        startForegroundNotification();

        // 4. 立即执行一次检查
        mHandler.post(this::doDaemonCheck);

        Toast.makeText(this, "无障碍保活服务已启动", Toast.LENGTH_SHORT).show();
    }

    private void startForegroundNotification() {
        String channelId = "daemon_service";
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "保活服务", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            if (nm != null) nm.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.tile)
                .setContentTitle("无障碍服务守护中")
                .setContentText("正在后台监控服务状态")
                .setContentIntent(pi);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(channelId);
        }
        startForeground(1001, builder.build());
    }

    // 核心检测逻辑，运行在后台线程
    private void doDaemonCheck() {
        if (isSelfModification) {
            isSelfModification = false;
            return;
        }

        String daemonListStr = sp.getString("daemon", "");
        if (daemonListStr.isEmpty()) return;

        Set<ComponentName> currentEnabled = AccessibilityUtils.getEnabledServices(this);
        Set<ComponentName> targetEnabled = new HashSet<>(currentEnabled);
        boolean needUpdate = false;
        StringBuilder restoredNames = new StringBuilder();

        String[] daemonArray = daemonListStr.split(":");
        for (String id : daemonArray) {
            if (id == null || id.isEmpty()) continue;
            ComponentName cn = ComponentName.unflattenFromString(id);

            // 如果必须保活的服务没有在当前开启列表中
            if (cn != null && !currentEnabled.contains(cn)) {
                // 检查服务是否存在于手机上（防止卸载后报错）
                try {
                    getPackageManager().getServiceInfo(cn, 0);
                    targetEnabled.add(cn);
                    needUpdate = true;
                    restoredNames.append(cn.getPackageName()).append(" ");
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
        }

        if (needUpdate) {
            Log.d(TAG, "检测到服务被杀，正在恢复: " + restoredNames);
            isSelfModification = true; // 标记由本APP修改
            AccessibilityUtils.setEnabledServices(this, targetEnabled);

            if (sp.getBoolean("toast", true)) {
                String msg = "已自动拉起: " + restoredNames;
                mMainHandler.post(() -> Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show());
            }
        }
    }

    class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            mHandler.post(DaemonService.this::doDaemonCheck);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getContentResolver().unregisterContentObserver(mContentOb);
        if (mWorkerThread != null) mWorkerThread.quitSafely();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}