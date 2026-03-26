package com.beautyinblocks.snarkyserver;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnarkFormatterTest {
    @Test
    void formatsServerPrefixAndEscapesPlaceholders() {
        SnarkFormatter formatter = new SnarkFormatter("<white>[Server] <reset>");

        Component component = formatter.format("Hello, {player}.", Map.of("player", "<green>Kolbie"));

        assertEquals("[Server] Hello, <green>Kolbie.", PlainTextComponentSerializer.plainText().serialize(component));
    }
}
