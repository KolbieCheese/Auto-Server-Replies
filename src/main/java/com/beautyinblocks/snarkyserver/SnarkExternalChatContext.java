package com.beautyinblocks.snarkyserver;

public record SnarkExternalChatContext(
        String outputId,
        String outputDisplayName,
        String sourcePlugin,
        String kind,
        String clanName,
        String clanTag,
        boolean toggleRouted,
        int recipientCount
) {
    public SnarkExternalChatContext {
        outputId = normalize(outputId);
        outputDisplayName = normalize(outputDisplayName);
        sourcePlugin = normalize(sourcePlugin);
        kind = normalize(kind);
        clanName = normalize(clanName);
        clanTag = normalize(clanTag);
        recipientCount = Math.max(0, recipientCount);
    }

    public boolean isExternal() {
        return !outputId.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
