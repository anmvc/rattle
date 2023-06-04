package io.github.aecsocket.rattle.impl

import io.github.aecsocket.alexandria.desc.BossBarDesc
import io.github.aecsocket.alexandria.hook.AlexandriaHook
import io.github.aecsocket.glossa.Glossa
import io.github.aecsocket.glossa.MessageProxy
import io.github.aecsocket.glossa.messageProxy
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.rapier.RapierEngine
import io.github.aecsocket.rattle.world.DynamicTerrain
import io.github.oshai.kotlinlogging.KLogger
import net.kyori.adventure.text.format.TextColor
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

val rattleManifest = AlexandriaHook.Manifest(
    id = "rattle",
    accentColor = TextColor.color(0xdeab14),
    langResources = listOf(
        "lang/en-US.yml",
    ),
)

abstract class RattleHook {
    @ConfigSerializable
    data class Settings(
        override val defaultLocale: Locale = AlexandriaHook.FallbackLocale,
        val timeStepMultiplier: Real = 1.0,
        val worlds: Map<String, World> = emptyMap(),
        val simpleBodies: SimpleBodies.Settings = SimpleBodies.Settings(),
        val stats: Stats = Stats(),
        val jobs: Jobs = Jobs(),
        val rapier: RapierEngine.Settings = RapierEngine.Settings(),
    ) : AlexandriaHook.Settings {
        @ConfigSerializable
        data class World(
            val physics: PhysicsSpace.Settings = PhysicsSpace.Settings(),
            val terrain: DynamicTerrain.Settings = DynamicTerrain.Settings(),
        )

        @ConfigSerializable
        data class Stats(
            val timingBuffers: List<Double> = listOf(5.0, 15.0, 60.0),
            val timingBarBuffer: Double = 5.0,
            val timingBar: BossBarDesc = BossBarDesc(),
        )

        @ConfigSerializable
        data class Jobs(
            val workerThreads: Int = 0,
            val threadTerminateTime: Double = 5.0,
            val commandTaskTerminateTime: Double = 5.0,
        )
    }

    abstract val ax: AlexandriaHook<*>
    abstract val log: KLogger
    abstract val settings: Settings
    abstract val glossa: Glossa

    private lateinit var executor: ExecutorService
    lateinit var engine: RapierEngine
        private set
    lateinit var messages: MessageProxy<RattleMessages>
        private set

    fun init() {
        // TODO allow plugins to hook in and register interaction layers
        engine = RapierEngine.Builder(settings.rapier).build()
        log.info { "Loaded physics engine ${engine.name} v${engine.version}" }

        val executorId = AtomicInteger(1)
        val workerThreads = numThreads(settings.jobs.workerThreads, 4)
        executor = Executors.newFixedThreadPool(workerThreads) { task ->
            Thread(task, "Physics-Worker-${executorId.getAndIncrement()}")
        }
        log.info { "Set up physics worker thread pool with $workerThreads threads" }
    }

    fun load(platform: RattlePlatform<*, *>?) {
        messages = glossa.messageProxy()
        platform?.load()
    }

    fun reload() {
        engine.settings = settings.rapier
    }

    fun destroy(platform: RattlePlatform<*, *>?) {
        executor.shutdown()
        log.info { "Waiting ${settings.jobs.threadTerminateTime} sec for physics jobs" }
        if (!executor.awaitTermination((settings.jobs.threadTerminateTime * 1000).toLong(), TimeUnit.MILLISECONDS)) {
            executor.shutdownNow()
            log.warn { "Could not wait for physics jobs to finish" }
        }

        platform?.destroy()

        engine.destroy()
    }

    fun runTask(task: Runnable) {
        executor.submit {
            try {
                task.run()
            } catch (ex: Exception) {
                log.warn(ex) { "Could not run physics task" }
            }
        }
    }
}