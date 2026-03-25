package com.beautyinblocks.snarkyserver;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Map;

public final class SnarkFormatter {
    private final String prefix;
    private final MiniMessage miniMessage;

    public SnarkFormatter(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public Component format(String template, Map<String, String> values) {
        String formatted = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String safeValue = miniMessage.escapeTags(entry.getValue() == null ? "" : entry.getValue());
            formatted = formatted.replace("{" + entry.getKey() + "}", safeValue);
        }
        return miniMessage.deserialize(prefix + formatted);
    }
}
