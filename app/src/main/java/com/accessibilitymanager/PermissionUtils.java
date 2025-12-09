package com.accessibilitymanager;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.DataOutputStream;
import java.io.IOException;

import rikka.shizuku.Shizuku;

/**
 * 权限管理工具类
 * <p>
 * 负责 WRITE_SECURE_SETTINGS 权限的检查与授权引导。
 * 支持 Root 命令授权、Shizuku 授权以及生成 ADB 命令。
 */
public class PermissionUtils {

    private static final String TAG = "PermissionUtils";

    /**
     * 检查是否拥有写入安全设置的权限
     *
     * @param context 上下文
     * @return true 表示已拥有权限
     */
    public static boolean hasSecureSettingsPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
        }
        // Android 6.0 以下通常在安装时授权
        return true;
    }

    /**
     * 显示权限申请引导对话框
     *
     * @param context 上下文
     */
    public static void showPermissionDialog(Context context) {
        String cmd = "pm grant " + context.getPackageName() + " android.permission.WRITE_SECURE_SETTINGS";
        new MaterialAlertDialogBuilder(context)
                .setTitle("需要权限")
                .setMessage("本应用的核心功能（管理其他服务、自动保活）需要“写入安全设置”权限。\n\n请选择一种方式授权：")
                .setPositiveButton("复制ADB命令", (dialog, i) -> {
                    ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("cmd", "adb shell " + cmd));
                        Toast.makeText(context, "命令已复制到剪贴板，请连接电脑执行", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Root授权", (dialog, i) -> runRootCommand(context, cmd))
                .setNeutralButton("Shizuku授权", (dialog, i) -> requestShizukuPermission(context))
                .show();
    }

    /**
     * 通过 Root 执行授权命令
     */
    private static void runRootCommand(Context context, String cmd) {
        Process p = null;
        DataOutputStream o = null;
        try {
            p = Runtime.getRuntime().exec("su");
            o = new DataOutputStream(p.getOutputStream());
            o.writeBytes(cmd + "\nexit\n");
            o.flush();
            p.waitFor();
            if (p.exitValue() == 0) {
                Toast.makeText(context, "Root授权成功，请重试操作", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Root授权失败，退出码：" + p.exitValue(), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Root command failed", e);
            Toast.makeText(context, "Root不可用或请求被拒绝", Toast.LENGTH_SHORT).show();
        } finally {
            try {
                if (o != null) o.close();
            } catch (IOException ignored) {
            }
            if (p != null) p.destroy();
        }
    }

    /**
     * 请求 Shizuku 权限
     */
    private static void requestShizukuPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                runShizukuCommand(context);
            } else {
                Shizuku.requestPermission(0);
                // 授权结果将在 Activity 的 Listener 中回调
            }
        } catch (Exception e) {
            Log.e(TAG, "Shizuku not available", e);
            Toast.makeText(context, "未检测到Shizuku或版本过低", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 通过 Shizuku 执行授权命令
     */
    public static void runShizukuCommand(Context context) {
        String cmd = "pm grant " + context.getPackageName() + " android.permission.WRITE_SECURE_SETTINGS";
        try {
            Process p = Shizuku.newProcess(new String[]{"sh", "-c", cmd}, null, null);
            p.waitFor();
            if (p.exitValue() == 0) {
                Toast.makeText(context, "Shizuku授权成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Shizuku授权失败", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Shizuku command failed", e);
        }
    }
}