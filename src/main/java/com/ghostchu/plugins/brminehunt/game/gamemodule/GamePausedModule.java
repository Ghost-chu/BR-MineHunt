package com.ghostchu.plugins.brminehunt.game.gamemodule;

import com.ghostchu.plugins.brminehunt.BR_MineHunt;
import com.ghostchu.plugins.brminehunt.game.Game;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class GamePausedModule extends AbstractGameModule implements GameModule, Listener, CommandExecutor {

    private final int waitingTime;
    private final GameModule timeoutModule;
    private final GameModule recoverModule;
    private final Predicate<Void> canRecover;
    private int remainingTime = Integer.MAX_VALUE;
    private final BossBar bossBar = BossBar.bossBar(Component.text("N/A"), 1.0f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);


    protected GamePausedModule(BR_MineHunt plugin, Game game, GameModule recoverModule, GameModule timeoutModule, Predicate<Void> canRecover) {
        super(plugin, game);
        this.waitingTime = plugin.getConfig().getInt("pause-max-time") * 20;
        this.recoverModule = recoverModule;
        this.timeoutModule = timeoutModule;
        this.canRecover = canRecover;
    }

    @Override
    public GameModule init() {
        this.remainingTime = waitingTime;
        Bukkit.getOnlinePlayers().forEach(p -> {
            p.showTitle(Title.title(plugin.text("paused.title"), plugin.text("paused.subtitle")));
            p.showBossBar(bossBar);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1,1);
        });
        Bukkit.broadcast(plugin.text("paused.enter-message", waitingTime / 20));
        return null;
    }

    @Override
    public GameModule tick() {
        if (remainingTime <= 0) {
            Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(bossBar));
            Bukkit.broadcast(plugin.text("paused.leave-message-timeout", waitingTime / 20));
            return timeoutModule;
        }
        if (canRecover.test(null)) {
            Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(bossBar));
            Bukkit.broadcast(plugin.text("paused.leave-message-ok", waitingTime / 20));
            return recoverModule;
        }

        bossBar.progress((float) remainingTime / waitingTime);
        bossBar.name(plugin.text("paused.bossbar", remainingTime / 20));
        remainingTime--;
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerLeft(PlayerQuitEvent event){
        if(game.getPlayerRole(event.getPlayer()) != null){
            game.getReconnectList().add(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (game.getPlayerRole(event.getPlayer()) != null) {
            event.joinMessage(plugin.text("paused.player-reconnected", event.getPlayer().getName()));
            event.getPlayer().setNoDamageTicks(100);
            game.getReconnectList().remove(event.getPlayer().getUniqueId());
        } else {
            event.joinMessage(plugin.text("general.joined-as-spectator", event.getPlayer().getName()));
            Bukkit.getScheduler().runTaskLater(plugin, () -> event.getPlayer().setGameMode(GameMode.SPECTATOR), 1);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void playerMove(PlayerMoveEvent event) {
        if (game.getPlayerRole(event.getPlayer()) == null) return;
        boolean shouldCancel = false;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() != to.getBlockX()) shouldCancel = true;
        if (from.getBlockZ() != to.getBlockZ()) shouldCancel = true;
        if (shouldCancel) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerAirChange(EntityAirChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void regainHealth(EntityRegainHealthEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void foodLevelChange(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerInteract(PlayerInteractEvent event) {
        if (game.getPlayerRole(event.getPlayer()) == null) return;
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerInteract(PlayerInteractAtEntityEvent event) {
        if (game.getPlayerRole(event.getPlayer()) == null) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerInteract(PlayerInteractEntityEvent event) {
        if (game.getPlayerRole(event.getPlayer()) == null) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerAttack(PrePlayerAttackEntityEvent event) {
        if (game.getPlayerRole(event.getPlayer()) == null) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerBreakBlock(BlockBreakEvent event) {
        if (game.getPlayerRole(event.getPlayer()) == null) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerPlaceBlock(BlockPlaceEvent event) {
        if (game.getPlayerRole(event.getPlayer()) == null) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerConsumeItem(PlayerItemConsumeEvent event) {
        if (game.getPlayerRole(event.getPlayer()) == null) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerChangeFoodLevel(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerDamageEntity(EntityDamageEvent event) {
        event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void playerDamageEntity(PlayerItemDamageEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerDamageEntity(EntityTargetEvent event) {
        if(event.getTarget() instanceof Player){
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void playerDamageBlock(EntityDamageByBlockEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerAttackEntity(EntityDamageByEntityEvent event) {
        event.setCancelled(true);
    }


}
