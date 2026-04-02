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
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
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
        boolean startedWithBundledDefaults = false;
        try {
            reloadPluginState();
        } catch (IllegalStateException exception) {
            getLogger().log(Level.SEVERE,
                    "Snarky Server failed to load its live configuration during startup. Attempting bundled defaults so admin commands can still be used.",
                    exception);
            try {
                loadBundledPluginState();
                startedWithBundledDefaults = true;
            } catch (IllegalStateException fallbackException) {
                getLogger().log(Level.SEVERE,
                        "Snarky Server could not start even with bundled defaults. Disabling plugin.",
                        fallbackException);
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }

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

        if (startedWithBundledDefaults) {
            getLogger().warning("Snarky Server started with bundled defaults because the live config could not be loaded. Fix the config files on disk and run /snarkreload.");
        }
        getLogger().info("Snarky Server enabled.");
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
        getLogger().info("Snarky Server disabled.");
    }

    public void reloadPluginState() {
        LoadedConfigurations loadedConfigurations;
        try {
            loadedConfigurations = loadConfigurationsFromDisk();
        } catch (IOException | InvalidConfigurationException exception) {
            String failureMessage = "Failed to reload Snarky Server configuration files (messages.yml, chances.yml, triggers.yml).";
            if (snarkService != null) {
                failureMessage += " Keeping previous in-memory configuration.";
            } else {
                failureMessage += " Plugin startup cannot continue.";
            }
            getLogger().severe(failureMessage + " " + exception.getMessage());
            throw new IllegalStateException("Snarky Server configuration reload failed.", exception);
        }

        SnarkyConfig config = SnarkyConfigLoader.load(
                loadedConfigurations.messages(),
                loadedConfigurations.chances(),
                loadedConfigurations.triggers()
        );
        initializeServices(config, loadedConfigurations.triggers(), true);
    }

    private void loadBundledPluginState() {
        LoadedConfigurations loadedConfigurations;
        try {
            loadedConfigurations = loadBundledConfigurations();
        } catch (IOException exception) {
            throw new IllegalStateException("Snarky Server bundled defaults could not be loaded.", exception);
        }

        SnarkyConfig config = SnarkyConfigLoader.load(
                loadedConfigurations.messages(),
                loadedConfigurations.chances(),
                loadedConfigurations.triggers()
        );
        initializeServices(config, loadedConfigurations.triggers(), false);
    }

    private LoadedConfigurations loadConfigurationsFromDisk() throws IOException, InvalidConfigurationException {
        prepareConfigFiles();
        YamlConfiguration messagesConfiguration = loadConfigFile(MESSAGES_FILE);
        YamlConfiguration chancesConfiguration = loadConfigFile(CHANCES_FILE);
        YamlConfiguration triggersConfiguration = loadConfigFile(TRIGGERS_FILE);
        return new LoadedConfigurations(messagesConfiguration, chancesConfiguration, triggersConfiguration);
    }

    private LoadedConfigurations loadBundledConfigurations() throws IOException {
        YamlConfiguration messagesConfiguration = loadBundledConfigFile(MESSAGES_FILE);
        YamlConfiguration chancesConfiguration = loadBundledConfigFile(CHANCES_FILE);
        YamlConfiguration triggersConfiguration = loadBundledConfigFile(TRIGGERS_FILE);
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

    private YamlConfiguration loadBundledConfigFile(String fileName) throws IOException {
        try (Reader reader = getTextResource(fileName)) {
            if (reader == null) {
                throw new IOException("Missing bundled resource: " + fileName);
            }

            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    private void initializeServices(
            SnarkyConfig config,
            YamlConfiguration triggersConfiguration,
            boolean persistExternalOutputChanges
    ) {
        HandlerList.unregisterAll(this);

        logMissingGenericMessageWarnings(config);
        logConfigurationSummary(config);
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
                outputId -> externalOutputRegistry == null ? null : externalOutputRegistry.getToggle(outputId),
                getLogger()
        );
        externalOutputRegistry = new SnarkExternalOutputRegistry(
                this,
                triggersConfiguration,
                new File(getDataFolder(), TRIGGERS_FILE),
                persistExternalOutputChanges,
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
        logExternalOutputSummary();
    }

    private void prepareConfigFiles() throws IOException, InvalidConfigurationException {
        SnarkyConfigBootstrapper bootstrapper = new SnarkyConfigBootstrapper(
                new SnarkyConfigMigrator(getLogger()),
                this::writeMissingBundledResource,
                getLogger()
        );
        bootstrapper.prepareDataFolder(getDataFolder().toPath());
    }

    private void writeMissingBundledResource(String resourceName, Path targetPath) throws IOException {
        if (targetPath.toFile().isFile()) {
            return;
        }

        try {
            saveResource(resourceName, false);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Missing bundled resource: " + resourceName, exception);
        }
    }

    private void logMissingGenericMessageWarnings(SnarkyConfig config) {
        if (config.messagesConfig().deathMessagesFor(DeathCategory.GENERIC).isEmpty()) {
            getLogger().warning("messages.death-generic is empty; automatic death replies without category-specific entries will be skipped.");
        }
        if (config.messagesConfig().chatMessagesFor(ChatCategory.GENERIC).isEmpty()) {
            getLogger().warning("messages.chat-generic is empty; automatic chat replies without category-specific entries will be skipped.");
        }
    }

    private void logConfigurationSummary(SnarkyConfig config) {
        SnarkTriggersConfig triggersConfig = config.triggersConfig();
        getLogger().info("Snarky config: global=" + stateLabel(triggersConfig.enabled())
                + ", death=" + stateLabel(triggersConfig.deathSnark().enabled())
                + " (generic chance " + formatChance(config.chancesConfig().deathChanceFor(DeathCategory.GENERIC))
                + ", generic pool " + config.messagesConfig().deathMessagesFor(DeathCategory.GENERIC).size() + ")"
                + ", chat=" + stateLabel(triggersConfig.chatSnark().enabled())
                + " (generic chance " + formatChance(config.chancesConfig().chatChanceFor(ChatCategory.GENERIC))
                + ", generic pool " + config.messagesConfig().chatMessagesFor(ChatCategory.GENERIC).size()
                + ", min length " + triggersConfig.chatSnark().minMessageLength()
                + ", ignore commands " + yesNo(triggersConfig.chatSnark().ignoreCommands()) + ")");
        getLogger().info("Snarky filters: bypass permission=" + valueOrNone(triggersConfig.filters().bypassPermission())
                + ", ignored worlds=" + formatList(triggersConfig.filters().ignoredWorlds())
                + ", ignored prefixes=" + formatList(triggersConfig.filters().ignoredPrefixes())
                + ", cooldowns=global " + triggersConfig.cooldowns().globalSeconds()
                + "s / per-player " + triggersConfig.cooldowns().perPlayerSeconds() + "s");

        if (!triggersConfig.enabled()) {
            getLogger().warning("Snarky Server automatic replies are globally disabled in triggers.yml.");
        }
        if (!triggersConfig.deathSnark().enabled()) {
            getLogger().warning("Death snark is disabled in triggers.yml.");
        }
        if (!triggersConfig.chatSnark().enabled()) {
            getLogger().warning("Chat snark is disabled in triggers.yml.");
        }
        if (allDeathChancesDisabled(config)) {
            getLogger().warning("All death reply chances are set to 0%; automatic death replies will never trigger.");
        }
        if (allChatChancesDisabled(config)) {
            getLogger().warning("All chat reply chances are set to 0%; automatic chat replies will never trigger.");
        }
        if (config.chancesConfig().deathChanceFor(DeathCategory.GENERIC) <= 0.05D
                || config.chancesConfig().chatChanceFor(ChatCategory.GENERIC) <= 0.02D) {
            getLogger().info("Snarky Server is configured with low default reply chances; quiet stretches are expected unless chances.yml is increased.");
        }
    }

    private void logExternalOutputSummary() {
        if (externalOutputRegistry == null) {
            return;
        }

        List<SnarkExternalOutputRegistry.OutputStatus> outputs = externalOutputRegistry.listOutputs();
        long enabledCount = outputs.stream().filter(SnarkExternalOutputRegistry.OutputStatus::enabled).count();
        long activeCount = outputs.stream().filter(SnarkExternalOutputRegistry.OutputStatus::active).count();
        getLogger().info("Snarky external outputs: discovered=" + outputs.size()
                + ", enabled=" + enabledCount
                + ", active listeners=" + activeCount + ".");
    }

    private boolean allDeathChancesDisabled(SnarkyConfig config) {
        for (DeathCategory category : DeathCategory.values()) {
            if (config.chancesConfig().deathChanceFor(category) > 0.0D) {
                return false;
            }
        }
        return true;
    }

    private boolean allChatChancesDisabled(SnarkyConfig config) {
        for (ChatCategory category : ChatCategory.values()) {
            if (config.chancesConfig().chatChanceFor(category) > 0.0D) {
                return false;
            }
        }
        return true;
    }

    private String formatChance(double chance) {
        return String.format(java.util.Locale.US, "%.2f%%", Math.max(0.0D, Math.min(1.0D, chance)) * 100.0D);
    }

    private String formatList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "none";
        }
        return String.join(", ", values);
    }

    private String valueOrNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private String stateLabel(boolean enabled) {
        return enabled ? "enabled" : "disabled";
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
