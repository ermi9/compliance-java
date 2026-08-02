package dev.praxis.core.check;

import java.util.List;
import java.util.Map;

/**
 * Typed, read-only view over the options the professor's ruleset supplies for one check. Options
 * tune a check; they never contain logic (invariant 6: logic lives in Java, config in the ruleset).
 */
public final class CheckOptions {

    private static final CheckOptions EMPTY = new CheckOptions(Map.of());

    private final Map<String, Object> raw;

    private CheckOptions(Map<String, Object> raw) {
        this.raw = Map.copyOf(raw);
    }

    public static CheckOptions of(Map<String, Object> raw) {
        return raw == null || raw.isEmpty() ? EMPTY : new CheckOptions(raw);
    }

    public static CheckOptions empty() {
        return EMPTY;
    }

    public boolean getBoolean(String key, boolean fallback) {
        Object v = raw.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return fallback;
    }

    @SuppressWarnings("unchecked")
    public List<String> getStringList(String key) {
        Object v = raw.get(key);
        if (v instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
