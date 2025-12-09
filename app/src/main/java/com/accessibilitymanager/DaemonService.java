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

/**
 * 后台守护服务
 * <p>
 * 核心功能：
 * 1. 监听系统 Settings 变化。
 * 2. 对比当前开启的服务与用户设定的保活列表。
 * 3. 自动拉起被意外关闭的服务。
 */
public class DaemonService extends Service {
    private static final String TAG = "DaemonService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "daemon_service_channel";

    private SettingsObserver mContentOb;
    private HandlerThread mWorkerThread;
    private Handler mHandler; // 后台工作线程，处理耗时逻辑
    private Handler mMainHandler; // 主线程，用于显示 Toast
    private SharedPreferences sp;
    private boolean isSelfModification = false; // 标志位：防止修改设置引发的递归调用

    @Override
    public void onCreate() {
        super.onCreate();
        sp = getSharedPreferences(AppConstants.PREFS_NAME, 0);
        mMainHandler = new Handler(getMainLooper());

        // 1. 启动后台线程 (防止阻塞主线程)
        mWorkerThread = new HandlerThread("DaemonWorker");
        mWorkerThread.start();
        mHandler = new Handler(mWorkerThread.getLooper());

        // 2. 注册 Settings 监听器
        mContentOb = new SettingsObserver(mHandler);
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                true,
                mContentOb
        );

        // 3. 开启前台服务通知 (保活必要手段)
        startForegroundNotification();

        // 4. 立即执行一次检查
        mHandler.post(this::doDaemonCheck);

        Log.i(TAG, "DaemonService 已启动，正在后台监控...");
    }

    private void startForegroundNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "保活服务", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            if (nm != null) nm.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.tile)
                .setContentTitle("海绵宝宝，猜猜我有几颗糖~")
                .setContentText("猜对了两颗都给你！")
                .setContentIntent(pi);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID);
        }
        startForeground(NOTIFICATION_ID, builder.build());
    }

    /**
     * 核心检测逻辑
     * 运行在后台线程，比对当前开启的服务与保活列表
     */
    private void doDaemonCheck() {
        // 如果是本服务自己修改导致的 Settings 变化，忽略本次回调
        if (isSelfModification) {
            isSelfModification = false;
            return;
        }

        String daemonListStr = sp.getString(AppConstants.KEY_DAEMON_LIST, "");
        if (daemonListStr.isEmpty()) return;

        Set<ComponentName> currentEnabled = AccessibilityUtils.getEnabledServices(this);
        Set<ComponentName> targetEnabled = new HashSet<>(currentEnabled);
        boolean needUpdate = false;
        StringBuilder restoredNames = new StringBuilder();

        String[] daemonArray = daemonListStr.split(":");
        for (String id : daemonArray) {
            if (id == null || id.isEmpty()) continue;
            ComponentName cn = ComponentName.unflattenFromString(id);

            // 如果保活列表中的服务未开启
            if (cn != null && !currentEnabled.contains(cn)) {
                // 检查该服务应用是否存在 (防止应用被卸载后导致崩溃或无限循环)
                try {
                    getPackageManager().getServiceInfo(cn, 0);
                    targetEnabled.add(cn);
                    needUpdate = true;
                    restoredNames.append(cn.getPackageName()).append(" ");
                } catch (PackageManager.NameNotFoundException ignored) {
                    Log.w(TAG, "未找到应用，跳过保活: " + id);
                }
            }
        }

        if (needUpdate) {
            Log.d(TAG, "检测到服务被关闭，正在恢复: " + restoredNames);
            isSelfModification = true; // 标记由本APP修改
            AccessibilityUtils.setEnabledServices(this, targetEnabled);

            if (sp.getBoolean(AppConstants.KEY_SHOW_TOAST, true)) {
                String msg = "已自动拉起: " + restoredNames;
                mMainHandler.post(() -> Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show());
            }
        }
    }

    /**
     * Settings 内容观察者
     */
    class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            // 在 Handler 线程中执行检查
            mHandler.post(DaemonService.this::doDaemonCheck);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // 服务被杀后尝试重启
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mContentOb != null) {
            getContentResolver().unregisterContentObserver(mContentOb);
        }
        if (mWorkerThread != null) {
            mWorkerThread.quitSafely();
        }
        Log.i(TAG, "DaemonService destroyed.");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}