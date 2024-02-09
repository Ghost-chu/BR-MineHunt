package com.ghostchu.plugins.brminehunt.game.gamemodule;

import com.ghostchu.plugins.brminehunt.BR_MineHunt;
import com.ghostchu.plugins.brminehunt.game.Game;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class GameShutdownServerModule extends AbstractGameModule implements GameModule, Listener, CommandExecutor {
    protected GameShutdownServerModule(BR_MineHunt plugin, Game game) {
        super(plugin, game);
    }

    @Override
    public GameModule init() {
        Bukkit.spigot().restart();
        return null;
    }

    @Override
    public GameModule tick() {
        throw new UnsupportedOperationException("GameShutdownServerModule.tick() should not be called");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return false;
    }
}
