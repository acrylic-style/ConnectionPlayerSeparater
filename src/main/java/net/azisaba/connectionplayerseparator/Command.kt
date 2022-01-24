package net.azisaba.connectionplayerseparator

import com.velocitypowered.api.command.SimpleCommand
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

// /cps
object Command : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val sender = invocation.source()
        val args = invocation.arguments()
        if (!sender.hasPermission("cps.op")) {
            return sender.sendMessage(Component.text("ぽまえけんげんないやろ").color(NamedTextColor.RED))
        }
        // 引数が指定されていない場合 (引数の個数が0以下の場合)
        if (args.isEmpty()) {
            sender.sendMessage(Component.text("------------------------使い方------------------------").color(NamedTextColor.GOLD))
            sender.sendMessage(Component.text("/${invocation.alias()} reload :リロード").color(NamedTextColor.BLUE))
            return
        }
        if (args[0].equals("reload", ignoreCase = true) || args[0].equals("r", ignoreCase = true)) {
            ConnectionPlayerSeparator.CPS.reloadConfig()
            sender.sendMessage(Component.text("リロード完了").color(NamedTextColor.GREEN))
        }
    }
}