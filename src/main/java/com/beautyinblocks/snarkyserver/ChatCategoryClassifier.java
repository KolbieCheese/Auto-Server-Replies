package com.beautyinblocks.snarkyserver;

import java.util.Locale;

public final class ChatCategoryClassifier {
    public ChatCategory classify(String messageText) {
        String normalized = messageText == null ? "" : messageText.trim();
        if (normalized.isEmpty()) {
            return ChatCategory.GENERIC;
        }

        String lower = normalized.toLowerCase(Locale.ROOT);

        // Deterministic string checks kept intentionally conservative.
        // Extension points: return QUESTION/EXCITED/GREETING once dedicated pools/chances are configured.
        if (normalized.endsWith("?")) {
            return ChatCategory.GENERIC;
        }
        if (normalized.endsWith("!") || normalized.contains("!!")) {
            return ChatCategory.GENERIC;
        }
        if (lower.startsWith("hi") || lower.startsWith("hello") || lower.startsWith("hey")) {
            return ChatCategory.GENERIC;
        }

        return ChatCategory.GENERIC;
    }
}
