package io.github.aecsocket.rattle.paper

import io.github.aecsocket.alexandria.log.Log
import io.github.aecsocket.alexandria.log.info
import io.github.aecsocket.alexandria.paper.AlexandriaPlugin
import io.github.aecsocket.alexandria.paper.SETTINGS_PATH
import io.github.aecsocket.alexandria.paper.extension.alexandriaPaperSerializers
import io.github.aecsocket.alexandria.paper.extension.forWorld
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.rapier.RapierEngine
import org.bukkit.World
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ObjectMapper
import java.util.concurrent.atomic.AtomicBoolean

lateinit var Rattle: RattlePlugin
    private set

class RattlePlugin : AlexandriaPlugin<RattleHook.Settings>(rattleManifest), RattleHook {
    override val configOptions: ConfigurationOptions = ConfigurationOptions.defaults()
        .serializers { it
            .registerAll(alexandriaPaperSerializers)
            .registerAnnotatedObjects(ObjectMapper.factoryBuilder()
                .addDiscoverer(dataClassFieldDiscoverer())
                .build()
            )
        }

    override val savedResources = listOf(SETTINGS_PATH)

    private lateinit var mEngine: RapierEngine
    override val engine: PhysicsEngine get() = mEngine

    private val mPhysics = HashMap<World, PaperWorldPhysics>()
    val physics: Map<World, PaperWorldPhysics> get() = mPhysics

    private val stepping = AtomicBoolean(false)

    init {
        Rattle = this
    }

    override fun loadSettings(node: ConfigurationNode) = node.get() ?: RattleHook.Settings()

    override fun onLoad(log: Log) {
        PaperRattleCommand(this)
        mEngine = RapierEngine(settings.rapier)
        log.info { "Loaded physics engine ${mEngine.name} v${mEngine.version}" }
    }

    override fun onReload(log: Log) {
        mEngine.settings = settings.rapier
    }

    override fun onEnable() {
        super.onEnable()
        scheduling.onServer().runRepeating {
            if (stepping.getAndSet(true)) return@runRepeating

            mEngine.stepSpaces(
                0.05 * settings.timeStepMultiplier,
                mPhysics.map { (_, world) -> world.physics },
            )

            stepping.set(false)
        }
    }

    override val worlds = Worlds()
    inner class Worlds : RattleHook.Worlds {
        operator fun contains(world: World) = mPhysics.contains(world)
        override fun contains(world: io.github.aecsocket.rattle.World) = contains(unwrap(world))

        operator fun get(world: World) = mPhysics[world]
        override fun get(world: io.github.aecsocket.rattle.World) = get(unwrap(world))

        fun getOrCreate(world: World) = mPhysics.computeIfAbsent(world) {
            RattleHook.createWorldPhysics(
                this@RattlePlugin,
                settings.worlds.forWorld(world)
            ) { physics, terrain, entities ->
                PaperWorldPhysics(world, physics, terrain, entities)
            }
        }
        override fun getOrCreate(world: io.github.aecsocket.rattle.World) = getOrCreate(unwrap(world))

        fun destroy(world: World) {
            val data = mPhysics.remove(world) ?: return
            data.physics.destroy()
        }
        override fun destroy(world: io.github.aecsocket.rattle.World) = destroy(unwrap(world))
    }
}
