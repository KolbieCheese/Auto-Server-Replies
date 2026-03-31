package com.beautyinblocks.snarkyserver;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

public final class SnarkExternalOutputDiscoveryListener implements Listener {
    private final SnarkExternalOutputRegistry externalOutputRegistry;

    public SnarkExternalOutputDiscoveryListener(SnarkExternalOutputRegistry externalOutputRegistry) {
        this.externalOutputRegistry = externalOutputRegistry;
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        externalOutputRegistry.discoverPlugin(event.getPlugin());
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        externalOutputRegistry.handlePluginDisable(event.getPlugin());
    }
}
