package com.ghostchu.plugins.brminehunt.game.gamemodule;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;

public interface GameModule extends Listener, TabCompleter, CommandExecutor {
    GameModule init();
    GameModule tick();

    void setInited(boolean inited);
    boolean isInited();
}
