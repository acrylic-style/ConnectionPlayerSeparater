package net.azisaba.connectionplayerseparator

import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.config.ServerInfo
import net.md_5.bungee.api.event.ServerConnectEvent
import net.md_5.bungee.api.plugin.Listener
import net.md_5.bungee.api.plugin.Plugin
import net.md_5.bungee.config.Configuration
import net.md_5.bungee.config.ConfigurationProvider
import net.md_5.bungee.config.YamlConfiguration
import net.md_5.bungee.event.EventHandler
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ConnectionPlayerSeparator : Plugin(), Listener {
    companion object {
        private val isServerOnline = mutableMapOf<String, Boolean>()
        private val forcedHosts = mutableMapOf<String, String>()
        lateinit var CPS: ConnectionPlayerSeparator
    }

    private fun checkOnline() {
        proxy.servers.values.filter { s: ServerInfo -> isServerOnline.containsKey(s.name) }.forEach { info ->
            info.ping { _, error -> isServerOnline[info.name] = error == null }
        }
    }

    init {
        CPS = this
    }

    private var lastLobby = 0
    @EventHandler
    fun onConnect(event: ServerConnectEvent) {
        if (event.reason != ServerConnectEvent.Reason.JOIN_PROXY) return
        val host = event.player.pendingConnection.virtualHost.hostName
        forcedHosts[host]?.let { targetServer ->
            val server = ProxyServer.getInstance().getServerInfo(targetServer)
            if (server == null) {
                logger.warning("Could not find ServerInfo by $targetServer")
                return@let
            }
            event.target = server
            return
        }
        val lobbyServers = isServerOnline.keys
            .filter { s -> isServerOnline[s] ?: false }
            .map { s -> proxy.getServerInfo(s) }
        if (lobbyServers.isEmpty()) {
            // event.player.disconnect(*TextComponent.fromLegacyText("${ChatColor.RED}接続先のサーバーが見つかりません。"))
            event.isCancelled = true
            return
        }
        lastLobby = (lastLobby + 1) % lobbyServers.size
        event.target = lobbyServers[lastLobby]
    }

    override fun onEnable() {
        reloadConfig()
        proxy.pluginManager.registerListener(this, this)
        proxy.pluginManager.registerCommand(this, Command)
        proxy.scheduler.schedule(
            this,
            { CPS.checkOnline() },
            0,
            10,
            TimeUnit.SECONDS,
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun reloadConfig() {
        isServerOnline.clear()
        val file = File(dataFolder, "config.yml")
        val config = try {
            ConfigurationProvider.getProvider(YamlConfiguration::class.java).load(file)
        } catch (e: IOException) {
            e.printStackTrace()
            return
        }
        config.getStringList("Servers").forEach { isServerOnline[it] = false }
        val forcedHostsIn = Configuration::class.java
            .getDeclaredField("self")
            .apply { isAccessible = true }
            .get(config.getSection("forcedHosts")) as Map<String, Any?>
        forcedHosts.clear()
        forcedHosts.putAll(forcedHostsIn.filterValues { it != null }.mapValues { (_, value) -> value.toString() })
    }
}