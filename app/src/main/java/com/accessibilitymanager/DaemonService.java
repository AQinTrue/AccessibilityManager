package com.accessibilitymanager;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
 * 后台守护服务 (增强版)
 * <p>
 * 新增特性：
 * 1. 监听屏幕点亮/解锁广播：手机唤醒时立即检查。
 * 2. 定时心跳机制：兜底防止 Observer 失效。
 */
public class DaemonService extends Service {
    private static final String TAG = "DaemonService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "daemon_service_channel";

    // 心跳间隔：60秒检查一次
    private static final long HEARTBEAT_INTERVAL = 60 * 1000;

    private SettingsObserver mContentOb;
    private HandlerThread mWorkerThread;
    private Handler mHandler;
    private Handler mMainHandler;
    private SharedPreferences sp;
    private boolean isSelfModification = false;

    // 屏幕广播接收器
    private ScreenReceiver mScreenReceiver;

    // 心跳任务
    private final Runnable mHeartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            doDaemonCheck();
            // 循环调用，保持心跳
            if (mHandler != null) {
                mHandler.postDelayed(this, HEARTBEAT_INTERVAL);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        sp = getSharedPreferences(AppConstants.PREFS_NAME, 0);
        mMainHandler = new Handler(getMainLooper());

        // 1. 启动后台线程
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

        // 3. 注册屏幕广播监听 (新增的关键部分)
        registerScreenReceiver();

        // 4. 开启前台服务通知
        startForegroundNotification();

        // 5. 立即执行一次检查，并启动心跳
        mHandler.post(this::doDaemonCheck);
        mHandler.postDelayed(mHeartbeatRunnable, HEARTBEAT_INTERVAL);

        Log.i(TAG, "DaemonService 已启动 (含屏幕监听与心跳)...");
    }

    /**
     * 注册动态广播接收器监听屏幕状态
     * 必须在代码中注册，Android 8.0+ 静态注册无效
     */
    private void registerScreenReceiver() {
        mScreenReceiver = new ScreenReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);     // 屏幕点亮
        filter.addAction(Intent.ACTION_USER_PRESENT);  // 用户解锁
        registerReceiver(mScreenReceiver, filter);
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
                try {
                    getPackageManager().getServiceInfo(cn, 0);
                    targetEnabled.add(cn);
                    needUpdate = true;
                    String label = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(cn.getPackageName(), 0)).toString();
                    restoredNames.append(label).append(" ");
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
            // Log.d(TAG, "Settings changed, trigger check");
            mHandler.removeCallbacks(mHeartbeatRunnable); // 重置心跳计时
            mHandler.post(DaemonService.this::doDaemonCheck);
            mHandler.postDelayed(mHeartbeatRunnable, HEARTBEAT_INTERVAL);
        }
    }

    /**
     * 屏幕广播接收器内部类
     */
    class ScreenReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_USER_PRESENT.equals(action)) {
                Log.i(TAG, "屏幕点亮/解锁，强制检查服务状态...");
                if (mHandler != null) {
                    mHandler.post(DaemonService.this::doDaemonCheck);
                }
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 每次 startService 也触发一次检查
        if (mHandler != null) {
            mHandler.post(this::doDaemonCheck);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mContentOb != null) {
            getContentResolver().unregisterContentObserver(mContentOb);
        }
        if (mScreenReceiver != null) {
            unregisterReceiver(mScreenReceiver);
        }
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
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