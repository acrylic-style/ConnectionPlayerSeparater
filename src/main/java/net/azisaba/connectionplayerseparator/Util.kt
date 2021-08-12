package net.azisaba.connectionplayerseparator

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.CommandSender
import net.md_5.bungee.api.chat.TextComponent

object Util {
    fun CommandSender.send(message: String) {
        sendMessage(*TextComponent.fromLegacyText(message.replace("  ", " ${ChatColor.RESET} ${ChatColor.RESET}")))
    }
}
