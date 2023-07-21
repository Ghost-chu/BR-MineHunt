package com.ghostchu.plugins.brminehunt.game.gamemodule;

import com.ghostchu.plugins.brminehunt.BR_MineHunt;
import com.ghostchu.plugins.brminehunt.game.Game;
import com.ghostchu.plugins.brminehunt.game.PlayerRole;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.CommandExecutor;
import org.bukkit.event.Listener;

import java.util.Objects;

public class GameRunnerWinModule extends AbstractGameModule implements GameModule, Listener, CommandExecutor {
    private final Location focusLocation;
    private long totalTicked = 0;
    private static final long nextModuleTick = 20 * 10;

    protected GameRunnerWinModule(BR_MineHunt plugin, Game game, Location focusLocation) {
        super(plugin, game);
        this.focusLocation = focusLocation;
    }

    @Override
    public GameModule init() {
        Bukkit.getOnlinePlayers().forEach(p -> {
            p.spigot().respawn();
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(focusLocation);
            p.playSound(p.getLocation(), Sound.MUSIC_DISC_PIGSTEP, 1.0f, 1.0f);
        });
        game.getRoleMembers(PlayerRole.RUNNER).stream().map(Bukkit::getPlayer).filter(Objects::nonNull).forEach(p -> p.showTitle(Title.title(plugin.text("match-complete.title-win"), plugin.text("match-complete.subtitle-win"))));
        game.getRoleMembers(PlayerRole.HUNTER).stream().map(Bukkit::getPlayer).filter(Objects::nonNull).forEach(p -> p.showTitle(Title.title(plugin.text("match-complete.title-lose"), plugin.text("match-complete.subtitle-lose"))));
        Bukkit.broadcast(plugin.text("match-complete.runner-win"));
        return null;
    }

    @Override
    public GameModule tick() {
        totalTicked++;
        if (totalTicked >= nextModuleTick) return new GameEndingModule(plugin, game);
        return null;
    }
}
