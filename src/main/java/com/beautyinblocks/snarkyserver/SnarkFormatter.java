package com.beautyinblocks.snarkyserver;

import org.bukkit.ChatColor;

import java.util.Map;

public final class SnarkFormatter {
    private final String prefix;

    public SnarkFormatter(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
    }

    public String format(String template, Map<String, String> values) {
        String formatted = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return ChatColor.translateAlternateColorCodes('&', prefix + formatted);
    }
}
