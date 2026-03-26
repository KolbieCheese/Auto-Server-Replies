package com.beautyinblocks.snarkyserver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatCategoryClassifierTest {
    private final ChatCategoryClassifier classifier = new ChatCategoryClassifier();

    @Test
    void respectsConfiguredPrecedence() {
        assertEquals(ChatCategory.LAG, classifier.classify("is the server lagging??", false));
        assertEquals(ChatCategory.CONFUSION, classifier.classify("how do I get home?", false));
        assertEquals(ChatCategory.GREETING, classifier.classify("hello", false));
        assertEquals(ChatCategory.CELEBRATION, classifier.classify("let's go!!", false));
        assertEquals(ChatCategory.BRAG, classifier.classify("too easy?", false));
        assertEquals(ChatCategory.QUESTION, classifier.classify("any idea?", false));
        assertEquals(ChatCategory.EXCITED, classifier.classify("amazing!", false));
        assertEquals(ChatCategory.SPAM, classifier.classify("ok", true));
        assertEquals(ChatCategory.CONFUSION, classifier.classify("hello how do I leave", false));
    }
}
