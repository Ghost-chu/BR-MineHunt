package com.ghostchu.plugins.brminehunt;

import com.ghostchu.plugins.brminehunt.game.Game;
import com.ghostchu.plugins.brminehunt.game.gamemodule.GameNotStartedModule;
import lombok.Getter;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class BR_MineHunt extends JavaPlugin implements Listener {
    @Getter
    private static BR_MineHunt instance;
    private Game game;


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
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
    @EventHandler(ignoreCancelled = true)
    public void onPlayerLeft(PlayerQuitEvent event) {
        Iterator<? extends BossBar> bossBarIterator = event.getPlayer().activeBossBars().iterator();
        List<BossBar> bossBarToRemove = new ArrayList<>();
        while (bossBarIterator.hasNext()){
            bossBarToRemove.add(bossBarIterator.next());
        }
        bossBarToRemove.forEach(b->event.getPlayer().hideBossBar(b));
        if(game.getPlayerRole(event.getPlayer()) != null){
            game.getReconnectList().add(event.getPlayer().getUniqueId());
        }
    }
    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (game.getPlayerRole(event.getPlayer()) == null) return;
        event.joinMessage(text("paused.player-reconnected", event.getPlayer().getName()));
        event.getPlayer().setNoDamageTicks(100);
        game.getReconnectList().remove(event.getPlayer().getUniqueId());
    }
//    @EventHandler(ignoreCancelled = true)
//    public void onPlayerChat(AsyncChatEvent event) {
//        event.renderer((source, sourceDisplayName, message, viewer) -> {
//            PlayerRole role = game.getPlayerRole(source);
//            if (role == null) {
//                return Component.text("[观察者] ").color(NamedTextColor.GRAY).append(sourceDisplayName).append(Component.text(": ").color(NamedTextColor.WHITE)).append(message.color(NamedTextColor.GRAY));
//            }
//            return role.getChatPrefixComponent()
//                    .append(Component.text(source.getName()).color(NamedTextColor.WHITE))
//                    .append(Component.text(": ").color(NamedTextColor.WHITE))
//                    .append(message.color(NamedTextColor.WHITE));
//        });
//    }

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
}
