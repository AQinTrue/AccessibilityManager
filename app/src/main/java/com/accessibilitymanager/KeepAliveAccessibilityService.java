package com.accessibilitymanager;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * 空无障碍服务 (保活组件)
 * <p>
 * 该服务不执行任何逻辑，仅用于提升 APP 的进程优先级 (Perceptible)。
 * 当此服务开启时，系统极少查杀本应用，从而保证守护服务 (DaemonService) 的稳定运行。
 */
public class KeepAliveAccessibilityService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 空实现：不处理任何事件，减少资源消耗
    }

    @Override
    public void onInterrupt() {
        // 服务中断回调
    }
}