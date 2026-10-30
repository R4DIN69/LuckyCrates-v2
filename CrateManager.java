package com.luckycrates.manager;

import com.luckycrates.LuckyCratesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class CrateManager {
    private final LuckyCratesPlugin plugin;
    private final Set<Location> crateLocations = new HashSet<>();
    // Map slot index (0..53) to item
    private final Map<Integer, ItemStack> slotRewards = new HashMap<>();
    private File cratesFile;
    private FileConfiguration cratesConfig;

    public CrateManager(LuckyCratesPlugin plugin) {
        this.plugin = plugin;
        loadCrates();
    }

    public void loadCrates() {
        crateLocations.clear();
        slotRewards.clear();

        cratesFile = new File(plugin.getDataFolder(), "crates.yml");
        if (!cratesFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                cratesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create crates.yml: " + e.getMessage());
            }
        }
        cratesConfig = YamlConfiguration.loadConfiguration(cratesFile);

        // Load locations
        List<String> rawLocs = cratesConfig.getStringList("locations");
        for (String raw : rawLocs) {
            Location loc = parseLocation(raw);
            if (loc != null) {
                crateLocations.add(loc);
            }
        }

        // Load slot rewards
        if (cratesConfig.isConfigurationSection("slot-rewards")) {
            for (String key : cratesConfig.getConfigurationSection("slot-rewards").getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    ItemStack item = cratesConfig.getItemStack("slot-rewards." + key);
                    if (item != null && item.getType() != Material.AIR) {
                        slotRewards.put(slot, item);
                    }
                } catch (NumberFormatException ignored) {}
            }
        } else if (cratesConfig.contains("rewards")) {
            // Migration from legacy list
            List<?> list = cratesConfig.getList("rewards");
            if (list != null) {
                int s = 0;
                for (Object obj : list) {
                    if (obj instanceof ItemStack is && is.getType() != Material.AIR) {
                        slotRewards.put(s++, is);
                    }
                }
            }
        }

        if (slotRewards.isEmpty()) {
            populateDefaultRewards();
            saveCrates();
        }
    }

    private void populateDefaultRewards() {
        // Slot 0: Ultra Rare (0.1% tier)
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta sm = sword.getItemMeta();
        if (sm != null) {
            sm.setDisplayName("§6§l★ Excalibur of Fortune ★");
            sm.addEnchant(Enchantment.SHARPNESS, 5, true);
            sm.addEnchant(Enchantment.UNBREAKING, 3, true);
            sm.addEnchant(Enchantment.LOOTING, 3, true);
            sword.setItemMeta(sm);
        }
        slotRewards.put(0, sword);

        // Slot 1: Rare tier
        slotRewards.put(1, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 3));
        // Slot 2..8: Progressively higher chance
        slotRewards.put(2, new ItemStack(Material.NETHERITE_INGOT, 4));
        slotRewards.put(3, new ItemStack(Material.TOTEM_OF_UNDYING, 2));
        slotRewards.put(4, new ItemStack(Material.DIAMOND_BLOCK, 4));
        slotRewards.put(5, new ItemStack(Material.EMERALD_BLOCK, 8));
        slotRewards.put(6, new ItemStack(Material.EXPERIENCE_BOTTLE, 64));
        slotRewards.put(7, new ItemStack(Material.GOLDEN_APPLE, 16));
        slotRewards.put(8, new ItemStack(Material.ENDER_PEARL, 16));
    }

    public void saveCrates() {
        if (cratesConfig == null || cratesFile == null) return;

        List<String> rawLocs = new ArrayList<>();
        for (Location loc : crateLocations) {
            rawLocs.add(serializeLocation(loc));
        }
        cratesConfig.set("locations", rawLocs);

        cratesConfig.set("slot-rewards", null);
        for (Map.Entry<Integer, ItemStack> entry : slotRewards.entrySet()) {
            cratesConfig.set("slot-rewards." + entry.getKey(), entry.getValue());
        }

        try {
            cratesConfig.save(cratesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save crates.yml: " + e.getMessage());
        }
    }

    /**
     * Calculates base weight for a slot:
     * Slot 0 is 0.1 weight (ultra-rare 0.1% tier).
     * Higher slots have progressively higher weights.
     */
    public double getSlotWeight(int slot) {
        if (slot <= 0) return 0.1;
        // Exponential/polynomial curve: slot 1 = 0.5, slot 2 = 1.2, slot 3 = 2.5, slot 4 = 4.5...
        return 0.1 + Math.pow(slot, 1.6) * 0.4;
    }

    /**
     * Calculates exact percentage probability for a slot based on currently occupied slots.
     */
    public double getSlotPercentage(int slot) {
        if (!slotRewards.containsKey(slot)) return 0.0;
        double totalWeight = 0.0;
        for (int s : slotRewards.keySet()) {
            totalWeight += getSlotWeight(s);
        }
        if (totalWeight <= 0) return 0.0;
        return (getSlotWeight(slot) / totalWeight) * 100.0;
    }

    /**
     * Picks a winning item using exact weighted random probability based on slot rarity.
     */
    public ItemStack pickWeightedReward() {
        if (slotRewards.isEmpty()) {
            return new ItemStack(Material.DIRT);
        }

        double totalWeight = 0.0;
        for (int slot : slotRewards.keySet()) {
            totalWeight += getSlotWeight(slot);
        }

        double rand = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0.0;

        for (Map.Entry<Integer, ItemStack> entry : slotRewards.entrySet()) {
            cumulative += getSlotWeight(entry.getKey());
            if (rand <= cumulative) {
                return entry.getValue().clone();
            }
        }

        return slotRewards.values().iterator().next().clone();
    }

    public List<ItemStack> getAllRewardItems() {
        return new ArrayList<>(slotRewards.values());
    }

    public Map<Integer, ItemStack> getSlotRewards() {
        return Collections.unmodifiableMap(slotRewards);
    }

    public void setSlotRewards(Map<Integer, ItemStack> newRewards) {
        slotRewards.clear();
        for (Map.Entry<Integer, ItemStack> entry : newRewards.entrySet()) {
            if (entry.getValue() != null && entry.getValue().getType() != Material.AIR) {
                slotRewards.put(entry.getKey(), entry.getValue().clone());
            }
        }
        saveCrates();
    }

    public boolean isCrate(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Location blockLoc = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        return crateLocations.contains(blockLoc);
    }

    public void addCrate(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        Location blockLoc = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        crateLocations.add(blockLoc);
        saveCrates();
    }

    public boolean removeCrate(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Location blockLoc = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        boolean removed = crateLocations.remove(blockLoc);
        if (removed) {
            saveCrates();
        }
        return removed;
    }

    public Set<Location> getCrateLocations() {
        return Collections.unmodifiableSet(crateLocations);
    }

    private String serializeLocation(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private Location parseLocation(String str) {
        String[] parts = str.split(",");
        if (parts.length != 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
