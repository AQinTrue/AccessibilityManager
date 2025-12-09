package com.accessibilitymanager;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

public class AccessibilityUtils {
    private static final String TAG = "AccessibilityUtils";
    private static final String SETTING_KEY = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES;
    private static final TextUtils.SimpleStringSplitter COLON_SPLITTER = new TextUtils.SimpleStringSplitter(':');

    /**
     * 获取当前系统已开启的无障碍服务列表
     */
    public static Set<ComponentName> getEnabledServices(Context context) {
        String settingValue = Settings.Secure.getString(context.getContentResolver(), SETTING_KEY);
        if (TextUtils.isEmpty(settingValue)) {
            return new HashSet<>();
        }
        Set<ComponentName> enabledServices = new HashSet<>();
        COLON_SPLITTER.setString(settingValue);
        while (COLON_SPLITTER.hasNext()) {
            String componentNameString = COLON_SPLITTER.next();
            ComponentName enabledService = ComponentName.unflattenFromString(componentNameString);
            if (enabledService != null) {
                enabledServices.add(enabledService);
            }
        }
        return enabledServices;
    }

    /**
     * 安全写入 Settings
     */
    public static void setEnabledServices(Context context, Set<ComponentName> services) {
        StringBuilder sb = new StringBuilder();
        for (ComponentName componentName : services) {
            if (sb.length() > 0) {
                sb.append(':');
            }
            sb.append(componentName.flattenToString());
        }
        try {
            Settings.Secure.putString(context.getContentResolver(), SETTING_KEY, sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "写入安全设置失败，可能无权限", e);
        }
    }

    /**
     * 判断某个服务是否开启
     */
    public static boolean isServiceEnabled(Context context, String serviceId) {
        ComponentName target = ComponentName.unflattenFromString(serviceId);
        if (target == null) return false;
        return getEnabledServices(context).contains(target);
    }

    /**
     * 开启指定服务
     */
    public static void enableService(Context context, String serviceId) {
        ComponentName componentName = ComponentName.unflattenFromString(serviceId);
        if (componentName == null) return;

        Set<ComponentName> enabledServices = getEnabledServices(context);
        // 如果集合中加入了新元素（即原本没开启），则写入设置
        if (enabledServices.add(componentName)) {
            setEnabledServices(context, enabledServices);
        }
    }

    /**
     * 关闭指定服务
     */
    public static void disableService(Context context, String serviceId) {
        ComponentName componentName = ComponentName.unflattenFromString(serviceId);
        if (componentName == null) return;

        Set<ComponentName> enabledServices = getEnabledServices(context);
        // 如果集合中移除了元素（即原本是开启的），则写入设置
        if (enabledServices.remove(componentName)) {
            setEnabledServices(context, enabledServices);
        }
    }
}