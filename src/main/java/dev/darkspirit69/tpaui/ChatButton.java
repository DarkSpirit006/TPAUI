package dev.darkspirit69.tpaui;

import net.md_5.bungee.api.ChatColor;

/** Display text and command for one clickable Java chat action. */
final class ChatButton {
    final String label;
    final ChatColor color;
    final String command;
    final String hoverText;

    ChatButton(String label, ChatColor color, String command, String hoverText) {
        this.label = label;
        this.color = color;
        this.command = command;
        this.hoverText = hoverText;
    }
}
