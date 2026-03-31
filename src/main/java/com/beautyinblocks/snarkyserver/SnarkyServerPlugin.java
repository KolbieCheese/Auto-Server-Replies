package com.beautyinblocks.snarkyserver;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.ThreadLocalRandom;

public final class SnarkyServerPlugin extends JavaPlugin {
    private static final String MESSAGES_FILE = "messages.yml";
    private static final String CHANCES_FILE = "chances.yml";
    private static final String TRIGGERS_FILE = "triggers.yml";

    private CooldownManager cooldownManager;
    private ChatBurstTracker chatBurstTracker;
    private SnarkService snarkService;
    private SnarkExternalOutputRegistry externalOutputRegistry;

    @Override
    public void onEnable() {
        bootstrapConfigResources();
        reloadPluginState();

        PluginCommand reloadCommand = getCommand("snarkreload");
        if (reloadCommand != null) {
            reloadCommand.setExecutor(new SnarkReloadCommand(this::reloadPluginState, getLogger()));
        } else {
            getLogger().warning("Command 'snarkreload' was not found in plugin.yml; reload command is unavailable.");
        }

        PluginCommand testCommand = getCommand("snarktest");
        if (testCommand != null) {
            SnarkTestCommand executor = new SnarkTestCommand(
                    getServer(),
                    this::getSnarkService,
                    this::getCooldownManager,
                    this::getExternalOutputRegistry
            );
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
        LoadedConfigurations loadedConfigurations;
        try {
            loadedConfigurations = loadConfigurationsFromDisk();
        } catch (IOException | InvalidConfigurationException exception) {
            getLogger().severe("Failed to reload SnarkyServer configuration files (messages.yml, chances.yml, triggers.yml)."
                    + " Keeping previous in-memory configuration. " + exception.getMessage());
            throw new IllegalStateException("SnarkyServer configuration reload failed.", exception);
        }

        SnarkyConfig config = SnarkyConfigLoader.load(
                loadedConfigurations.messages(),
                loadedConfigurations.chances(),
                loadedConfigurations.triggers()
        );
        initializeServices(config, loadedConfigurations.triggers());
    }

    private LoadedConfigurations loadConfigurationsFromDisk() throws IOException, InvalidConfigurationException {
        YamlConfiguration messagesConfiguration = loadConfigFile(MESSAGES_FILE);
        YamlConfiguration chancesConfiguration = loadConfigFile(CHANCES_FILE);
        YamlConfiguration triggersConfiguration = loadConfigFile(TRIGGERS_FILE);
        return new LoadedConfigurations(messagesConfiguration, chancesConfiguration, triggersConfiguration);
    }

    private YamlConfiguration loadConfigFile(String fileName) throws IOException, InvalidConfigurationException {
        File file = new File(getDataFolder(), fileName);
        if (!file.isFile()) {
            throw new IOException("Missing required file: " + file.getAbsolutePath());
        }

        YamlConfiguration configuration = new YamlConfiguration();
        configuration.load(file);
        return configuration;
    }

    private void initializeServices(SnarkyConfig config, YamlConfiguration triggersConfiguration) {
        HandlerList.unregisterAll(this);

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
        SnarkExternalOutputManifestLoader manifestLoader = new SnarkExternalOutputManifestLoader(getLogger());
        SnarkExternalChatEventBridge chatEventBridge = new SnarkExternalChatEventBridge(
                this,
                snarkService,
                outputId -> externalOutputRegistry != null && externalOutputRegistry.isOutputEnabled(outputId),
                getLogger()
        );
        externalOutputRegistry = new SnarkExternalOutputRegistry(
                this,
                triggersConfiguration,
                new File(getDataFolder(), TRIGGERS_FILE),
                config.triggersConfig(),
                manifestLoader,
                chatEventBridge,
                getLogger()
        );

        Bukkit.getPluginManager().registerEvents(
                new SnarkListener(this, snarkService),
                this
        );
        Bukkit.getPluginManager().registerEvents(
                new SnarkExternalOutputDiscoveryListener(externalOutputRegistry),
                this
        );
        externalOutputRegistry.discoverLoadedPlugins();
    }

    private void bootstrapConfigResources() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create plugin data directory at " + getDataFolder().getAbsolutePath());
        }

        saveResource(MESSAGES_FILE, false);
        saveResource(CHANCES_FILE, false);
        saveResource(TRIGGERS_FILE, false);
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

    public SnarkExternalOutputRegistry getExternalOutputRegistry() {
        return externalOutputRegistry;
    }

    private SnarkMessagesConfig loadBundledTestMessageFallbacks(SnarkMessagesConfig liveMessages) {
        try (Reader defaultMessagesReader = getTextResource(MESSAGES_FILE)) {
            if (defaultMessagesReader == null) {
                getLogger().warning("Bundled messages.yml is missing; /snarktest will use only the live config message pools.");
                return liveMessages;
            }

            SnarkMessagesConfig bundledMessages = SnarkyConfigLoader
                    .loadMessages(YamlConfiguration.loadConfiguration(defaultMessagesReader));
            if (bundledMessages.deathMessagesFor(DeathCategory.GENERIC).isEmpty()
                    && bundledMessages.chatMessagesFor(ChatCategory.GENERIC).isEmpty()) {
                getLogger().warning("Bundled messages.yml did not provide default test message pools; /snarktest will use only the live config message pools.");
                return liveMessages;
            }

            return bundledMessages;
        } catch (IOException exception) {
            getLogger().warning("Failed to read bundled messages.yml for /snarktest defaults: " + exception.getMessage());
            return liveMessages;
        }
    }

    private record LoadedConfigurations(
            YamlConfiguration messages,
            YamlConfiguration chances,
            YamlConfiguration triggers
    ) {
    }
}
