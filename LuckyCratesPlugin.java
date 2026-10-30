package com.luckycrates;

import com.luckycrates.command.GiveKeyCommand;
import com.luckycrates.command.LuckyCratesCommand;
import com.luckycrates.command.PlaceCommand;
import com.luckycrates.listener.CrateListener;
import com.luckycrates.listener.GUIListener;
import com.luckycrates.manager.CrateManager;
import com.luckycrates.manager.KeyManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class LuckyCratesPlugin extends JavaPlugin {
    private static LuckyCratesPlugin instance;

    private KeyManager keyManager;
    private CrateManager crateManager;
    private GiveKeyCommand giveKeyCommand;

    // Track active players currently in the roulette spinner
    private final Set<UUID> activeSpinners = new HashSet<>();

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Initialize managers
        this.keyManager = new KeyManager(this);
        this.crateManager = new CrateManager(this);

        // Register commands & listeners
        this.giveKeyCommand = new GiveKeyCommand(this);
        PlaceCommand placeCommand = new PlaceCommand(this);
        LuckyCratesCommand luckyCratesCommand = new LuckyCratesCommand(this);

        // Register event listeners
        Bukkit.getPluginManager().registerEvents(new CrateListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GUIListener(this), this);
        Bukkit.getPluginManager().registerEvents(giveKeyCommand, this);

        // Register command executors
        if (getCommand("place") != null) {
            getCommand("place").setExecutor(placeCommand);
            getCommand("place").setTabCompleter(placeCommand);
        }
        if (getCommand("luckykey") != null) {
            getCommand("luckykey").setExecutor(giveKeyCommand);
            getCommand("luckykey").setTabCompleter(giveKeyCommand);
        }
        if (getCommand("crate") != null) {
            getCommand("crate").setExecutor(luckyCratesCommand);
            getCommand("crate").setTabCompleter(luckyCratesCommand);
        }

        getLogger().info("LuckyCrates has been enabled successfully! (Paper 1.21+)");
    }

    @Override
    public void onDisable() {
        if (crateManager != null) {
            crateManager.saveCrates();
        }
        activeSpinners.clear();
        getLogger().info("LuckyCrates disabled.");
    }

    public static LuckyCratesPlugin getInstance() {
        return instance;
    }

    public KeyManager getKeyManager() {
        return keyManager;
    }

    public CrateManager getCrateManager() {
        return crateManager;
    }

    public GiveKeyCommand getGiveKeyCommand() {
        return giveKeyCommand;
    }

    public Set<UUID> getActiveSpinners() {
        return activeSpinners;
    }
}
