package io.github.aecsocket.ignacio.paper

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import io.papermc.paper.event.player.PlayerTrackEntityEvent
import io.papermc.paper.event.player.PlayerUntrackEntityEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerLocaleChangeEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.event.world.WorldUnloadEvent

internal class IgnacioListener(private val ignacio: Ignacio) : Listener {
    @EventHandler
    fun on(event: ServerTickEndEvent) {
        ignacio.update()
    }

    @EventHandler
    fun on(event: PlayerQuitEvent) {
        ignacio.removePlayerData(event.player)
    }

    @EventHandler
    fun on(event: PlayerLocaleChangeEvent) {
        ignacio.playerData(event.player).updateMessages(event.player.locale())
    }

    @EventHandler
    fun on(event: PlayerTrackEntityEvent) {
        ignacio.primitiveBodies.track(event.player, event.entity)
    }

    @EventHandler
    fun on(event: PlayerUntrackEntityEvent) {
        ignacio.primitiveBodies.untrack(event.player, event.entity)
    }

    @EventHandler
    fun on(event: ChunkLoadEvent) {
        val world = ignacio.worlds[event.world] ?: return
        world.loadChunks(setOf(event.chunk))
    }

    @EventHandler
    fun on(event: ChunkUnloadEvent) {
        val world = ignacio.worlds[event.world] ?: return
        world.unloadChunks(setOf(event.chunk))
    }

    @EventHandler
    fun on(event: WorldUnloadEvent) {
        ignacio.worlds.destroy(event.world)
    }
}
