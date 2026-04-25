package com.accessibilitymanager;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.accessibility.AccessibilityManager;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rikka.shizuku.Shizuku;

/**
 * 主界面 Activity
 * <p>
 * 负责展示系统中的无障碍服务列表，提供开关控制与保活锁定功能。
 * 实现了权限的按需申请与保活服务的静默开启。
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 1001; // 权限请求码

    private List<AccessibilityServiceInfo> serviceList;
    private SharedPreferences sp;
    private String daemonListStr;
    private ServiceAdapter adapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ContentObserver settingsObserver;
    private Shizuku.OnRequestPermissionResultListener shizukuPermissionListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initToolbar();
        initImmersiveStatusBar();

        sp = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
        daemonListStr = sp.getString(AppConstants.KEY_DAEMON_LIST, "");

        // 初始化“隐藏后台”状态
        boolean hideRecents = sp.getBoolean(AppConstants.KEY_HIDE_RECENTS, false);
        if (hideRecents) {
            applyHideFromRecents(true);
        }

        initListView();
        initSettingsObserver();
        initShizukuListener();

        // 检查并申请通知权限 (Android 13+)
        checkNotificationPermission();

        // 尝试启动守护服务 (如果有权限)
        startDaemonService();
    }

    private void initToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
        }
    }

    /**
     * 配置沉浸式状态栏和导航栏
     */
    private void initImmersiveStatusBar() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            // 确保布局延伸到状态栏和导航栏下方
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void initListView() {
        ListView listView = findViewById(R.id.list);
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);

        if (am != null) {
            serviceList = new ArrayList<>(am.getInstalledAccessibilityServiceList());
        } else {
            serviceList = new ArrayList<>();
        }

        sortServices();

        adapter = new ServiceAdapter();
        listView.setAdapter(adapter);
    }

    private void sortServices() {
        Collections.sort(serviceList, (o1, o2) -> {
            boolean firstPinned = DaemonListStore.containsId(daemonListStr, o1.getId());
            boolean secondPinned = DaemonListStore.containsId(daemonListStr, o2.getId());
            return Boolean.compare(secondPinned, firstPinned);
        });
    }

    private void initSettingsObserver() {
        settingsObserver = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange) {
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }
        };
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                true, settingsObserver);
    }

    private void initShizukuListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            shizukuPermissionListener = (requestCode, grantResult) -> {
                if (requestCode != PermissionUtils.REQUEST_CODE_SHIZUKU) {
                    return;
                }
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    PermissionUtils.runShizukuCommand(this);
                }
            };
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
        }
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE_POST_NOTIFICATIONS);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 用户授权后，尝试重新启动服务以显示通知
                startDaemonService();
            } else {
                Toast.makeText(this, R.string.notification_permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void ensureKeepAliveService() {
        if (PermissionUtils.hasSecureSettingsPermission(this)) {
            AccessibilityUtils.tryEnableKeepAliveService(this);
        }
    }

    private void startDaemonService() {
        if (!PermissionUtils.hasSecureSettingsPermission(this)) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to request battery optimization ignore", e);
                }
            }
        }

        Intent intent = new Intent(this, DaemonService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    /**
     * 设置应用是否从最近任务列表中隐藏
     */
    private void applyHideFromRecents(boolean hide) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    List<ActivityManager.AppTask> tasks = am.getAppTasks();
                    if (tasks != null && !tasks.isEmpty()) {
                        tasks.get(0).setExcludeFromRecents(hide);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to change recents visibility", e);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (settingsObserver != null) {
            getContentResolver().unregisterContentObserver(settingsObserver);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && shizukuPermissionListener != null) {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.arrange, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.boot).setChecked(sp.getBoolean(AppConstants.KEY_AUTO_BOOT, true));
        menu.findItem(R.id.toast).setChecked(sp.getBoolean(AppConstants.KEY_SHOW_TOAST, true));
        menu.findItem(R.id.hide).setChecked(sp.getBoolean(AppConstants.KEY_HIDE_RECENTS, false));
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.boot) {
            boolean newState = !item.isChecked();
            sp.edit().putBoolean(AppConstants.KEY_AUTO_BOOT, newState).apply();
            item.setChecked(newState);
            return true;
        } else if (id == R.id.toast) {
            boolean newState = !item.isChecked();
            sp.edit().putBoolean(AppConstants.KEY_SHOW_TOAST, newState).apply();
            item.setChecked(newState);
            return true;
        } else if (id == R.id.hide) {
            // 处理隐藏后台逻辑
            boolean newState = !item.isChecked();
            sp.edit().putBoolean(AppConstants.KEY_HIDE_RECENTS, newState).apply();
            item.setChecked(newState);
            applyHideFromRecents(newState);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showServiceDescriptionDialog(String title, CharSequence description) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(description)
                .setPositiveButton(R.string.dialog_close, null)
                .show();
    }

    class ServiceAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return serviceList.size();
        }

        @Override
        public Object getItem(int position) {
            return serviceList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @SuppressLint("InflateParams")
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item, parent, false);
                holder = new ViewHolder();
                holder.cardView = convertView.findViewById(R.id.card_view);
                holder.serviceNameTv = convertView.findViewById(R.id.service_name);
                holder.serviceDescTv = convertView.findViewById(R.id.service_desc);
                holder.serviceIconIv = convertView.findViewById(R.id.service_icon);
                holder.serviceSwitch = convertView.findViewById(R.id.service_switch);
                holder.lockButton = convertView.findViewById(R.id.lock_button);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            AccessibilityServiceInfo info = serviceList.get(position);
            String id = info.getId();
            ComponentName cn = ComponentName.unflattenFromString(id);
            PackageManager pm = getPackageManager();

            String title = id;
            holder.serviceIconIv.setImageResource(android.R.drawable.sym_def_app_icon);
            try {
                CharSequence serviceLabel = info.getResolveInfo() != null
                        ? info.getResolveInfo().loadLabel(pm)
                        : null;
                if (!TextUtils.isEmpty(serviceLabel)) {
                    title = serviceLabel.toString();
                } else if (cn != null) {
                    title = pm.getApplicationLabel(pm.getApplicationInfo(cn.getPackageName(), 0)).toString();
                }
                if (cn != null) {
                    holder.serviceIconIv.setImageDrawable(pm.getApplicationIcon(cn.getPackageName()));
                }
            } catch (PackageManager.NameNotFoundException e) {
                holder.serviceIconIv.setImageResource(android.R.drawable.sym_def_app_icon);
            }
            CharSequence description = info.loadDescription(pm);
            CharSequence fullDescription = TextUtils.isEmpty(description)
                    ? getString(R.string.service_description_fallback)
                    : description;

            holder.serviceNameTv.setText(title);
            holder.serviceDescTv.setText(fullDescription);

            boolean isEnabled = AccessibilityUtils.isServiceEnabled(MainActivity.this, id);
            boolean isDaemon = DaemonListStore.containsId(daemonListStr, id);
            final String dialogTitle = title;
            final CharSequence dialogDescription = fullDescription;

            holder.cardView.setOnClickListener(v -> showServiceDescriptionDialog(dialogTitle, dialogDescription));

            holder.serviceSwitch.setOnCheckedChangeListener(null);
            holder.serviceSwitch.setChecked(isEnabled);

            holder.lockButton.setVisibility(isEnabled ? View.VISIBLE : View.INVISIBLE);
            holder.lockButton.setImageResource(isDaemon ? R.drawable.lock1 : R.drawable.lock);
            holder.lockButton.setContentDescription(
                    getString(isDaemon ? R.string.lock_button_desc_locked : R.string.lock_button_desc_unlocked)
            );

            holder.serviceSwitch.setOnClickListener(v -> {
                if (!PermissionUtils.hasSecureSettingsPermission(MainActivity.this)) {
                    holder.serviceSwitch.setChecked(!holder.serviceSwitch.isChecked());
                    PermissionUtils.showPermissionDialog(MainActivity.this);
                    return;
                }
                if (holder.serviceSwitch.isChecked()) {
                    AccessibilityUtils.enableService(MainActivity.this, id);
                    ensureKeepAliveService();
                    holder.lockButton.setVisibility(View.VISIBLE);
                } else {
                    if (isDaemon) {
                        updateDaemonList(id, false);
                    }
                    AccessibilityUtils.disableService(MainActivity.this, id);
                    holder.lockButton.setVisibility(View.INVISIBLE);
                }
            });

            holder.lockButton.setOnClickListener(v -> {
                if (!PermissionUtils.hasSecureSettingsPermission(MainActivity.this)) {
                    PermissionUtils.showPermissionDialog(MainActivity.this);
                    return;
                }
                boolean newStatus = !DaemonListStore.containsId(daemonListStr, id);
                updateDaemonList(id, newStatus);
                if (newStatus) {
                    startDaemonService();
                    ensureKeepAliveService();
                }
            });
            return convertView;
        }

        private void updateDaemonList(String id, boolean add) {
            daemonListStr = add
                    ? DaemonListStore.addId(daemonListStr, id)
                    : DaemonListStore.removeId(daemonListStr, id);
            sp.edit().putString(AppConstants.KEY_DAEMON_LIST, daemonListStr).apply();
            sortServices();
            notifyDataSetChanged();
        }
    }

    static class ViewHolder {
        View cardView;
        TextView serviceNameTv;
        TextView serviceDescTv;
        ImageView serviceIconIv;
        SwitchMaterial serviceSwitch;
        ImageButton lockButton;
    }
}