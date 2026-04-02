package com.beautyinblocks.snarkyserver;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

public class SnarkExternalOutputRegistry {
    private final JavaPlugin plugin;
    private final YamlConfiguration triggersConfiguration;
    private final File triggersFile;
    private final boolean persistToDisk;
    private final SnarkExternalOutputManifestLoader manifestLoader;
    private final SnarkExternalChatEventBridge chatEventBridge;
    private final Logger logger;
    private final Map<String, SnarkTriggersConfig.ExternalOutputToggle> toggles = new LinkedHashMap<>();
    private final Map<String, RegisteredOutput> registeredOutputs = new LinkedHashMap<>();
    private final Map<String, Set<String>> outputsByPlugin = new LinkedHashMap<>();

    public SnarkExternalOutputRegistry(
            JavaPlugin plugin,
            YamlConfiguration triggersConfiguration,
            File triggersFile,
            boolean persistToDisk,
            SnarkTriggersConfig triggersConfig,
            SnarkExternalOutputManifestLoader manifestLoader,
            SnarkExternalChatEventBridge chatEventBridge,
            Logger logger
    ) {
        this.plugin = plugin;
        this.triggersConfiguration = triggersConfiguration;
        this.triggersFile = triggersFile;
        this.persistToDisk = persistToDisk;
        this.manifestLoader = manifestLoader;
        this.chatEventBridge = chatEventBridge;
        this.logger = logger;
        this.toggles.putAll(triggersConfig.externalOutputs());
    }

    public void discoverLoadedPlugins() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        for (Plugin loadedPlugin : pluginManager.getPlugins()) {
            discoverPlugin(loadedPlugin);
        }
    }

    public void discoverPlugin(Plugin sourcePlugin) {
        if (sourcePlugin == null || !sourcePlugin.isEnabled()) {
            return;
        }

        Optional<SnarkExternalOutputManifest> manifest = manifestLoader.load(sourcePlugin);
        if (manifest.isEmpty()) {
            return;
        }

        for (SnarkExternalOutput output : manifest.get().outputs()) {
            registerOutput(sourcePlugin, output);
        }
    }

    public void handlePluginDisable(Plugin sourcePlugin) {
        if (sourcePlugin == null) {
            return;
        }

        Set<String> outputIds = outputsByPlugin.getOrDefault(sourcePlugin.getName(), Set.of());
        for (String outputId : outputIds) {
            RegisteredOutput registeredOutput = registeredOutputs.get(outputId);
            if (registeredOutput == null || registeredOutput.listener() == null) {
                continue;
            }

            HandlerList.unregisterAll(registeredOutput.listener());
            registeredOutputs.put(outputId, registeredOutput.deactivate());
        }
    }

    public boolean isOutputEnabled(String outputId) {
        SnarkTriggersConfig.ExternalOutputToggle toggle = toggles.get(outputId);
        return toggle != null && toggle.enabled();
    }

    public SnarkTriggersConfig.ExternalOutputToggle getToggle(String outputId) {
        return toggles.get(outputId);
    }

    public List<OutputStatus> listOutputs() {
        List<OutputStatus> statuses = new ArrayList<>();
        for (RegisteredOutput registeredOutput : registeredOutputs.values()) {
            SnarkTriggersConfig.ExternalOutputToggle toggle = toggles.get(registeredOutput.output().id());
            statuses.add(new OutputStatus(
                    registeredOutput.output().id(),
                    registeredOutput.output().displayName(),
                    registeredOutput.output().sourcePlugin(),
                    registeredOutput.output().kind(),
                    registeredOutput.output().eventClass(),
                    registeredOutput.output().description(),
                    toggle != null && toggle.enabled(),
                    registeredOutput.listener() != null
            ));
        }

        statuses.sort(Comparator.comparing(OutputStatus::id, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(statuses);
    }

    boolean hasActiveListener(String outputId) {
        RegisteredOutput registeredOutput = registeredOutputs.get(outputId);
        return registeredOutput != null && registeredOutput.listener() != null;
    }

    private void registerOutput(Plugin sourcePlugin, SnarkExternalOutput output) {
        RegisteredOutput existingOutput = registeredOutputs.get(output.id());
        if (existingOutput != null && !existingOutput.output().sourcePlugin().equals(sourcePlugin.getName())) {
            logger.warning("Snarky external output id '" + output.id() + "' from plugin '" + sourcePlugin.getName()
                    + "' conflicts with already-registered plugin '" + existingOutput.output().sourcePlugin() + "'.");
            return;
        }

        Class<? extends Event> eventClass = resolveEventClass(sourcePlugin, output);
        if (eventClass == null) {
            return;
        }

        Listener listener = existingOutput != null ? existingOutput.listener() : null;
        if (listener == null) {
            listener = new Listener() {
            };
            EventExecutor eventExecutor = chatEventBridge.createExecutor(output);
            plugin.getServer().getPluginManager().registerEvent(
                    eventClass,
                    listener,
                    EventPriority.MONITOR,
                    eventExecutor,
                    plugin,
                    true
            );
        }

        registeredOutputs.put(output.id(), new RegisteredOutput(output, eventClass, listener));
        outputsByPlugin.computeIfAbsent(sourcePlugin.getName(), ignored -> new LinkedHashSet<>()).add(output.id());
        persistToggle(output);
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Event> resolveEventClass(Plugin sourcePlugin, SnarkExternalOutput output) {
        try {
            Class<?> rawEventClass = Class.forName(output.eventClass(), false, sourcePlugin.getClass().getClassLoader());
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                logger.warning("Snarky external output '" + output.id() + "' from plugin '" + sourcePlugin.getName()
                        + "' declared event class '" + output.eventClass() + "', but it does not extend Bukkit Event.");
                return null;
            }
            return (Class<? extends Event>) rawEventClass;
        } catch (ClassNotFoundException exception) {
            logger.warning("Snarky external output '" + output.id() + "' from plugin '" + sourcePlugin.getName()
                    + "' could not load event class '" + output.eventClass() + "'.");
            return null;
        } catch (LinkageError error) {
            logger.warning("Snarky external output '" + output.id() + "' from plugin '" + sourcePlugin.getName()
                    + "' failed to link event class '" + output.eventClass() + "': " + error.getMessage());
            return null;
        }
    }

    private void persistToggle(SnarkExternalOutput output) {
        SnarkTriggersConfig.ExternalOutputToggle currentToggle = toggles.get(output.id());
        boolean enabled = currentToggle != null && currentToggle.enabled();
        SnarkTriggersConfig.ExternalOutputToggle.DiscordSrvForwarding discordsrv = currentToggle != null
                ? currentToggle.discordsrv()
                : defaultDiscordSrvForwarding(output);
        SnarkTriggersConfig.ExternalOutputToggle updatedToggle = new SnarkTriggersConfig.ExternalOutputToggle(
                enabled,
                output.displayName(),
                output.sourcePlugin(),
                output.kind(),
                output.eventClass(),
                output.description(),
                discordsrv
        );
        toggles.put(output.id(), updatedToggle);

        if (!persistToDisk) {
            return;
        }

        String path = "external-outputs." + output.id();
        boolean changed = false;
        changed |= setIfChanged(path + ".enabled", updatedToggle.enabled());
        changed |= setIfChanged(path + ".display-name", updatedToggle.displayName());
        changed |= setIfChanged(path + ".source-plugin", updatedToggle.sourcePlugin());
        changed |= setIfChanged(path + ".kind", updatedToggle.kind());
        changed |= setIfChanged(path + ".event-class", updatedToggle.eventClass());
        changed |= setIfChanged(path + ".description", updatedToggle.description());
        changed |= setIfChanged(path + ".discordsrv.enabled", updatedToggle.discordsrv().enabled());
        changed |= setIfChanged(path + ".discordsrv.channel", updatedToggle.discordsrv().channel());

        if (!changed) {
            return;
        }

        try {
            triggersConfiguration.save(triggersFile);
        } catch (IOException exception) {
            logger.warning("Failed to persist Snarky external output '" + output.id()
                    + "' to triggers.yml: " + exception.getMessage());
        }
    }

    private boolean setIfChanged(String path, Object value) {
        Object existing = triggersConfiguration.get(path);
        if ((existing == null && value == null) || (existing != null && existing.equals(value))) {
            return false;
        }

        triggersConfiguration.set(path, value);
        return true;
    }

    private SnarkTriggersConfig.ExternalOutputToggle.DiscordSrvForwarding defaultDiscordSrvForwarding(SnarkExternalOutput output) {
        if ("lightweightclans:clan_chat".equalsIgnoreCase(output.id())) {
            return new SnarkTriggersConfig.ExternalOutputToggle.DiscordSrvForwarding(true, "clan");
        }
        return SnarkTriggersConfig.ExternalOutputToggle.DiscordSrvForwarding.disabled();
    }

    public record OutputStatus(
            String id,
            String displayName,
            String sourcePlugin,
            String kind,
            String eventClass,
            String description,
            boolean enabled,
            boolean active
    ) {
    }

    private record RegisteredOutput(
            SnarkExternalOutput output,
            Class<? extends Event> eventClass,
            Listener listener
    ) {
        private RegisteredOutput deactivate() {
            return new RegisteredOutput(output, eventClass, null);
        }
    }
}
