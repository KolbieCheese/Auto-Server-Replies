package com.beautyinblocks.snarkyserver;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.Reader;
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
        initializeServices();
    }

    private void initializeServices() {
        HandlerList.unregisterAll(this);

        SnarkyConfig config = SnarkyConfigLoader.load(getConfig());
        logMissingGenericMessageWarnings(config);
        cooldownManager = new CooldownManager(config.triggersConfig().cooldowns());
        chatBurstTracker = new ChatBurstTracker();
        SnarkFormatter formatter = new SnarkFormatter(config.prefix());
        DeathCategoryClassifier deathCategoryClassifier = new DeathCategoryClassifier();
        ChatCategoryClassifier chatCategoryClassifier = new ChatCategoryClassifier();
        PlayerVisibilityChecker playerVisibilityChecker = new PlayerVisibilityChecker(getServer());
        SnarkMessagesConfig testMessageFallbacks = loadBundledTestMessageFallbacks(config.messagesConfig());
        snarkService = new SnarkService(
                ThreadLocalRandom.current(),
                cooldownManager,
                formatter,
                config,
                deathCategoryClassifier,
                chatCategoryClassifier,
                chatBurstTracker,
                playerVisibilityChecker,
                testMessageFallbacks
        );

        Bukkit.getPluginManager().registerEvents(
                new SnarkListener(this, snarkService),
                this
        );
    }

    private void logMissingGenericMessageWarnings(SnarkyConfig config) {
        if (config.messagesConfig().deathMessagesFor(DeathCategory.GENERIC).isEmpty()) {
            getLogger().warning("messages.death-generic is empty; automatic death replies without category-specific entries will be skipped.");
        }
        if (config.messagesConfig().chatMessagesFor(ChatCategory.GENERIC).isEmpty()) {
            getLogger().warning("messages.chat-generic is empty; automatic chat replies without category-specific entries will be skipped.");
        }
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public SnarkService getSnarkService() {
        return snarkService;
    }

    private SnarkMessagesConfig loadBundledTestMessageFallbacks(SnarkMessagesConfig liveMessages) {
        try (Reader defaultConfigReader = getTextResource("config.yml")) {
            if (defaultConfigReader == null) {
                getLogger().warning("Bundled config.yml is missing; /snarktest will use only the live config message pools.");
                return liveMessages;
            }

            SnarkMessagesConfig bundledMessages = SnarkyConfigLoader
                    .load(YamlConfiguration.loadConfiguration(defaultConfigReader))
                    .messagesConfig();
            if (bundledMessages.deathMessagesFor(DeathCategory.GENERIC).isEmpty()
                    && bundledMessages.chatMessagesFor(ChatCategory.GENERIC).isEmpty()) {
                getLogger().warning("Bundled config.yml did not provide default test message pools; /snarktest will use only the live config message pools.");
                return liveMessages;
            }

            return bundledMessages;
        } catch (IOException exception) {
            getLogger().warning("Failed to read bundled config.yml for /snarktest defaults: " + exception.getMessage());
            return liveMessages;
        }
    }
}
