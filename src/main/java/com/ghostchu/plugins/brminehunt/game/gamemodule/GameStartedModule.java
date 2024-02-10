package com.ghostchu.plugins.brminehunt.game.gamemodule;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.ghostchu.plugins.brminehunt.BR_MineHunt;
import com.ghostchu.plugins.brminehunt.game.Game;
import com.ghostchu.plugins.brminehunt.game.PlayerRole;
import org.bukkit.*;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

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
        if (!game.getReconnectList().isEmpty()) {
            if (game.getRoleMembersOnline(PlayerRole.HUNTER).isEmpty() || game.getRoleMembersOnline(PlayerRole.RUNNER).isEmpty()) {
                return new GamePausedModule(plugin, game, this, new GameEndingNoPlayerModule(plugin, game), v -> !game.getRoleMembersOnline(PlayerRole.HUNTER).isEmpty() && !game.getRoleMembersOnline(PlayerRole.RUNNER).isEmpty());
            }
        }
        if (dragonKilledMark) return new GameRunnerWinModule(plugin, game, lastFocusLoc);
        if (checkNoRunnerAlive()) return new GameHunterWinModule(plugin, game, lastFocusLoc);
        return null;
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
    public void playerConnected(PlayerJoinEvent event) {
        if (game.getPlayerRole(event.getPlayer()) == null) return;
        event.joinMessage(plugin.text("paused.player-reconnected"));
        event.getPlayer().setNoDamageTicks(100);
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
        for (Player runner : game.getRoleMembers(PlayerRole.RUNNER).stream().map(Bukkit::getPlayer).toList()) {
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
}
