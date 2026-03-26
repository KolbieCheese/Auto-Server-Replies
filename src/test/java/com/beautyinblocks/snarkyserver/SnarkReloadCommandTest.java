package com.beautyinblocks.snarkyserver;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnarkReloadCommandTest {
    @Test
    void reloadsWhenAuthorized() {
        AtomicInteger reloadCount = new AtomicInteger();
        SnarkReloadCommand command = new SnarkReloadCommand(reloadCount::incrementAndGet, Logger.getLogger("test"));
        CommandSender sender = mock(CommandSender.class);
        Command bukkitCommand = mock(Command.class);
        when(sender.hasPermission("snarkyserver.reload")).thenReturn(true);

        command.onCommand(sender, bukkitCommand, "snarkreload", new String[0]);

        verify(sender).sendMessage(contains("reloaded"));
        assertEquals(1, reloadCount.get());
    }
}
