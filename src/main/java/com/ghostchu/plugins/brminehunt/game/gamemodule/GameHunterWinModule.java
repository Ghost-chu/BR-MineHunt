package com.ghostchu.plugins.brminehunt.game.gamemodule;

import com.ghostchu.plugins.brminehunt.BR_MineHunt;
import com.ghostchu.plugins.brminehunt.game.Game;
import com.ghostchu.plugins.brminehunt.game.PlayerRole;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class GameHunterWinModule extends AbstractGameModule implements GameModule, Listener, CommandExecutor {
    private long totalTicked = 0;
    private static final long nextModuleTick = 20 * 10;
    private final Location focusLocation;

    protected GameHunterWinModule(BR_MineHunt plugin, Game game, Location focusLocation) {
        super(plugin, game);
        this.focusLocation = focusLocation;
    }

    @Override
    public GameModule init() {
        List<Player> teleportPlayers = game.getAllRoleMembers().stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
        teleportPlayers.forEach(p -> {
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(focusLocation);
            p.playSound(p.getLocation(), Sound.MUSIC_DISC_PIGSTEP, 1.0f, 1.0f);
        });
        game.getRoleMembers(PlayerRole.HUNTER).stream().map(Bukkit::getPlayer).toList().forEach(p -> p.showTitle(Title.title(plugin.text("match-complete.title-win"), plugin.text("match-complete.subtitle-win"))));
        game.getRoleMembers(PlayerRole.RUNNER).stream().map(Bukkit::getPlayer).toList().forEach(p -> p.showTitle(Title.title(plugin.text("match-complete.title-lose"), plugin.text("match-complete.subtitle-lose"))));
        Bukkit.broadcast(plugin.text("match-complete.hunter-win"));
        return null;
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
    public GameModule tick() {
        totalTicked++;
        if (totalTicked >= nextModuleTick) return new GameEndingModule(plugin, game);
        return null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return false;
    }
}
