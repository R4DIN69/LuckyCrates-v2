package com.luckycrates.listener;

import com.luckycrates.LuckyCratesPlugin;
import com.luckycrates.gui.CrateEditorGUI;
import com.luckycrates.gui.SpinnerGUI;
import com.luckycrates.util.ColorUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;

public class CrateListener implements Listener {
    private final LuckyCratesPlugin plugin;

    public CrateListener(LuckyCratesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;

        Location loc = block.getLocation();
        if (!plugin.getCrateManager().isCrate(loc)) {
            return;
        }

        Player player = event.getPlayer();

        // 1. LEFT CLICK in Creative Mode: Open Rewards Customizer GUI
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (player.getGameMode() == GameMode.CREATIVE) {
                event.setCancelled(true);
                if (player.hasPermission("luckycrates.admin")) {
                    new CrateEditorGUI(plugin).open(player);
                } else {
                    player.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.prefix", "") +
                            plugin.getConfig().getString("messages.no-permission", "&cYou do not have permission.")));
                }
                return;
            }
        }

        // 2. RIGHT CLICK: Open Crate Spinner with Lucky Key
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (event.getHand() != EquipmentSlot.HAND) {
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true);

            if (!player.hasPermission("luckycrates.use")) {
                player.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.prefix", "") +
                        plugin.getConfig().getString("messages.no-permission", "&cYou do not have permission.")));
                return;
            }

            if (plugin.getActiveSpinners().contains(player.getUniqueId())) {
                player.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.prefix", "") +
                        plugin.getConfig().getString("messages.already-spinning", "&cYou are already opening a crate!")));
                return;
            }

            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();

            boolean hasKeyInMain = plugin.getKeyManager().isKey(mainHand);
            boolean hasKeyInOff = !hasKeyInMain && plugin.getKeyManager().isKey(offHand);

            if (!hasKeyInMain && !hasKeyInOff) {
                String keyName = plugin.getConfig().getString("key.name", "&6&l★ Lucky Key ★");
                String needKeyMsg = plugin.getConfig().getString("messages.need-key", "&cYou need a %key% &cto open this crate!")
                        .replace("%key%", keyName);
                player.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.prefix", "") + needKeyMsg));

                try {
                    String errSound = plugin.getConfig().getString("spinner.sounds.error", "BLOCK_CHEST_LOCKED");
                    player.playSound(player.getLocation(), Sound.valueOf(errSound), 0.8f, 0.8f);
                } catch (Exception ignored) {}
                return;
            }

            // Consume one key
            if (hasKeyInMain) {
                mainHand.setAmount(mainHand.getAmount() - 1);
            } else {
                offHand.setAmount(offHand.getAmount() - 1);
            }

            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.9f, 1.0f);
            player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc.clone().add(0.5, 1.0, 0.5), 25, 0.3, 0.3, 0.3, 0.1);

            new SpinnerGUI(plugin, player).start();
        }
    }

    // -------------------------------------------------------------
    // LUCKY KEY PROTECTION: Cannot be placed, dropped, or removed
    // -------------------------------------------------------------

    /** Prevent placing Lucky Keys as blocks */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getKeyManager().isKey(event.getItemInHand())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.prefix", "") +
                    "&cLucky Keys cannot be placed as blocks!"));
        }
    }

    /** Prevent dropping Lucky Keys via Q or dragging outside inventory */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (plugin.getKeyManager().isKey(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.prefix", "") +
                    "&cLucky Keys cannot be dropped from your inventory!"));
        }
    }

    /** Prevent placing Lucky Keys into Item Frames or Armor Stands */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (plugin.getKeyManager().isKey(item)) {
            if (event.getRightClicked() instanceof ItemFrame || event.getRightClicked() instanceof ArmorStand) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.prefix", "") +
                        "&cLucky Keys cannot be placed on entities!"));
            }
        }
    }

    /** Prevent storing Lucky Keys into chests, hoppers, barrels, etc. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        boolean isKeyMoving = plugin.getKeyManager().isKey(current) || plugin.getKeyManager().isKey(cursor);
        if (!isKeyMoving) return;

        // Block dropping with 'Q' inside inventory
        if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
            event.setCancelled(true);
            player.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.prefix", "") +
                    "&cLucky Keys cannot be dropped!"));
            return;
        }

        // If clicking outside window to drop
        if (event.getSlotType() == InventoryType.SlotType.OUTSIDE) {
            event.setCancelled(true);
            return;
        }

        // Block moving into external non-player containers
        InventoryType topType = event.getView().getTopInventory().getType();
        if (topType != InventoryType.CRAFTING && topType != InventoryType.PLAYER) {
            // If clicking inside external container with key
            if (event.getClickedInventory() != null && event.getClickedInventory() == event.getView().getTopInventory()) {
                event.setCancelled(true);
                player.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.prefix", "") +
                        "&cLucky Keys cannot be stored in external containers!"));
                return;
            }
            // If shift-clicking key from player inventory into external container
            if (event.isShiftClick()) {
                event.setCancelled(true);
                player.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.prefix", "") +
                        "&cLucky Keys cannot be stored in external containers!"));
            }
        }
    }

    /** Keep Lucky Keys on death so players never lose their keys */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Iterator<ItemStack> iter = event.getDrops().iterator();
        while (iter.hasNext()) {
            ItemStack drop = iter.next();
            if (plugin.getKeyManager().isKey(drop)) {
                iter.remove();
                event.getItemsToKeep().add(drop);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!plugin.getCrateManager().isCrate(block.getLocation())) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE && player.isSneaking() && player.hasPermission("luckycrates.admin")) {
            plugin.getCrateManager().removeCrate(block.getLocation());
            player.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.prefix", "") +
                    "&cRemoved Lucky Crate!"));
        } else {
            event.setCancelled(true);
            player.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.prefix", "") +
                    "&cThis is a protected Lucky Crate! Use &e/crate remove &cto remove it."));
        }
    }
}
