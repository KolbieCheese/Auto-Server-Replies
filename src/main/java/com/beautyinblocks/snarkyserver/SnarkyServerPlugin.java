package com.beautyinblocks.snarkyserver;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

public final class SnarkyServerPlugin extends JavaPlugin {
    private CooldownManager cooldownManager;
    private ChatBurstTracker chatBurstTracker;
    private SnarkService snarkService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadPluginState();

        PluginCommand reloadCommand = getCommand("snarkreload");
        if (reloadCommand != null) {
            reloadCommand.setExecutor(new SnarkReloadCommand(this::reloadPluginState, getLogger()));
        } else {
            getLogger().warning("Command 'snarkreload' was not found in plugin.yml; reload command is unavailable.");
        }

        PluginCommand testCommand = getCommand("snarktest");
        if (testCommand != null) {
            SnarkTestCommand executor = new SnarkTestCommand(getServer(), this::getSnarkService, this::getCooldownManager);
            testCommand.setExecutor(executor);
            testCommand.setTabCompleter(executor);
        } else {
            getLogger().warning("Command 'snarktest' was not found in plugin.yml; test command is unavailable.");
        }

        getLogger().info("SnarkyServerPlugin enabled.");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        if (cooldownManager != null) {
            cooldownManager.clear();
        }
        if (chatBurstTracker != null) {
            chatBurstTracker.clear();
        }
        getLogger().info("SnarkyServerPlugin disabled.");
    }

    public void reloadPluginState() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        initializeServices();
    }

    private void initializeServices() {
        HandlerList.unregisterAll(this);

        SnarkyConfig config = SnarkyConfigLoader.load(getConfig());
        cooldownManager = new CooldownManager(config.cooldowns());
        chatBurstTracker = new ChatBurstTracker();
        SnarkFormatter formatter = new SnarkFormatter(config.prefix());
        DeathCategoryClassifier deathCategoryClassifier = new DeathCategoryClassifier();
        ChatCategoryClassifier chatCategoryClassifier = new ChatCategoryClassifier();
        snarkService = new SnarkService(
                ThreadLocalRandom.current(),
                cooldownManager,
                formatter,
                config,
                chatCategoryClassifier,
                chatBurstTracker
        );

        Bukkit.getPluginManager().registerEvents(
                new SnarkListener(this, snarkService, deathCategoryClassifier),
                this
        );
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public SnarkService getSnarkService() {
        return snarkService;
    }
}
