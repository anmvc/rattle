package io.github.aecsocket.ignacio.paper.world

import io.github.aecsocket.ignacio.*
import io.github.aecsocket.ignacio.paper.Ignacio
import org.bukkit.Chunk
import org.bukkit.World


interface TerrainStrategy : Destroyable {
     fun enable()

     fun disable()

     fun isTerrain(body: PhysicsBody.Read): Boolean

     fun onPhysicsUpdate(deltaTime: Float)

     fun onChunksLoad(chunks: Collection<Chunk>)

     fun onChunksUnload(chunks: Collection<Chunk>)
}

fun interface TerrainStrategyFactory {
    fun create(ignacio: Ignacio, world: World, physics: PhysicsSpace): TerrainStrategy
}

object NoOpTerrainStrategy : TerrainStrategy {
    override fun destroy() {}

    override fun enable() {}

    override fun disable() {}

    override fun isTerrain(body: PhysicsBody.Read) = false

    override fun onPhysicsUpdate(deltaTime: Float) {}

    override fun onChunksLoad(chunks: Collection<Chunk>) {}

    override fun onChunksUnload(chunks: Collection<Chunk>) {}
}

interface EntityStrategy : Destroyable {

}

fun interface EntityStrategyFactory {
    fun create(ignacio: Ignacio, world: World, physics: PhysicsSpace): EntityStrategy
}

object NoOpEntityStrategy : EntityStrategy {
    override fun destroy() {}
}

class PhysicsWorld(
    val world: World,
    val physics: PhysicsSpace,
    val terrain: TerrainStrategy,
    val entities: EntityStrategy,
) : Destroyable {
    private val destroyed = DestroyFlag()
    private var nextDeltaTime = 0f

    override fun destroy() {
        destroyed.mark()
        terrain.destroy()
        entities.destroy()
    }

    operator fun component1() = physics

    fun startPhysicsUpdate(deltaTime: Float) {
        nextDeltaTime = deltaTime
    }

    fun joinPhysicsUpdate() {
        terrain.onPhysicsUpdate(nextDeltaTime)
        physics.update(nextDeltaTime)
    }
}
