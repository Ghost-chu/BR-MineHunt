package com.ghostchu.plugins.brminehunt.game.gamemodule;

import com.ghostchu.plugins.brminehunt.BR_MineHunt;
import com.ghostchu.plugins.brminehunt.game.Game;
import de.musterbukkit.replaysystem.main.ReplayAPI;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class GameEndingNoPlayerModule extends AbstractGameModule implements GameModule, Listener, CommandExecutor {
    private static final int endTimer = 30 * 20;
    private int remains = 0;
    private final BossBar bossBar = BossBar.bossBar(Component.text("N/A"), 1.0f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);

    protected GameEndingNoPlayerModule(BR_MineHunt plugin, Game game) {
        super(plugin, game);
    }

    @Override
    public GameModule init() {
        game.getAllRoleMembers().stream().map(Bukkit::getPlayer).filter(Objects::nonNull).forEach(p -> p.setGameMode(GameMode.CREATIVE));
        Bukkit.broadcast(plugin.text("ending-no-player.session-ending"));
        Bukkit.getOnlinePlayers().forEach(p -> {
            p.showTitle(Title.title(plugin.text("ending-no-player.title"), plugin.text("ending-no-player.subtitle")));
            p.showBossBar(bossBar);
        });
        if(Bukkit.getPluginManager().isPluginEnabled("ReplaySystem")){
            Bukkit.broadcast(plugin.text("ending.public-match-info", ReplayAPI.getReplayID()));
        }
        remains = endTimer;
        return null;
    }

    @Override
    public GameModule tick() {
        remains--;
        bossBar.progress((float) remains / endTimer);
        bossBar.name(plugin.text("ending-no-player.bossbar", (int) (remains / 20.0)));
        bossBar.color(BossBar.Color.RED);
        if (remains > 0) {
            return null;
        }
        Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(bossBar));
        return new GameShutdownServerModule(plugin, game);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            event.getPlayer().setGameMode(GameMode.CREATIVE);
            event.getPlayer().showBossBar(bossBar);
        }, 1);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return false;
    }
}
