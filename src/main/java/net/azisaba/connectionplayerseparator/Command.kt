package net.azisaba.connectionplayerseparator

import net.azisaba.connectionplayerseparator.Util.send
import net.md_5.bungee.api.CommandSender
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.plugin.Command

object Command : Command("cps") {
    override fun execute(sender: CommandSender, args: Array<String>) {
        if (!sender.hasPermission("cps.op")) {
            sender.send(ChatColor.RED.toString() + "ぽまえけんげんないやろ")
            return
        }
        // 引数が指定されていない場合 (引数の個数が0以下の場合)
        if (args.isEmpty()) {
            sender.send(ChatColor.GOLD.toString() + "------------------------使い方------------------------")
            sender.send(ChatColor.BLUE.toString() + "/" + name + " reload :リロード")
            return
        }
        if (args[0].equals("reload", ignoreCase = true) || args[0].equals("r", ignoreCase = true)) {
            ConnectionPlayerSeparator.CPS.reloadConfig()
            sender.send(ChatColor.GREEN.toString() + "リロード完了")
            return
        }
    }
}