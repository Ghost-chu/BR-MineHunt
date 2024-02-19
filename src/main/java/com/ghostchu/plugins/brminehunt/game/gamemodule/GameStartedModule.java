package com.ghostchu.plugins.brminehunt.game.gamemodule;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.ghostchu.plugins.brminehunt.BR_MineHunt;
import com.ghostchu.plugins.brminehunt.game.Game;
import com.ghostchu.plugins.brminehunt.game.PlayerRole;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GameStartedModule extends AbstractGameModule implements GameModule, Listener, CommandExecutor {
    private long totalTicked = 0;
    private boolean dragonKilledMark = false;
    private Location lastFocusLoc = new Location(Bukkit.getWorlds().get(0), 0, 100, 0);

    protected GameStartedModule(BR_MineHunt plugin, Game game) {
        super(plugin, game);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public GameModule init() {
        game.getAllRoleMembers().stream().map(Bukkit::getPlayer).filter(Objects::nonNull).forEach(p -> {
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 3, 255, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20 * 3, 255, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 20 * 3, 255, false, false));
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
            p.setGameMode(GameMode.SURVIVAL);
        });
        return null;
    }

    @Override
    public GameModule tick() {
        totalTicked++;
        if (totalTicked % 5 == 0) {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (game.getPlayerRole(onlinePlayer.getUniqueId()) == null) {
                    onlinePlayer.sendActionBar(plugin.text("started.you-are-spectator"));
                }
                if (game.getPlayerRole(onlinePlayer.getUniqueId()) == PlayerRole.RUNNER && onlinePlayer.getGameMode() == GameMode.SPECTATOR) {
                    onlinePlayer.sendActionBar(plugin.text("started.you-are-supporter"));
                }
            }
        }
        if (!game.getReconnectList().isEmpty()) {
            if (game.getRoleMembersOnline(PlayerRole.HUNTER).isEmpty() || game.getRoleMembersOnline(PlayerRole.RUNNER).isEmpty()) {
                return new GamePausedModule(plugin, game, this, new GameEndingNoPlayerModule(plugin, game), v -> !game.getRoleMembersOnline(PlayerRole.HUNTER).isEmpty() && !game.getRoleMembersOnline(PlayerRole.RUNNER).isEmpty());
            }
        }
        if (dragonKilledMark) return new GameRunnerWinModule(plugin, game, lastFocusLoc);
        if (checkNoRunnerAlive()) return new GameHunterWinModule(plugin, game, lastFocusLoc);
        if (totalTicked % 5 == 0) {
            giveNightVision();
        }
        return null;
    }

    private void giveNightVision() {
        Bukkit.getOnlinePlayers().forEach(p -> {
            if (p.getGameMode() == GameMode.SPECTATOR) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20*8, 1, false, false));
            }
        });
    }


    private boolean checkNoRunnerAlive() {
        boolean anyAlive = false;
        for (UUID roleMemberUUID : game.getRoleMembers(PlayerRole.RUNNER)) {
            // if (game.getReconnectList().contains(roleMemberUUID)) return false;
            Player roleMember = Bukkit.getPlayer(roleMemberUUID);
            if (roleMember == null) continue;
            if (roleMember.getGameMode() == GameMode.SURVIVAL) anyAlive = true;
        }
        return !anyAlive;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (PlayerRole.HUNTER == game.getPlayerRole(event.getPlayer())) {
            event.getPlayer().getInventory().addItem(new ItemStack(Material.COMPASS, 1));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (game.getPlayerRole(event.getPlayer()) != null) {
            event.joinMessage(plugin.text("paused.player-reconnected", event.getPlayer().getName()));
            event.getPlayer().setNoDamageTicks(100);
            game.getReconnectList().remove(event.getPlayer().getUniqueId());
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> event.getPlayer().setGameMode(GameMode.SPECTATOR), 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void useCompass(PlayerInteractEvent event) {
        if (event.getItem() == null) return;
        if (event.getItem().getType() != Material.COMPASS) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (game.getPlayerRole(player) != PlayerRole.HUNTER) return;
        Player closestRunner = findClosestRunner(player);
        if (closestRunner == null) {
            player.sendMessage(plugin.text("started.compass-different-dimension"));
            return;
        }
        CompassMeta compassMeta = (CompassMeta) event.getItem().getItemMeta();
        Location loc = closestRunner.getLocation().clone();
        loc.setY(255);
        compassMeta.setLodestone(loc);
        compassMeta.setLodestoneTracked(false);
        event.getItem().setItemMeta(compassMeta);
        player.sendActionBar(plugin.text("started.compass-tracking", closestRunner.getName()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        PlayerRole role = game.getPlayerRole(event.getPlayer());
        if (role == null) return;
        if (role == PlayerRole.RUNNER) {
            lastFocusLoc = event.getPlayer().getLocation();
            event.getPlayer().setGameMode(GameMode.SPECTATOR);
            event.getPlayer().sendMessage(plugin.text("started.runner-death", event.getPlayer().getName()));
            event.getPlayer().setBedSpawnLocation(event.getPlayer().getLocation(), true);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> event.getPlayer().spigot().respawn(), 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(PlayerPostRespawnEvent event) {
        PlayerRole role = game.getPlayerRole(event.getPlayer());
        if (role == null) return;
        if (role == PlayerRole.RUNNER) {
            lastFocusLoc = event.getPlayer().getLocation();
            event.getPlayer().teleport(lastFocusLoc);
            event.getPlayer().setGameMode(GameMode.SPECTATOR);
            //event.getPlayer().sendMessage(plugin.text("started.runner-death", event.getPlayer().getName()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDragonDeath(EntityDeathEvent event) {
        if (event.getEntity().getType() != EntityType.ENDER_DRAGON) return;
        lastFocusLoc = new Location(event.getEntity().getWorld(), 0, 80, 0);
        dragonKilledMark = true;
    }

    @Nullable
    private Player findClosestRunner(Player hunter) {
        Player closestRunner = null;
        for (Player runner : game.getRoleMembersOnline(PlayerRole.RUNNER)) {
            if (runner.getWorld() != hunter.getWorld()) {
                continue;
            }
            if (runner.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (closestRunner == null) {
                closestRunner = runner;
                continue;
            }
            if (hunter.getLocation().distance(runner.getLocation()) < hunter.getLocation().distance(closestRunner.getLocation())) {
                closestRunner = runner;
            }
        }
        return closestRunner;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> list = new ArrayList<>(List.of("spectate", "howtoplay", "invsee", "codusk", "help"));
        if (sender.hasPermission("minehunt.admin.forcejoin"))
            list.add("forcejoin");
        return list;
    }


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return false;
        if (args.length == 0) {
            player.sendMessage(plugin.text("general.invalid-command"));
            return true;
        }
        switch (args[0]) {
            case "spectate" -> {
                player.setGameMode(GameMode.SPECTATOR);
                game.setPlayerRole(player, null);
                Bukkit.broadcast(plugin.text("started.switch-to-spectator", player.getName()));
            }
            case "invsee" -> {
                Player target = Bukkit.getPlayer(args[1]);
                if (game.getPlayerRole(target) != null || player.getGameMode() != GameMode.SPECTATOR) {
                    target.sendMessage(plugin.text("started.no-invsee-for-team"));
                    return true;
                }
                ItemStack[] stacks = target.getInventory().getContents();
                Inventory inv = Bukkit.createInventory(null, InventoryType.PLAYER);
                inv.setContents(stacks);
                if (inv instanceof PlayerInventory playerInventory) {
                    playerInventory.setArmorContents(target.getInventory().getArmorContents());
                    playerInventory.setExtraContents(target.getInventory().getExtraContents());
                }
                player.openInventory(inv);
            }
            case "forcejoin" -> {
                if (!player.hasPermission("minehunt.admin.forcejoin")) {
                    return true;
                }
                String username = args[1];
                String teamName = args[2];
                Player target = Bukkit.getPlayer(username);
                PlayerRole targetRole = PlayerRole.valueOf(teamName.toUpperCase(Locale.ROOT));
                game.setPlayerRole(target.getUniqueId(), null);
                Player teammate = Bukkit.getPlayer(game.getRoleMembers(targetRole).iterator().next());
                target.teleport(teammate);
                game.setPlayerRole(target.getUniqueId(), targetRole);
                target.setGameMode(GameMode.SURVIVAL);
                Bukkit.broadcast(plugin.text("started.force-joined", sender.getName(), target.getName(), targetRole.getComponent(), teammate.getName()));
            }
            case "howtoplay" -> player.sendMessage(plugin.text("general.how-to-play"));
            case "codusk" -> player.sendMessage(ChatColor.GOLD + "鱼头，小片三呢？");
            case "help" ->
                    player.sendMessage(plugin.text("general.available-commands", "/minehunt howtoplay, /minehunt spectate", "/minehunt codusk", "/minehunt forcejoin", "/minehunt invsee"));
            default -> player.sendMessage(plugin.text("general.invalid-command"));
        }
        return true;
    }
}
