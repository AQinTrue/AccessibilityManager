package com.accessibilitymanager;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.DataOutputStream;

import rikka.shizuku.Shizuku;

public class PermissionUtils {

    public static boolean hasSecureSettingsPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
        }
        return false;
    }

    public static void showPermissionDialog(Context context) {
        String cmd = "pm grant " + context.getPackageName() + " android.permission.WRITE_SECURE_SETTINGS";
        new MaterialAlertDialogBuilder(context)
                .setTitle("需要写入安全设置权限")
                .setMessage("本应用需要该权限才能管理无障碍服务。\n\n方法1：Root用户直接授权\n方法2：使用Shizuku授权\n方法3：连接电脑ADB执行命令")
                .setPositiveButton("复制ADB命令", (dialog, i) -> {
                    ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("cmd", "adb shell " + cmd));
                    Toast.makeText(context, "命令已复制", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Root授权", (dialog, i) -> runRootCommand(context, cmd))
                .setNeutralButton("Shizuku授权", (dialog, i) -> requestShizukuPermission(context))
                .show();
    }

    private static void runRootCommand(Context context, String cmd) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream o = new DataOutputStream(p.getOutputStream());
            o.writeBytes(cmd + "\nexit\n");
            o.flush();
            o.close();
            p.waitFor();
            if (p.exitValue() == 0) {
                Toast.makeText(context, "Root授权成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Root授权失败", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "Root不可用", Toast.LENGTH_SHORT).show();
        }
    }

    private static void requestShizukuPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                runShizukuCommand(context);
            } else {
                Shizuku.requestPermission(0);
                // 注意：需要在Activity中监听回调，这里简化处理，用户再次点击即可
            }
        } catch (Exception e) {
            Toast.makeText(context, "未检测到Shizuku或版本过低", Toast.LENGTH_SHORT).show();
        }
    }

    public static void runShizukuCommand(Context context) {
        String cmd = "pm grant " + context.getPackageName() + " android.permission.WRITE_SECURE_SETTINGS";
        try {
            Process p = Shizuku.newProcess(new String[]{"sh", "-c", cmd}, null, null);
            p.waitFor();
            if (p.exitValue() == 0) {
                Toast.makeText(context, "Shizuku授权成功", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}