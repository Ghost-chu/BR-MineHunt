package com.ghostchu.plugins.brminehunt;

import com.ghostchu.plugins.brminehunt.game.Game;
import com.ghostchu.plugins.brminehunt.game.PlayerRole;
import com.ghostchu.plugins.brminehunt.game.gamemodule.GameNotStartedModule;
import com.ghostchu.plugins.brminehunt.game.gamemodule.GameStartedModule;
import com.google.gson.Gson;
import lombok.Getter;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class BR_MineHunt extends JavaPlugin implements Listener, @NotNull PluginMessageListener {
    @Getter
    private static BR_MineHunt instance;
    private Game game;
    private Gson gson = new Gson();


    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        instance = this;
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Default world: " + Bukkit.getWorlds().get(0));
        game = new Game(this);
        game.setActiveModule(new GameNotStartedModule(this, game));
        World defWorld = Bukkit.getWorlds().get(0);
        Bukkit.getWorlds().get(0).getChunkAt(defWorld.getSpawnLocation()).addPluginChunkTicket(this);
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "journeymap:perm_req", this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "journeymap:perm_req");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerLeft(PlayerQuitEvent event) {
        Iterator<? extends BossBar> bossBarIterator = event.getPlayer().activeBossBars().iterator();
        List<BossBar> bossBarToRemove = new ArrayList<>();
        while (bossBarIterator.hasNext()) {
            bossBarToRemove.add(bossBarIterator.next());
        }
        bossBarToRemove.forEach(b -> event.getPlayer().hideBossBar(b));
        if (game.getPlayerRole(event.getPlayer()) != null) {
            game.getReconnectList().add(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerRegisterChannelEvent event) {
        getLogger().info(event.getChannel());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (game.getPlayerRole(event.getPlayer()) == null) return;
        event.joinMessage(text("paused.player-reconnected", event.getPlayer().getName()));
        event.getPlayer().setNoDamageTicks(100);
        game.getReconnectList().remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void antiCheat(PlayerJoinEvent event) {
        playerAntiCheat(event.getPlayer());
        Bukkit.getScheduler().runTaskLater(this, () -> {
            event.getPlayer().sendMessage(text("general.anti-cheat-warning"));
        }, 60L);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void antiCheat(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> playerAntiCheat(event.getPlayer()), 2L);
    }

    private void playerAntiCheat(Player player) {
        player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<dark_aqua> </dark_aqua><gold> </gold><dark_aqua> </dark_aqua><gold> </gold><dark_aqua> </dark_aqua><gold> </gold><yellow> </yellow>"
        ));
        player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<dark_aqua> </dark_aqua><gold> </gold><dark_aqua> </dark_aqua><gold> </gold><dark_aqua> </dark_aqua><gold> </gold><light_purple> </light_purple>"
        ));
        player.sendMessage(Component.text("§f§a§i§r§x§a§e§r§o"));
        player.sendMessage(Component.text("§3 §6 §3 §6 §3 §6 §d"));
        player.sendMessage(Component.text("§3 §6 §3 §6 §3 §6 §e"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerChatting(AsyncPlayerChatEvent event) {
        event.setCancelled(true);
        PlayerRole sourceRole = game.getPlayerRole(event.getPlayer());
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            PlayerRole viewerRole = game.getPlayerRole(onlinePlayer);
            Component messageBody = LegacyComponentSerializer.legacySection().deserialize(event.getMessage());
            if (sourceRole == null && viewerRole != null) {
                if (game.getActiveModule() instanceof GameStartedModule) {
                    continue;
                    //messageBody = Component.text("<比赛进行时无法查看观察者的消息>").color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC);
                }
            }
            Component finalMessage;
            if (sourceRole == null) {
                finalMessage = Component.text("[观察者]").color(NamedTextColor.GRAY).append(LegacyComponentSerializer.legacySection().deserialize(event.getPlayer().getName())).append(Component.text(": ").color(NamedTextColor.WHITE)).append(messageBody.color(NamedTextColor.GRAY));
            } else {
                finalMessage = sourceRole.getChatPrefixComponent()
                        .append(Component.text(event.getPlayer().getName()).color(NamedTextColor.WHITE))
                        .append(Component.text(": ").color(NamedTextColor.WHITE))
                        .append(messageBody.color(NamedTextColor.WHITE));
            }
            onlinePlayer.sendMessage(finalMessage);
        }
    }

    public Component text(String key, Object... args) {
        ConfigurationSection section = getConfig().getConfigurationSection("lang");
        if (section == null) throw new IllegalStateException("lang section not found");
        String string = section.getString(key, "missing:" + key);
        Component component = MiniMessage.miniMessage().deserialize(string);
        return fillArgs(component, convert(args));
    }

    @NotNull
    public Component[] convert(@Nullable Object... args) {
        if (args == null || args.length == 0) {
            return new Component[0];
        }
        Component[] components = new Component[args.length];
        for (int i = 0; i < args.length; i++) {
            Object obj = args[i];
            if (obj == null) {
                components[i] = Component.empty();
                continue;
            }
            Class<?> clazz = obj.getClass();
            if (obj instanceof Component component) {
                components[i] = component;
                continue;
            }
            if (obj instanceof ComponentLike componentLike) {
                components[i] = componentLike.asComponent();
                continue;
            }
            // Check
            try {
                if (Character.class.equals(clazz)) {
                    components[i] = Component.text((char) obj);
                    continue;
                }
                if (Byte.class.equals(clazz)) {
                    components[i] = Component.text((Byte) obj);
                    continue;
                }
                if (Integer.class.equals(clazz)) {
                    components[i] = Component.text((Integer) obj);
                    continue;
                }
                if (Long.class.equals(clazz)) {
                    components[i] = Component.text((Long) obj);
                    continue;
                }
                if (Float.class.equals(clazz)) {
                    components[i] = Component.text((Float) obj);
                    continue;
                }
                if (Double.class.equals(clazz)) {
                    components[i] = Component.text((Double) obj);
                    continue;
                }
                if (Boolean.class.equals(clazz)) {
                    components[i] = Component.text((Boolean) obj);
                    continue;
                }
                if (String.class.equals(clazz)) {
                    components[i] = LegacyComponentSerializer.legacySection().deserialize((String) obj);
                    continue;
                }
                components[i] = LegacyComponentSerializer.legacySection().deserialize(obj.toString());
            } catch (Exception exception) {
                exception.printStackTrace();
                components[i] = LegacyComponentSerializer.legacySection().deserialize(obj.toString());
            }
        }
        return components;
    }

    /**
     * Replace args in origin to args
     *
     * @param origin origin
     * @param args   args
     * @return filled component
     */
    @NotNull
    public static Component fillArgs(@NotNull Component origin, @Nullable Component... args) {
        for (int i = 0; i < args.length; i++) {
            origin = origin.replaceText(TextReplacementConfig.builder()
                    .matchLiteral("{" + i + "}")
                    .replacement(args[i] == null ? Component.empty() : args[i])
                    .build());
        }
        return origin.compact();
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        player.kick(Component.text("JourneyMap incompatible with this server."));
    }
}
