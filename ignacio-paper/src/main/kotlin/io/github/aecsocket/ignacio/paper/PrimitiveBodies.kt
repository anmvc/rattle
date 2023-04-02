package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.alexandria.paper.extension.runDelayed
import io.github.aecsocket.ignacio.*
import io.github.aecsocket.ignacio.paper.render.*
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PrimitiveBodies internal constructor(private val ignacio: Ignacio) {
    private inner class Instance(
        val id: Int,
        val physics: PhysicsSpace,
        val body: PhysicsBody,
        val render: Render?,
        val marker: Entity,
    ) {
        val destroyed = AtomicBoolean(false)

        fun destroy() {
            if (destroyed.getAndSet(true)) return
            render?.despawn()
            ignacio.runDelayed {
                marker.remove()
            }
            ignacio.engine.launchTask {
                physics.bodies.remove(body)
                physics.bodies.destroy(body)
            }
        }
    }

    private val nextId = AtomicInteger()
    private val lock = Any()
    private val instances = HashMap<Int, Instance>()
    private val entityToInstance = HashMap<Entity, Instance>()
    private val bodyToInstance = HashMap<World, MutableMap<PhysicsBody, Instance>>()
    private var nextMove = emptyMap<Entity, Location>()

    val count get() = synchronized(lock) { instances.size }

    fun create(
        world: World,
        transform: Transform,
        createBody: (physics: PhysicsSpace) -> PhysicsBody,
        createRender: ((tracker: PlayerTracker) -> Render)?,
    ): Int {
        val id = nextId.getAndIncrement()
        spawnMarkerEntity(transform.position.location(world)) { marker ->
            val (physics) = ignacio.worlds.getOrCreate(world)
            val body = createBody(physics)
            physics.bodies.add(body)
            body.writeAs<PhysicsBody.MovingWrite> { moving ->
                moving.activate()
            }
            val render = createRender?.invoke(marker.playerTracker())
            val instance = Instance(id, physics, body, render, marker)
            synchronized(lock) {
                instances[id] = instance
                entityToInstance[marker] = instance
                bodyToInstance.computeIfAbsent(world) { HashMap() }[body] = instance
            }

            render?.let {
                ignacio.runDelayed {
                    it.spawn()
                }
            }
        }
        return id
    }

    private fun removeMapping(instance: Instance) {
        instances -= instance.id
        entityToInstance -= instance.marker
        bodyToInstance[instance.marker.world]?.remove(instance.body)
    }

    fun destroy(id: Int): Boolean {
        return synchronized(lock) {
            instances.remove(id)?.let { instance ->
                removeMapping(instance)
                instance.destroy()
                true
            } ?: false
        }
    }

    fun destroyAll() {
        synchronized(lock) {
            instances.forEach { (_, instance) ->
                instance.destroy()
            }
            instances.clear()
            entityToInstance.clear()
            bodyToInstance.clear()
        }
    }

    operator fun get(id: Int) = instances[id]?.run { physics to body }

    internal fun syncUpdate() {
        // TODO Folia: tasks must be individually scheduled per entity
        nextMove.forEach { (entity, location) ->
            entity.teleport(location)
        }
    }

    internal fun physicsUpdate() {
        val nextMove = HashMap<Entity, Location>()
        synchronized(lock) {
            instances.toMap().forEach { (_, instance) ->
                if (!instance.marker.isValid || !instance.body.added) {
                    removeMapping(instance)
                    instance.destroy()
                    return@forEach
                }

                instance.body.read { body ->
                    val transform = body.transform
                    nextMove[instance.marker] = transform.position.location(instance.marker.world)
                    instance.render?.transform = transform
                }
            }
        }
        this.nextMove = nextMove
    }

    internal fun onWorldUnload(world: World) {
        synchronized(lock) {
            bodyToInstance.remove(world)?.forEach { (_, instance) ->
                removeMapping(instance)
                instance.destroy()
            }
        }
    }

    internal fun onEntityRemove(entity: Entity) {
        synchronized(lock) {
            entityToInstance.remove(entity)?.let { instance ->
                removeMapping(instance)
                instance.destroy()
            }
        }
    }

    internal fun onPlayerTrackEntity(player: Player, entity: Entity) {
        synchronized(lock) {
            val instance = entityToInstance[entity] ?: return
            instance.render?.spawn(player)
        }
    }

    internal fun onPlayerUntrackEntity(player: Player, entity: Entity) {
        synchronized(lock) {
            val instance = entityToInstance[entity] ?: return
            instance.render?.despawn(player)
        }
    }
}