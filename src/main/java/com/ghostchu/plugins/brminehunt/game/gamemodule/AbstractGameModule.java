package com.ghostchu.plugins.brminehunt.game.gamemodule;

import com.ghostchu.plugins.brminehunt.BR_MineHunt;
import com.ghostchu.plugins.brminehunt.game.Game;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class AbstractGameModule implements GameModule {
    public final Game game;
    public final BR_MineHunt plugin;
    private boolean isInited;

    protected AbstractGameModule(BR_MineHunt plugin, Game game) {
        this.plugin = plugin;
        this.game = game;
    }

    public void setInited(boolean inited) {
        isInited = inited;
    }

    public boolean isInited() {
        return isInited;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return null;
    }
}
