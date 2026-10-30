package com.luckycrates.command;

import com.luckycrates.LuckyCratesPlugin;
import com.luckycrates.util.ColorUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class PlaceCommand implements CommandExecutor, TabCompleter {
    private final LuckyCratesPlugin plugin;

    public PlaceCommand(LuckyCratesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can place crates.");
            return true;
        }

        if (!player.hasPermission("luckycrates.admin")) {
            player.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.prefix", "") +
                    plugin.getConfig().getString("messages.no-permission", "&cYou do not have permission.")));
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("crate")) {
            player.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.prefix", "") +
                    "&eUsage: &f/place crate &7(Look at a block to register it as a Lucky Crate)"));
            return true;
        }

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || targetBlock.getType().isAir()) {
            player.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.prefix", "") +
                    plugin.getConfig().getString("messages.no-target-block", "&cYou must look at a block within 5 blocks!")));
            return true;
        }

        Location loc = targetBlock.getLocation();
        if (plugin.getCrateManager().isCrate(loc)) {
            player.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.prefix", "") +
                    "&cThis block is already registered as a Lucky Crate!"));
            return true;
        }

        plugin.getCrateManager().addCrate(loc);

        // Visual and sound feedback
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0.5, 1.2, 0.5), 20, 0.3, 0.3, 0.3, 0.05);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);

        String msg = plugin.getConfig().getString("messages.crate-placed", "&aSuccessfully created a Lucky Crate at &e%world%, %x%, %y%, %z%&a!")
                .replace("%world%", loc.getWorld().getName())
                .replace("%x%", String.valueOf(loc.getBlockX()))
                .replace("%y%", String.valueOf(loc.getBlockY()))
                .replace("%z%", String.valueOf(loc.getBlockZ()));
        player.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.prefix", "") + msg));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("crate");
        }
        return Collections.emptyList();
    }
}
