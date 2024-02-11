package com.ghostchu.plugins.brminehunt.game;

import com.ghostchu.plugins.brminehunt.BR_MineHunt;
import com.ghostchu.plugins.brminehunt.game.gamemodule.GameModule;
import com.google.common.collect.ImmutableList;
import lombok.Getter;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Logger;

public class Game implements Listener {
    @Getter
    private final BR_MineHunt plugin;
    private final Team hunterTeam;
    private final Team runnerTeam;

    @Getter
    private GameModule activeModule;
    private final Map<UUID, PlayerRole> roleMapping = new ConcurrentHashMap<>(); //线程安全
    @Getter
    private final Set<UUID> reconnectList = new CopyOnWriteArraySet<>();
    @Getter
    private final String matchId = UUID.randomUUID().toString().replace("-","");

    public Game(BR_MineHunt plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickGameModule, 0, 1);
        ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        Scoreboard mainScoreboard = scoreboardManager.getMainScoreboard();
        hunterTeam = mainScoreboard.registerNewTeam("hunter");
        runnerTeam = mainScoreboard.registerNewTeam("runner");
        hunterTeam.color(PlayerRole.HUNTER.getColor());
        runnerTeam.color(PlayerRole.RUNNER.getColor());
        hunterTeam.setAllowFriendlyFire(false);
        runnerTeam.setAllowFriendlyFire(false);
        hunterTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.FOR_OTHER_TEAMS);
        runnerTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.FOR_OTHER_TEAMS);
        hunterTeam.setCanSeeFriendlyInvisibles(true);
        runnerTeam.setCanSeeFriendlyInvisibles(true);
        hunterTeam.prefix(PlayerRole.HUNTER.getChatPrefixComponent());
        runnerTeam.prefix(PlayerRole.RUNNER.getChatPrefixComponent());
        getLogger().info("MatchID " + matchId);
    }

    private void tickGameModule() {
        GameModule switchedModule = null;
        if (activeModule != null) {
            if (activeModule.isInited()) {
                switchedModule = activeModule.tick();
            } else {
                switchedModule = activeModule.init();
                activeModule.setInited(true);
            }
        }
        if (switchedModule != null) {
            setActiveModule(switchedModule);
        }
    }

    public void setActiveModule(GameModule switchGameModule) {
        HandlerList.unregisterAll(this.activeModule);
        Bukkit.getPluginCommand("minehunt").setExecutor(null);
        Bukkit.getPluginCommand("minehunt").setTabCompleter(null);
        this.activeModule = switchGameModule;
        Bukkit.getPluginManager().registerEvents(activeModule, plugin);
        Bukkit.getPluginCommand("minehunt").setExecutor(activeModule);
        Bukkit.getPluginCommand("minehunt").setTabCompleter(activeModule);
    }


    public void setPlayerRole(@NotNull Player player, @Nullable PlayerRole playerRole) {
        setPlayerRole(player.getUniqueId(), playerRole);
    }

    public void setPlayerRole(@NotNull UUID player, @Nullable PlayerRole playerRole) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player);
        if (playerRole == null) {
            this.roleMapping.remove(player);
            this.hunterTeam.removePlayer(offlinePlayer);
            this.runnerTeam.removePlayer(offlinePlayer);
        } else {
            this.roleMapping.put(player, playerRole);
            switch (playerRole) {
                case HUNTER -> this.hunterTeam.addPlayer(offlinePlayer);
                case RUNNER -> this.runnerTeam.addPlayer(offlinePlayer);
            }
        }
    }

    public void makeReadyPlayer(Player p) {
        p.setGameMode(GameMode.SURVIVAL);
        p.setInvulnerable(false);
        p.setExp(0);
        p.setLevel(0);
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setRemainingAir(p.getMaximumAir());
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setWardenWarningLevel(0);
        p.setWardenWarningCooldown(0);
        p.setFlying(false);
        p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));
        p.getInventory().clear();
        p.getEnderChest().clear();
        p.setCompassTarget(p.getWorld().getSpawnLocation());
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.MASTER, 1.0f, 1.0f);
        PlayerRole role = this.getPlayerRole(p.getUniqueId());
        if (role == null)
            throw new IllegalStateException("Player " + p.getName() + " has no role in game but in roleMembers");
        p.displayName(role.getChatPrefixComponent().append(p.displayName().color(TextColor.color(NamedTextColor.WHITE))));
    }

    public Collection<UUID> getAllRoleMembers() {
        return ImmutableList.copyOf(roleMapping.keySet().stream().toList());
    }

    @NotNull
    public Collection<UUID> getRoleMembers(@NotNull PlayerRole role) {
        List<UUID> uuid = new ArrayList<>(roleMapping.size());
        roleMapping.forEach((k, v) -> {
            if (v == role) {
                uuid.add(k);
            }
        });
        return uuid;
    }

    @NotNull
    public Collection<Player> getRoleMembersOnline(@NotNull PlayerRole role) {
        Collection<Player> uuid = new ArrayList<>(roleMapping.size());
        roleMapping.forEach((k, v) -> {
            if(v == role) {
                Player p = Bukkit.getPlayer(k);
                if (p != null) {
                    uuid.add(p);
                }
            }
        });
        return uuid;
    }

    @Nullable
    public PlayerRole getPlayerRole(@NotNull UUID uuid) {
        return roleMapping.get(uuid);
    }

    @Nullable
    public PlayerRole getPlayerRole(@NotNull Player player) {
        return roleMapping.get(player.getUniqueId());
    }

    private Logger getLogger() {
        return plugin.getLogger();
    }


}
