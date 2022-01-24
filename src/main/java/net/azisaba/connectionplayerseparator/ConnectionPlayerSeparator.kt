package net.azisaba.connectionplayerseparator

import com.google.common.reflect.TypeToken
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PostLoginEvent
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
import java.util.concurrent.TimeUnit

@Plugin(id = "connectionplayerseparator", name = "ConnectionPlayerSeparator")
open class ConnectionPlayerSeparator(val server: ProxyServer, val logger: Logger, @DataDirectory val dataDirectory: Path, dummy: Void?) {
    @Inject constructor(server: ProxyServer, logger: Logger, @DataDirectory dataDirectory: Path): this(server, logger, dataDirectory, null)

    companion object {
        private val isServerOnline = mutableMapOf<String, Boolean>()
        private val forcedHosts = mutableMapOf<String, String>()
        lateinit var CPS: ConnectionPlayerSeparator
    }

    private fun checkOnline() {
        server.allServers.filter { s -> isServerOnline.containsKey(s.serverInfo.name) }.forEach { s ->
            isServerOnline[s.serverInfo.name] = runCatching { s.ping().join() }.isSuccess
        }
    }

    init {
        CPS = this
    }

    private var lastLobby = 0

    @Subscribe
    fun onProxyInitialization(e: ProxyInitializeEvent) {
        reloadConfig()
        server.commandManager.register("cps", Command)
        server.scheduler
            .buildTask(this) { CPS.checkOnline() }
            .delay(0, TimeUnit.MILLISECONDS)
            .repeat(10, TimeUnit.SECONDS)
            .schedule()
    }

    @Subscribe
    fun onLogin(e: PostLoginEvent) {
        val host = e.player.virtualHost.map { it.hostName }.orElse(null) ?: return
        forcedHosts[host]?.let { targetServer ->
            val server = this.server.getServer(targetServer)
            if (!server.isPresent) {
                logger.warn("Could not find ServerInfo by $targetServer")
                return@let
            }
            e.player.createConnectionRequest(server.get()).fireAndForget()
            return
        }
        val lobbyServers = isServerOnline.keys
            .filter { s -> isServerOnline[s] ?: false }
            .map { s -> this.server.getServer(s) }
        if (lobbyServers.isEmpty()) {
            e.player.disconnect(Component.text("接続先のサーバーが見つかりません。").color(NamedTextColor.RED))
            return
        }
        lastLobby = (lastLobby + 1) % lobbyServers.size
        lobbyServers[lastLobby]?.ifPresent { e.player.createConnectionRequest(it).fireAndForget() }
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