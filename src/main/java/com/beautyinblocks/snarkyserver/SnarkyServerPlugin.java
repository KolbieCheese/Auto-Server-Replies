package com.beautyinblocks.snarkyserver;

import org.bukkit.plugin.java.JavaPlugin;

public final class SnarkyServerPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("SnarkyServerPlugin enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("SnarkyServerPlugin disabled.");
    }
}
