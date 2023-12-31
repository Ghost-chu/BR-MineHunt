package com.ghostchu.plugins.brminehunt.game.gamemodule;

import com.ghostchu.plugins.brminehunt.BR_MineHunt;
import com.ghostchu.plugins.brminehunt.game.Game;
import com.ghostchu.plugins.brminehunt.game.PlayerRole;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

public class GameStartingModule extends AbstractGameModule implements GameModule, Listener, CommandExecutor {

    protected GameStartingModule(BR_MineHunt plugin, Game game) {
        super(plugin, game);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public GameModule init() {
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
            world.setTime(6000);
        }
        game.getAllRoleMembers().stream().map(Bukkit::getPlayer).toList().forEach(game::makeReadyPlayer);
        Location airDropAt = airDrop(Bukkit.getWorlds().get(0).getSpawnLocation());

        Bukkit.getWorlds().get(0).setSpawnLocation(Bukkit.getWorlds().get(0).getSpawnLocation());
        game.getRoleMembers(PlayerRole.HUNTER).stream().map(Bukkit::getPlayer).toList().forEach(p -> {
            p.teleportAsync(Bukkit.getWorlds().get(0).getSpawnLocation()).whenComplete((a, b) -> {
                p.setBedSpawnLocation(Bukkit.getWorlds().get(0).getSpawnLocation(), true);
                p.getInventory().addItem(new ItemStack(Material.COMPASS,1));
            });

        });
        game.getRoleMembers(PlayerRole.RUNNER).stream().map(Bukkit::getPlayer).toList().forEach(p -> {
            p.teleportAsync(airDropAt).whenComplete((a, b) -> p.setBedSpawnLocation(airDropAt, true));
        });
        return new GameStartedModule(plugin, game);
    }

    @Override
    public GameModule tick() {
        throw new IllegalStateException(getClass().getName() + " is un-tickable and should transfer to next module when init()");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return false;
    }

    private Location airDrop(Location spawnpoint) {
        Location loc = spawnpoint.clone();
        loc = new Location(loc.getWorld(), loc.getBlockX(), 0, loc.getBlockZ());
        Random random = new Random();
        loc.add(random.nextInt(200) + 100, 0, random.nextInt(200) + 100);
        loc = loc.getWorld().getHighestBlockAt(loc).getLocation();
        if (!loc.getBlock().isSolid())
            loc.getBlock().setType(Material.GLASS);
        loc.setY(loc.getY() + 1);
        return loc;
    }
}
