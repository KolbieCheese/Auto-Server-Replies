package com.beautyinblocks.snarkyserver;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ChatCategoryClassifier {
    private static final Pattern LAG_PATTERN = Pattern.compile(
            "\\b(lag(?:ging)?|laggy|rubberband(?:ing)?|desync|tps)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CONFUSION_PATTERN = Pattern.compile(
            "\\b(how do i|how to|where do i|what do i do|why can't i|why cant i|what is|how does)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern GREETING_PATTERN = Pattern.compile(
            "^(hi|hello|hey|yo|howdy|greetings)(\\s+(all|everyone|guys|chat|server|there|yall|y'all))?[!.,?]*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CELEBRATION_PATTERN = Pattern.compile(
            "\\b(let's go|lets go|gg|nice|finally|i did it|we did it|got it|we won|i won|clutch)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BRAG_PATTERN = Pattern.compile(
            "\\b(too easy|ez|light work|i win|i won|carried|owned|i('?m| am)? (so )?(best|better|cracked|stacked|rich))\\b",
            Pattern.CASE_INSENSITIVE
    );

    public ChatCategory classify(String messageText, boolean spamBurstTriggered) {
        String normalized = messageText == null ? "" : messageText.trim();
        if (normalized.isEmpty()) {
            return ChatCategory.GENERIC;
        }

        String lower = normalized.toLowerCase(Locale.ROOT);

        if (isLag(lower)) {
            return ChatCategory.LAG;
        }
        if (isConfused(lower)) {
            return ChatCategory.CONFUSION;
        }
        if (isGreeting(normalized)) {
            return ChatCategory.GREETING;
        }
        if (isCelebration(lower)) {
            return ChatCategory.CELEBRATION;
        }
        if (isBrag(lower)) {
            return ChatCategory.BRAG;
        }
        if (isQuestion(normalized)) {
            return ChatCategory.QUESTION;
        }
        if (isExcited(normalized)) {
            return ChatCategory.EXCITED;
        }
        if (spamBurstTriggered) {
            return ChatCategory.SPAM;
        }

        return ChatCategory.GENERIC;
    }

    boolean isQuestion(String normalized) {
        return normalized.endsWith("?")
                || normalized.contains("??")
                || normalized.contains("?!")
                || normalized.contains("!?");
    }

    boolean isExcited(String normalized) {
        return normalized.endsWith("!")
                || normalized.contains("!!");
    }

    boolean isLag(String normalizedLowerCase) {
        return LAG_PATTERN.matcher(normalizedLowerCase).find();
    }

    boolean isConfused(String normalizedLowerCase) {
        return CONFUSION_PATTERN.matcher(normalizedLowerCase).find();
    }

    boolean isGreeting(String normalized) {
        return normalized.length() <= 24 && GREETING_PATTERN.matcher(normalized).matches();
    }

    boolean isCelebration(String normalizedLowerCase) {
        return CELEBRATION_PATTERN.matcher(normalizedLowerCase).find();
    }

    boolean isBrag(String normalizedLowerCase) {
        return BRAG_PATTERN.matcher(normalizedLowerCase).find();
    }
}
