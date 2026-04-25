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

    public static final int REQUEST_CODE_SHIZUKU = 1002;

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
                .setTitle(R.string.permission_dialog_title)
                .setMessage(R.string.permission_dialog_message)
                .setPositiveButton(R.string.permission_dialog_action_copy_adb, (dialog, i) -> {
                    ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("cmd", "adb shell " + cmd));
                        Toast.makeText(context, R.string.permission_dialog_copy_success, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.permission_dialog_action_root, (dialog, i) -> runRootCommand(context, cmd))
                .setNeutralButton(R.string.permission_dialog_action_shizuku, (dialog, i) -> requestShizukuPermission(context, REQUEST_CODE_SHIZUKU))
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
                Toast.makeText(context, R.string.permission_dialog_root_success, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, context.getString(R.string.permission_dialog_root_failed, p.exitValue()), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Root command failed", e);
            Toast.makeText(context, R.string.permission_dialog_root_unavailable, Toast.LENGTH_SHORT).show();
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
    public static void requestShizukuPermission(Context context, int requestCode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                runShizukuCommand(context);
            } else {
                Shizuku.requestPermission(requestCode);
                // 授权结果将在 Activity 的 Listener 中回调
            }
        } catch (Exception e) {
            Log.e(TAG, "Shizuku not available", e);
            Toast.makeText(context, R.string.permission_dialog_shizuku_unavailable, Toast.LENGTH_SHORT).show();
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
                Toast.makeText(context, R.string.permission_dialog_shizuku_success, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, R.string.permission_dialog_shizuku_failed, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Shizuku command failed", e);
        }
    }
}