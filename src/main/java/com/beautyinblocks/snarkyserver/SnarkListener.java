package com.beautyinblocks.snarkyserver;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class SnarkListener implements Listener {
    private final JavaPlugin plugin;
    private final SnarkService snarkService;
    private final DeathCategoryClassifier deathCategoryClassifier;

    public SnarkListener(JavaPlugin plugin, SnarkService snarkService, DeathCategoryClassifier deathCategoryClassifier) {
        this.plugin = plugin;
        this.snarkService = snarkService;
        this.deathCategoryClassifier = deathCategoryClassifier;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        DeathCategory category = deathCategoryClassifier.classify(player);
        String killerName = player.getKiller() == null ? null : player.getKiller().getName();

        Component snark = snarkService.buildAutomaticDeathReply(player, category, killerName);
        if (snark != null) {
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(snark));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (!snarkService.shouldHandleChatAsync(plainMessage)) {
            return;
        }

        UUID playerId = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                return;
            }

            Component snark = snarkService.buildAutomaticChatReply(player, plainMessage);
            if (snark != null) {
                Bukkit.broadcast(snark);
            }
        });
    }
}
