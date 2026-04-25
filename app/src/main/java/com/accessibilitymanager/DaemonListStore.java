package com.accessibilitymanager;

import android.content.SharedPreferences;

import java.util.LinkedHashSet;
import java.util.Set;

public final class DaemonListStore {

    private DaemonListStore() {
    }

    public static Set<String> parseIds(String raw) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (raw == null || raw.isEmpty()) {
            return ids;
        }
        String[] segments = raw.split(":");
        for (String segment : segments) {
            if (segment != null && !segment.isEmpty()) {
                ids.add(segment);
            }
        }
        return ids;
    }

    public static String toRawString(Set<String> ids) {
        StringBuilder builder = new StringBuilder();
        for (String id : ids) {
            if (id != null && !id.isEmpty()) {
                builder.append(id).append(':');
            }
        }
        return builder.toString();
    }

    public static boolean containsId(String raw, String targetId) {
        return parseIds(raw).contains(targetId);
    }

    public static String addId(String raw, String targetId) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(parseIds(raw));
        if (targetId != null && !targetId.isEmpty()) {
            ids.add(targetId);
        }
        return toRawString(ids);
    }

    public static String removeId(String raw, String targetId) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(parseIds(raw));
        ids.remove(targetId);
        return toRawString(ids);
    }

    public static Set<String> readIds(SharedPreferences sharedPreferences) {
        return parseIds(sharedPreferences.getString(AppConstants.KEY_DAEMON_LIST, ""));
    }

    public static String writeIds(SharedPreferences sharedPreferences, Set<String> ids) {
        String raw = toRawString(ids);
        sharedPreferences.edit().putString(AppConstants.KEY_DAEMON_LIST, raw).apply();
        return raw;
    }
}
