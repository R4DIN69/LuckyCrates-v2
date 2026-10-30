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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LuckyCratesCommand implements CommandExecutor, TabCompleter {
    private final LuckyCratesPlugin plugin;

    public LuckyCratesCommand(LuckyCratesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "place" -> {
                if (sender instanceof Player p) {
                    p.performCommand("place crate");
                } else {
                    sender.sendMessage("§cOnly players can run this command.");
                }
            }
            case "remove", "delete" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cOnly players can run this command.");
                    return true;
                }
                if (!player.hasPermission("luckycrates.admin")) {
                    player.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.no-permission", "&cNo permission.")));
                    return true;
                }

                Block target = player.getTargetBlockExact(5);
                if (target == null || !plugin.getCrateManager().isCrate(target.getLocation())) {
                    player.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.prefix", "") +
                            "&cYou must look at a registered Lucky Crate block within 5 blocks to remove it!"));
                    return true;
                }

                Location loc = target.getLocation();
                plugin.getCrateManager().removeCrate(loc);
                player.getWorld().spawnParticle(Particle.POOF, loc.clone().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.05);
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 0.8f, 1.2f);

                player.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.prefix", "") +
                        "&aSuccessfully removed Lucky Crate at &e" + loc.getWorld().getName() + " (" +
                        loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")&a!"));
            }
            case "key", "givekey" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /crate key <player> [amount]");
                    return true;
                }
                int amount = 1;
                if (args.length >= 3) {
                    try {
                        amount = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cInvalid amount.");
                        return true;
                    }
                }
                plugin.getGiveKeyCommand().giveKey(sender, args[1], amount);
            }
            case "reload" -> {
                if (!sender.hasPermission("luckycrates.admin")) {
                    sender.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.no-permission", "&cNo permission.")));
                    return true;
                }
                plugin.reloadConfig();
                plugin.getCrateManager().loadCrates();
                sender.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.prefix", "") + "&aConfiguration and crates reloaded!"));
            }
            case "list" -> {
                if (!sender.hasPermission("luckycrates.admin")) {
                    sender.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.no-permission", "&cNo permission.")));
                    return true;
                }
                sender.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.prefix", "") + "&6Registered Lucky Crates:"));
                for (Location loc : plugin.getCrateManager().getCrateLocations()) {
                    sender.sendMessage(" §8- §e" + loc.getWorld().getName() + " §7(X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ() + ")");
                }
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ColorUtil.color("&8&m----------------&r &6&lLuckyCrates Commands &8&m----------------"));
        sender.sendMessage(ColorUtil.color(" &6/place crate &8- &7Register targeted block as a Lucky Crate"));
        sender.sendMessage(ColorUtil.color(" &6/crate remove &8- &7Remove targeted Lucky Crate block"));
        sender.sendMessage(ColorUtil.color(" &6/give <player> luckykey <amount> &8- &7Give Lucky Keys to player"));
        sender.sendMessage(ColorUtil.color(" &6/crate key <player> [amount] &8- &7Alternative key command"));
        sender.sendMessage(ColorUtil.color(" &6/crate reload &8- &7Reload config.yml and crates.yml"));
        sender.sendMessage(ColorUtil.color(" &6/crate list &8- &7View list of placed crates"));
        sender.sendMessage(ColorUtil.color(" &7&oTip: Left-click crate in Creative mode to edit rewards & slot rarities!"));
        sender.sendMessage(ColorUtil.color("&8&m----------------------------------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("place", "remove", "key", "reload", "list", "help");
        }
        return Collections.emptyList();
    }
}
