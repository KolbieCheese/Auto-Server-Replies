package com.beautyinblocks.snarkyserver;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ChatCategoryClassifier {
    private static final Pattern GREETING_PATTERN = Pattern.compile(
            "^(hi|hello|hey|yo|howdy|greetings)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public ChatCategory classify(String messageText) {
        String normalized = messageText == null ? "" : messageText.trim();
        if (normalized.isEmpty()) {
            return ChatCategory.GENERIC;
        }

        String lower = normalized.toLowerCase(Locale.ROOT);

        if (isQuestion(normalized)) {
            return ChatCategory.QUESTION;
        }
        if (isExcited(normalized)) {
            return ChatCategory.EXCITED;
        }
        if (isGreeting(lower)) {
            return ChatCategory.GREETING;
        }

        return ChatCategory.GENERIC;
    }

    private boolean isQuestion(String normalized) {
        return normalized.endsWith("?")
                || normalized.contains("??")
                || normalized.contains("?!")
                || normalized.contains("!?");
    }

    private boolean isExcited(String normalized) {
        return normalized.endsWith("!")
                || normalized.contains("!!");
    }

    private boolean isGreeting(String normalizedLowerCase) {
        return GREETING_PATTERN.matcher(normalizedLowerCase).find();
    }
}
