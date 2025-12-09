package com.accessibilitymanager;

/**
 * 全局常量管理类
 * <p>
 * 用于统一管理 SharedPreferences 的 Key、文件名以及其他静态配置，
 * 避免硬编码导致的拼写错误，方便团队协作与维护。
 */
public class AppConstants {
    /**
     * SharedPreferences 文件名
     */
    public static final String PREFS_NAME = "app_config";

    /**
     * 保活列表 Key (格式: "pkg/cls:pkg2/cls2:")
     */
    public static final String KEY_DAEMON_LIST = "daemon_list";

    /**
     * 是否开机自启 Key
     */
    public static final String KEY_AUTO_BOOT = "auto_boot";

    /**
     * 是否显示保活Toast Key
     */
    public static final String KEY_SHOW_TOAST = "show_toast";

    /**
     * 是否隐藏后台 Key
     */
    public static final String KEY_HIDE_RECENTS = "hide_recents";
}