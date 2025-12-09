package com.accessibilitymanager;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.view.accessibility.AccessibilityEvent;

@SuppressLint("AccessibilityPolicy")
public class KeepAliveAccessibilityService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不需要处理任何事件，空实现即可
    }

    @Override
    public void onInterrupt() {
    }
}