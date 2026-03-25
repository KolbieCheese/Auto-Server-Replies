package com.beautyinblocks.snarkyserver;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

public final class SnarkyServerPlugin extends JavaPlugin {
    private CooldownManager cooldownManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        initializeServices();
        getLogger().info("SnarkyServerPlugin enabled.");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        if (cooldownManager != null) {
            cooldownManager.clear();
        }
        getLogger().info("SnarkyServerPlugin disabled.");
    }

    private void initializeServices() {
        HandlerList.unregisterAll(this);
        reloadConfig();

        SnarkyConfig config = SnarkyConfigLoader.load(getConfig());
        cooldownManager = new CooldownManager(config.cooldowns());
        SnarkFormatter formatter = new SnarkFormatter(config.prefix());
        SnarkService snarkService = new SnarkService(
                ThreadLocalRandom.current(),
                cooldownManager,
                formatter,
                config
        );

        Bukkit.getPluginManager().registerEvents(new SnarkListener(this, snarkService), this);
    }
}
