package com.accessibilitymanager;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window; // 导入 Window
import android.view.accessibility.AccessibilityManager;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {

    private List<AccessibilityServiceInfo> serviceList;
    private SharedPreferences sp;
    private String daemonListStr;
    private ServiceAdapter adapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ContentObserver settingsObserver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("无障碍管理器");

        // 【修复】还原UI沉浸式设置（去除紫边）
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            // 确保布局延伸到状态栏下方
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }

        sp = getSharedPreferences("data", 0);
        daemonListStr = sp.getString("daemon", "");

        ListView listView = findViewById(R.id.list);
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);

        // 创建可变列表防止崩溃
        serviceList = new ArrayList<>(am.getInstalledAccessibilityServiceList());

        Collections.sort(serviceList, (o1, o2) -> {
            boolean b1 = daemonListStr.contains(o1.getId());
            boolean b2 = daemonListStr.contains(o2.getId());
            return Boolean.compare(b2, b1);
        });

        adapter = new ServiceAdapter();
        listView.setAdapter(adapter);

        settingsObserver = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange) {
                adapter.notifyDataSetChanged();
            }
        };
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                true, settingsObserver);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Shizuku.addRequestPermissionResultListener((requestCode, grantResult) -> {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    PermissionUtils.runShizukuCommand(this);
                }
            });
        }

        checkFirstRun();
        startDaemonService();
    }

    private void checkFirstRun() {
        if (sp.getBoolean("first", true)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("提示")
                    .setMessage("为保证保活效果，建议在系统设置中开启本APP的【保活组件】（一个空的无障碍服务）。")
                    .setPositiveButton("去开启", (d, i) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        } catch (Exception ignored) {
                        }
                    })
                    .setNegativeButton("知道了", null)
                    .show();
            sp.edit().putBoolean("first", false).apply();
        }
    }

    private void startDaemonService() {
        if (!PermissionUtils.hasSecureSettingsPermission(this)) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception ignored) {
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getContentResolver().unregisterContentObserver(settingsObserver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.arrange, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.boot).setChecked(sp.getBoolean("boot", true));
        menu.findItem(R.id.toast).setChecked(sp.getBoolean("toast", true));
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.boot || id == R.id.toast) {
            boolean newState = !item.isChecked();
            sp.edit().putBoolean(id == R.id.boot ? "boot" : "toast", newState).apply();
            item.setChecked(newState);
            return true;
        }
        return super.onOptionsItemSelected(item);
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

        @SuppressLint("ViewHolder")
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item, parent, false);
            }

            AccessibilityServiceInfo info = serviceList.get(position);
            String id = info.getId();
            ComponentName cn = ComponentName.unflattenFromString(id);
            PackageManager pm = getPackageManager();

            TextView nameTv = convertView.findViewById(R.id.b);
            TextView descTv = convertView.findViewById(R.id.a);
            ImageView iconIv = convertView.findViewById(R.id.c);
            SwitchMaterial sw = convertView.findViewById(R.id.s);
            ImageButton lockBtn = convertView.findViewById(R.id.ib);

            String label = id;
            try {
                if (cn != null) {
                    label = pm.getApplicationLabel(pm.getApplicationInfo(cn.getPackageName(), 0)).toString();
                    iconIv.setImageDrawable(pm.getApplicationIcon(cn.getPackageName()));
                }
            } catch (Exception e) {
                iconIv.setImageResource(android.R.drawable.sym_def_app_icon);
            }
            nameTv.setText(label);
            descTv.setText(info.loadDescription(pm));

            boolean isEnabled = AccessibilityUtils.isServiceEnabled(MainActivity.this, id);
            boolean isDaemon = daemonListStr.contains(id);

            sw.setOnCheckedChangeListener(null);
            sw.setChecked(isEnabled);
            lockBtn.setVisibility(isEnabled ? View.VISIBLE : View.INVISIBLE);
            lockBtn.setImageResource(isDaemon ? R.drawable.lock1 : R.drawable.lock);

            sw.setOnClickListener(v -> {
                if (!PermissionUtils.hasSecureSettingsPermission(MainActivity.this)) {
                    sw.setChecked(!sw.isChecked());
                    PermissionUtils.showPermissionDialog(MainActivity.this);
                    return;
                }

                if (sw.isChecked()) {
                    AccessibilityUtils.enableService(MainActivity.this, id);
                } else {
                    if (isDaemon) {
                        updateDaemonList(id, false);
                        lockBtn.setImageResource(R.drawable.lock);
                    }
                    AccessibilityUtils.disableService(MainActivity.this, id);
                }
            });

            lockBtn.setOnClickListener(v -> {
                boolean newStatus = !daemonListStr.contains(id);
                updateDaemonList(id, newStatus);
                lockBtn.setImageResource(newStatus ? R.drawable.lock1 : R.drawable.lock);
                if (newStatus) startDaemonService();
            });

            return convertView;
        }

        private void updateDaemonList(String id, boolean add) {
            if (add) {
                if (!daemonListStr.contains(id)) daemonListStr += id + ":";
            } else {
                daemonListStr = daemonListStr.replace(id + ":", "");
            }
            sp.edit().putString("daemon", daemonListStr).apply();
            notifyDataSetChanged();
        }
    }
}