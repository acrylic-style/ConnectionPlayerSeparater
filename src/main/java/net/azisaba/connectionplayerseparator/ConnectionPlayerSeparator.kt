package net.azisaba.connectionplayerseparator

import com.google.common.reflect.TypeToken
import com.google.inject.Inject
import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.player.ServerPreConnectEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader
import org.slf4j.Logger
import java.io.IOException
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

@Plugin(id = "connectionplayerseparator", name = "ConnectionPlayerSeparator")
open class ConnectionPlayerSeparator @Inject constructor(private val server: ProxyServer, private val logger: Logger, @DataDirectory val dataDirectory: Path) {
    private val isServerOnline = mutableMapOf<String, Boolean>()
    private val forcedHosts = mutableMapOf<String, String>()
    private val firedEvent = mutableSetOf<UUID>()

    private fun checkOnline() {
        server.allServers.filter { s -> isServerOnline.containsKey(s.serverInfo.name) }.forEach { s ->
            isServerOnline[s.serverInfo.name] = runCatching { s.ping().join() }.isSuccess
        }
    }

    private var lastLobby = 0

    @Subscribe
    fun onProxyInitialization(e: ProxyInitializeEvent) {
        reloadConfig()
        server.commandManager.register("cps", Command(this))
        server.scheduler
            .buildTask(this) { checkOnline() }
            .delay(0, TimeUnit.MILLISECONDS)
            .repeat(10, TimeUnit.SECONDS)
            .schedule()
    }

    @Subscribe(order = PostOrder.EARLY)
    fun onServerPreConnectOnLogin(e: ServerPreConnectEvent) {
        if (e.player.currentServer.isPresent || firedEvent.contains(e.player.uniqueId)) return
        firedEvent.add(e.player.uniqueId)
        val host = e.player.virtualHost.map { it.hostName }.orElse(null) ?: return
        forcedHosts[host]?.let { targetServer ->
            val server = this.server.getServer(targetServer)
            if (!server.isPresent) {
                logger.warn("Could not find ServerInfo by $targetServer")
                return@let
            }
            e.result = ServerPreConnectEvent.ServerResult.allowed(server.get()) // server.get() -> life
            return
        }
        if (isServerOnline.isEmpty()) {
            return // "Servers" is empty
        }
        val lobbyServers = isServerOnline.keys
            .filter { s -> isServerOnline[s] ?: false }
            .map { s -> this.server.getServer(s) }
        if (lobbyServers.isEmpty()) {
            e.player.disconnect(Component.text("接続先のサーバーが見つかりません。").color(NamedTextColor.RED))
            return
        }
        lastLobby = (lastLobby + 1) % lobbyServers.size
        lobbyServers[lastLobby]?.ifPresent {
            e.result = ServerPreConnectEvent.ServerResult.allowed(it)
        }
    }

    @Subscribe
    fun onDisconnect(e: DisconnectEvent) {
        firedEvent.remove(e.player.uniqueId)
    }

    @Suppress("UNCHECKED_CAST")
    fun reloadConfig() {
        isServerOnline.clear()
        val config = try {
            YAMLConfigurationLoader.builder().setPath(dataDirectory.resolve("config.yml")).build().load()
        } catch (e: IOException) {
            e.printStackTrace()
            return
        }
        config.getNode("Servers").getList(TypeToken.of(String::class.java)).forEach { isServerOnline[it] = false }
        forcedHosts.clear()
        config.getNode("forcedHosts").childrenMap.forEach { (key, node) ->
            forcedHosts[key.toString()] = node.string ?: ""
        }
    }
}