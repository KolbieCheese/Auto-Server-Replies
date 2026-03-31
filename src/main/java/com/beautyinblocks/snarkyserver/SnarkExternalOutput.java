package com.beautyinblocks.snarkyserver;

import java.util.Locale;

public record SnarkExternalOutput(
        String id,
        String displayName,
        String sourcePlugin,
        String eventClass,
        String kind,
        String description
) {
    public SnarkExternalOutput {
        id = normalizeRequired(id, "id");
        displayName = normalizeRequired(displayName, "displayName");
        sourcePlugin = normalizeRequired(sourcePlugin, "sourcePlugin");
        eventClass = normalizeRequired(eventClass, "eventClass");
        kind = normalizeRequired(kind, "kind").toLowerCase(Locale.ROOT);
        description = normalize(description);
    }

    public boolean isChatKind() {
        return "chat".equals(kind);
    }

    private static String normalizeRequired(String value, String fieldName) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
