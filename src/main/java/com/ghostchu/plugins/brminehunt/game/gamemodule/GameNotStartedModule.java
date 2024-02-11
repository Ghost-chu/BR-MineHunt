package com.ghostchu.plugins.brminehunt.game.gamemodule;

import com.ghostchu.plugins.brminehunt.BR_MineHunt;
import com.ghostchu.plugins.brminehunt.game.Game;
import com.ghostchu.plugins.brminehunt.game.PlayerRole;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.popcraft.chunky.api.ChunkyAPI;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class GameNotStartedModule extends AbstractGameModule implements GameModule, Listener, CommandExecutor {
    private final AtomicBoolean worldPregenerated = new AtomicBoolean(false);
    private final Random random = new Random();
    private long totalTicked = 0L;
    private final int waitingTime;
    private int remainingTime = Integer.MAX_VALUE;
    private final BossBar bossBar = BossBar.bossBar(Component.text("N/A"), 1.0f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
    private final BossBar mapGenerate = BossBar.bossBar(Component.text("N/A"), 1.0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);

    public GameNotStartedModule(BR_MineHunt plugin, Game game) {
        super(plugin, game);
        this.waitingTime = plugin.getConfig().getInt("waiting-time") * 20;
    }

    @Override
    public GameModule init() {
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        }
        pregenerateWorlds();
        return null;
    }

    private void pregenerateWorlds() {
        World defWorld = Bukkit.getWorlds().get(0);
        Location spawnPoint = defWorld.getSpawnLocation();
        ChunkyAPI chunky = Bukkit.getServer().getServicesManager().load(ChunkyAPI.class);
        if (chunky == null) return;
        chunky.startTask(defWorld.getName(), "square", spawnPoint.getBlockX(), spawnPoint.getBlockZ(), 500, 500, "concentric");
        chunky.onGenerationProgress(e -> {
            if (!e.complete()) {
                mapGenerate.progress(e.progress() / 100.0f);
                mapGenerate.color(BossBar.Color.YELLOW);
                mapGenerate.name(plugin.text("not-started.bossbar-map-generate", String.format("%.2f", e.progress()) + "%", e.chunks()));
            } else {
                mapGenerate.progress(1.0f);
                mapGenerate.color(BossBar.Color.GREEN);
                mapGenerate.name(plugin.text("not-started.bossbar-map-generated"));
                worldPregenerated.set(true);
            }
        });
        chunky.onGenerationComplete(e -> {
            mapGenerate.progress(1.0f);
            mapGenerate.color(BossBar.Color.GREEN);
            mapGenerate.name(plugin.text("not-started.bossbar-map-generated"));
            worldPregenerated.set(true);
        });
    }

    @Override
    public GameModule tick() {
        totalTicked++;
        mentionRoles();
        broadcastAnnouncement();
        cleanupList();
        if (totalTicked % 20 == 0)
            tryAssignRolesForNonSpectatingPlayers();
        boolean start = tickTimer();
        if (!start) return null;
        Bukkit.getOnlinePlayers().forEach(p -> {
            p.hideBossBar(bossBar);
            p.hideBossBar(mapGenerate);
        });
        return new GameStartingModule(plugin, game);
    }

    private void cleanupList() {
        for (UUID roleMember : game.getRoleMembers(PlayerRole.RUNNER)) {
            Player p = Bukkit.getPlayer(roleMember);
            if (p == null || !p.isOnline()) game.setPlayerRole(roleMember, null);
        }
        for (UUID roleMember : game.getRoleMembers(PlayerRole.HUNTER)) {
            Player p = Bukkit.getPlayer(roleMember);
            if (p == null || !p.isOnline()) game.setPlayerRole(roleMember, null);
        }
    }

    private boolean tickTimer() {
        int runnerSize = game.getRoleMembers(PlayerRole.RUNNER).size();
        int hunterSize = game.getRoleMembers(PlayerRole.HUNTER).size();
        int minPlayers = plugin.getConfig().getInt("min-players");
        boolean runnerSlotAvailable = plugin.getConfig().getInt("runner-max") > runnerSize;
        boolean hunterSlotAvailable = plugin.getConfig().getInt("max-players") - plugin.getConfig().getInt("runner-max") > hunterSize;
        if ((runnerSize + hunterSize) < plugin.getConfig().getInt("min-players") || runnerSize == 0 || hunterSize == 0) {
            remainingTime = waitingTime;
            bossBar.progress(1.0f);
            bossBar.name(plugin.text("not-started.waiting-for-more-players", minPlayers - (runnerSize + hunterSize)));
            bossBar.color(BossBar.Color.RED);
            return false;
        }

        if (!worldPregenerated.get()) {
            bossBar.progress(1.0f);
            bossBar.name(plugin.text("not-started.waiting-world-generate"));
            bossBar.color(BossBar.Color.PINK);
            return false;
        }

        remainingTime--;
        bossBar.progress((float) remainingTime / waitingTime);
        bossBar.name(plugin.text("not-started.starting-countdown", (int) (remainingTime / 20.0)));
        bossBar.color(BossBar.Color.GREEN);

        if (!runnerSlotAvailable && !hunterSlotAvailable && remainingTime > 5 * 20) {
            Bukkit.broadcast(plugin.text("not-started.all-get-ready", 5));
            remainingTime = 5 * 20;
        }

        return remainingTime <= 0;
    }

    private void broadcastAnnouncement() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        if (totalTicked % (20 * 12) == 0) {
            Bukkit.broadcast(plugin.text("not-started.announcement"));
        }
    }

    private void mentionRoles() {
        Bukkit.getOnlinePlayers().forEach(p -> {
            PlayerRole role = game.getPlayerRole(p);
            if (role != null) {
                p.sendActionBar(plugin.text("not-started.hotbar", role.getDisplay()));
            } else {
                if (p.getGameMode() == GameMode.SPECTATOR) {
                    p.sendActionBar(plugin.text("not-started.spectating"));
                } else {
                    p.sendActionBar(plugin.text("not-started.finding-slot"));
                }
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void playerLeft(PlayerQuitEvent event) {
        game.setPlayerRole(event.getPlayer(), null);
        // re-assign non-role players
        tryAssignRolesForNonSpectatingPlayers();
        tryFixupRoles();
    }

    private void tryFixupRoles() {
        int runnerSize = game.getRoleMembers(PlayerRole.RUNNER).size();
        int hunterSize = game.getRoleMembers(PlayerRole.HUNTER).size();
        if (runnerSize == 0) {
            // move a hunter to runner
            Collection<Player> hunters = game.getRoleMembersOnline(PlayerRole.HUNTER);
            if (hunters.size() > 1) {
                Player hunter = hunters.iterator().next();
                attemptAssignRole(hunter);
            }
        }
        if (hunterSize == 0) {
            // move a runner to hunter
            Collection<Player> runners = game.getRoleMembersOnline(PlayerRole.RUNNER);
            if (runners.size() > 1) {
                Player runner = runners.iterator().next();
                attemptAssignRole(runner);
            }
        }
    }

    private void tryAssignRolesForNonSpectatingPlayers() {
        List<? extends Player> noRolePlayers = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
                .filter(p -> game.getPlayerRole(p) == null)
                .collect(Collectors.toList());
        if (noRolePlayers.isEmpty()) return;
        Collections.shuffle(noRolePlayers);
        noRolePlayers.forEach(this::attemptAssignRole);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerJoin(PlayerJoinEvent event) {
        attemptAssignRole(event.getPlayer());
        event.getPlayer().showBossBar(bossBar);
        event.getPlayer().showBossBar(mapGenerate);
    }

    private void attemptAssignRole(Player player) {
        game.setPlayerRole(player, null);
        int runnerSize = game.getRoleMembers(PlayerRole.RUNNER).size();
        int hunterSize = game.getRoleMembers(PlayerRole.HUNTER).size();
        boolean runnerSlotAvailable = plugin.getConfig().getInt("runner-max") > runnerSize;
        boolean hunterSlotAvailable = plugin.getConfig().getInt("max-players") - plugin.getConfig().getInt("runner-max") > hunterSize;
        PlayerRole playerRole = null;
        if (runnerSize == 0) { // make sure we have at least 1 runner in game
            playerRole = PlayerRole.RUNNER;
        } else if (hunterSize == 0) {
            playerRole = PlayerRole.HUNTER;
        } else {
            if (runnerSlotAvailable && hunterSlotAvailable) {
                if (random.nextBoolean()) playerRole = PlayerRole.RUNNER;
                else playerRole = PlayerRole.HUNTER;
            } else if (hunterSlotAvailable) {
                playerRole = PlayerRole.HUNTER;
            } else if (runnerSlotAvailable) {
                playerRole = PlayerRole.RUNNER;
            }
        }
        if (playerRole != null) {
            game.setPlayerRole(player, playerRole);
            game.makeReadyPlayer(player);
            plugin.getLogger().info("分配玩家 " + player.getName() + " 角色为：" + playerRole.getDisplay());
            player.showTitle(Title.title(Component.empty(), plugin.text("not-started.joined-as", playerRole.getDisplay())));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
        } else {
            player.sendMessage(plugin.text("not-started.slot-full"));
        }

    }

    @EventHandler(ignoreCancelled = true)
    public void playerMove(PlayerMoveEvent event) {
        boolean shouldCancel = false;
        Location to = event.getTo();
        if (to.getWorld() != Bukkit.getWorlds().get(0)) {
            shouldCancel = true;
        }
        Location spawnPoint = Bukkit.getWorlds().get(0).getSpawnLocation().clone();
        if (spawnPoint.distance(to) > 30) {
            shouldCancel = true;
            plugin.getLogger().info("Mark as cancel cause distance too far");
        }
        if (shouldCancel) event.getPlayer().teleportAsync(spawnPoint).whenComplete((a, b) -> {

        });
    }

    @EventHandler(ignoreCancelled = true)
    public void playerInteract(PlayerInteractEvent event) {
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerInteract(PlayerInteractAtEntityEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerInteract(PlayerInteractEntityEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerAttack(PrePlayerAttackEntityEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerBreakBlock(BlockBreakEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerPlaceBlock(BlockPlaceEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerConsumeItem(PlayerItemConsumeEvent event) {
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
    public void playerDamageBlock(EntityDamageByBlockEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerAttackEntity(EntityDamageByEntityEvent event) {
        event.setCancelled(true);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return false;
        if (args.length == 0) {
            player.sendMessage(plugin.text("general.invalid-command"));
            return true;
        }
        switch (args[0]) {
            case "spectate" -> {
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    player.setGameMode(GameMode.SURVIVAL);
                    attemptAssignRole(player);
                } else {
                    player.setGameMode(GameMode.SPECTATOR);
                    game.setPlayerRole(player, null);
                }
            }
            case "start" -> {
                if (sender.hasPermission("minehunt.admin.start")) {
                    remainingTime = 5 * 20;
                }
            }
            case "shuffle" -> {
                if (sender.hasPermission("minehunt.admin.shuffle")) {
                    game.getAllRoleMembers().forEach(u -> game.setPlayerRole(u, null));
                    tryAssignRolesForNonSpectatingPlayers();
                    tryFixupRoles();
                }
            }
            case "howtoplay" -> player.sendMessage(plugin.text("general.how-to-play"));
            case "codusk" -> player.sendMessage(ChatColor.GOLD + "鱼头，小片三呢？");
            case "help" ->
                    player.sendMessage(plugin.text("general.available-commands", "/minehunt howtoplay, /minehunt spectate", "/minehunt codusk"));
            default -> player.sendMessage(plugin.text("general.invalid-command"));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> list =  new ArrayList<>(List.of("spectate", "howtoplay", "codusk", "help"));
        if(sender.hasPermission("minehunt.admin.start"))
            list.add("start");
        if(sender.hasPermission("minehunt.admin.shuffle"))
            list.add("shuffle");
        return list;
    }
}
