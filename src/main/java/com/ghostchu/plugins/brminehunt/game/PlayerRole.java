package com.ghostchu.plugins.brminehunt.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public enum PlayerRole {
    RUNNER("逃亡者", NamedTextColor.GREEN),
    HUNTER("猎人", NamedTextColor.RED);


    private final String display;
    private final NamedTextColor color;

    PlayerRole(String display, NamedTextColor color) {
        this.display = display;
        this.color = color;
    }

    public String getDisplay() {
        return display;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public Component getChatPrefixComponent() {
        return Component.text("[" + getDisplay() + "]").color(getColor());
    }
}
