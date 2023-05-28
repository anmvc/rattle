package io.github.aecsocket.rattle

import cloud.commandframework.CommandManager
import cloud.commandframework.arguments.CommandArgument
import cloud.commandframework.arguments.standard.DoubleArgument
import cloud.commandframework.arguments.standard.IntegerArgument
import cloud.commandframework.context.CommandContext
import io.github.aecsocket.alexandria.extension.flag
import io.github.aecsocket.alexandria.extension.hasFlag
import io.github.aecsocket.alexandria.hook.HookCommand
import io.github.aecsocket.glossa.messageProxy
import io.github.aecsocket.klam.nextDVec3
import net.kyori.adventure.audience.Audience
import kotlin.random.Random

private const val ALL = "all"
private const val BODY = "body"
private const val BOX = "box"
private const val CAPSULE = "capsule"
private const val CCD = "ccd"
private const val COUNT = "count"
private const val CREATE = "create"
private const val DENSITY = "density"
private const val DESTROY = "destroy"
private const val FIXED = "fixed"
private const val FRICTION = "friction"
private const val HALF_EXTENT = "half-extent"
private const val HALF_HEIGHT = "half-height"
private const val LOCATION = "location"
private const val MASS = "mass"
private const val MOVING = "moving"
private const val RADIUS = "radius"
private const val RESTITUTION = "restitution"
private const val SPACE = "space"
private const val SPHERE = "sphere"
private const val SPREAD = "spread"
private const val WORLD = "world"

typealias RealArgument<C> = DoubleArgument<C>

abstract class RattleCommand<C : Audience>(
    private val rattle: RattleHook,
    manager: CommandManager<C>,
) : HookCommand<C>(rattle, manager) {
    private val messages = rattle.glossa.messageProxy<RattleMessages>()

    protected abstract fun locationArgumentOf(key: String): CommandArgument<C, *>

    protected abstract fun CommandContext<C>.getLocation(key: String): Location

    protected abstract fun worldArgumentOf(key: String): CommandArgument<C, *>

    protected abstract fun CommandContext<C>.getWorld(key: String): World

    init {
        root.run {
            literal(SPACE)
                .argument(worldArgumentOf(WORLD))
                .run {
                    manager.command(
                        literal(CREATE)
                            .axPermission("$SPACE.$CREATE")
                            .axHandler(::spaceCreate)
                    )
                    manager.command(
                        literal(DESTROY)
                            .axPermission("$SPACE.$DESTROY")
                            .axHandler(::spaceDestroy)
                    )
                }

            literal(BODY).run {
                literal(CREATE)
                    .axPermission("$BODY.$CREATE")
                    .argument(locationArgumentOf(LOCATION))
                    .flag(manager.flagBuilder(COUNT)
                        .withAliases("n")
                        .withArgument(IntegerArgument.builder<C>(COUNT).withMin(1))
                    )
                    .flag(manager.flagBuilder(SPREAD)
                        .withAliases("s")
                        .withArgument(RealArgument.builder<C>(SPREAD).withMin(0))
                    )
                    .flag(manager.flagBuilder(DENSITY)
                        .withAliases("d")
                        .withArgument(RealArgument.builder<C>(DENSITY).withMin(0))
                    )
                    .flag(manager.flagBuilder(MASS)
                        .withAliases("m")
                        .withArgument(RealArgument.builder<C>(MASS).withMin(0))
                    )
                    .flag(manager.flagBuilder(FRICTION)
                        .withArgument(RealArgument.builder<C>(FRICTION).withMin(0))
                    )
                    .flag(manager.flagBuilder(RESTITUTION)
                        .withArgument(RealArgument.builder<C>(RESTITUTION).withMin(0))
                    )
                    .run {
                        literal(FIXED)
                            .run {
                                manager.command(
                                    literal(SPHERE)
                                        .argument(RealArgument.of(RADIUS))
                                        .axHandler(::bodyCreateFixedSphere)
                                )
                                manager.command(
                                    literal(BOX)
                                        .argument(RealArgument.of(HALF_EXTENT))
                                        .axHandler(::bodyCreateFixedBox)
                                )
                                manager.command(
                                    literal(CAPSULE)
                                        .argument(RealArgument.of(HALF_HEIGHT))
                                        .argument(RealArgument.of(RADIUS))
                                        .axHandler(::bodyCreateFixedCapsule)
                                )
                            }

                        literal(MOVING)
                            .flag(manager.flagBuilder(CCD))
                            .run {
                                manager.command(
                                    literal(SPHERE)
                                        .argument(RealArgument.of(RADIUS))
                                        .axHandler(::bodyCreateMovingSphere)
                                )
                                manager.command(
                                    literal(BOX)
                                        .argument(RealArgument.of(HALF_EXTENT))
                                        .axHandler(::bodyCreateMovingBox)
                                )
                                manager.command(
                                    literal(CAPSULE)
                                        .argument(RealArgument.of(HALF_HEIGHT))
                                        .argument(RealArgument.of(RADIUS))
                                        .axHandler(::bodyCreateMovingCapsule)
                                )
                            }
                    }

                literal(DESTROY)
                    .axPermission("$BODY.$DESTROY")
                    .run {
                        manager.command(literal(ALL)
                            .axHandler(::bodyDestroyAll)
                        )
                    }
            }
        }
    }

    private fun spaceCreate(ctx: CommandContext<C>) {
        val sender = ctx.sender
        val messages = messages.forAudience(sender)
        val world = ctx.getWorld(WORLD)
        if (rattle.worlds.contains(world)) {
            messages.error.space.alreadyExists(
                world = world.key.asString(),
            ).sendTo(sender)
            return
        }

        rattle.worlds.getOrCreate(world)

        messages.command.space.create(
            world = world.key.asString(),
        ).sendTo(sender)
    }

    private fun spaceDestroy(ctx: CommandContext<C>) {
        val sender = ctx.sender
        val messages = messages.forAudience(sender)
        val world = ctx.getWorld(WORLD)
        if (!rattle.worlds.contains(world)) {
            messages.error.space.doesNotExist(
                world = world.key.asString(),
            ).sendTo(sender)
            return
        }

        rattle.worlds.destroy(world)

        messages.command.space.destroy(
            world = world.key.asString(),
        ).sendTo(sender)
    }

    private data class BodyCreateInfo(
        val count: Int,
        val positionX: Double,
        val positionY: Double,
        val positionZ: Double,
    )

    private fun bodyCreate(
        ctx: CommandContext<C>,
        geom: Geometry,
        createBody: (Iso) -> RigidBody,
    ): BodyCreateInfo {
        val location = ctx.getLocation(LOCATION)
        val count = ctx.flag(COUNT) ?: 1
        val spread = ctx.flag(SPREAD) ?: 0.0
        val mass = ctx.flag<Real>(DENSITY)?.let { Mass.Density(it) }
            ?: ctx.flag<Real>(MASS)?.let { Mass.Constant(it) }
            ?: Mass.Density(1.0)
        val friction = ctx.flag(FRICTION) ?: DEFAULT_FRICTION
        val restitution = ctx.flag(RESTITUTION) ?: DEFAULT_RESTITUTION

        val (physics) = rattle.worlds.getOrCreate(location.world)

        val shape = rattle.engine.createShape(geom)
        val material = rattle.engine.createMaterial(
            friction = friction,
            restitution = restitution,
        )

        repeat(count) {
            val offset = (Random.nextDVec3() * 2.0 - 1.0) * spread
            val position = Iso(location.position + offset)
            val body = createBody(position)
            body.addTo(physics)

            val collider = rattle.engine.createCollider(
                shape = shape,
                material = material,
                mass = mass,
            )
            collider.addTo(physics)
            collider.write { coll ->
                coll.parent = body
            }

            todoBodyStuff(ctx.sender, body)
        }

        return BodyCreateInfo(
            count = count,
            positionX = location.position.x,
            positionY = location.position.y,
            positionZ = location.position.z,
        )
    }

    abstract fun todoBodyStuff(sender: C, body: RigidBody)

    private fun bodyCreateFixed(
        ctx: CommandContext<C>,
        geom: Geometry,
    ): BodyCreateInfo {
        return bodyCreate(ctx, geom) { position ->
            rattle.engine.createFixedBody(position)
        }
    }

    private fun bodyCreateMoving(
        ctx: CommandContext<C>,
        geom: Geometry,
    ): BodyCreateInfo {
        val ccd = ctx.hasFlag(CCD)
        return bodyCreate(ctx, geom) { position ->
            rattle.engine.createMovingBody(
                position = position,
                isCcdEnabled = ccd,
            )
        }
    }

    private fun sphereGeom(ctx: CommandContext<C>): Geometry {
        val radius = ctx.get<Real>(RADIUS)
        return Sphere(radius)
    }

    private fun boxGeom(ctx: CommandContext<C>): Geometry {
        val halfExtent = ctx.get<Real>(HALF_EXTENT)
        return Box(Vec(halfExtent))
    }

    private fun capsuleGeom(ctx: CommandContext<C>): Geometry {
        val halfHeight = ctx.get<Real>(HALF_HEIGHT)
        val radius = ctx.get<Real>(RADIUS)
        return Capsule(halfHeight, radius)
    }

    private fun bodyCreateFixedSphere(ctx: CommandContext<C>) {
        val sender = ctx.sender
        val (count, positionX, positionY, positionZ) = bodyCreateFixed(ctx, sphereGeom(ctx))
        messages.forAudience(sender).command.body.create.fixed.sphere(
            count = count,
            positionX = positionX, positionY = positionY, positionZ = positionZ,
        ).sendTo(sender)
    }

    private fun bodyCreateFixedBox(ctx: CommandContext<C>) {
        val sender = ctx.sender
        val (count, positionX, positionY, positionZ) = bodyCreateFixed(ctx, boxGeom(ctx))
        messages.forAudience(sender).command.body.create.fixed.box(
            count = count,
            positionX = positionX, positionY = positionY, positionZ = positionZ,
        ).sendTo(sender)
    }

    private fun bodyCreateFixedCapsule(ctx: CommandContext<C>) {
        val sender = ctx.sender
        val (count, positionX, positionY, positionZ) = bodyCreateFixed(ctx, capsuleGeom(ctx))
        messages.forAudience(sender).command.body.create.fixed.capsule(
            count = count,
            positionX = positionX, positionY = positionY, positionZ = positionZ,
        ).sendTo(sender)
    }

    private fun bodyCreateMovingSphere(ctx: CommandContext<C>) {
        val sender = ctx.sender
        val (count, positionX, positionY, positionZ) = bodyCreateMoving(ctx, sphereGeom(ctx))
        messages.forAudience(sender).command.body.create.moving.sphere(
            count = count,
            positionX = positionX, positionY = positionY, positionZ = positionZ,
        ).sendTo(sender)
    }

    private fun bodyCreateMovingBox(ctx: CommandContext<C>) {
        val sender = ctx.sender
        val (count, positionX, positionY, positionZ) = bodyCreateMoving(ctx, boxGeom(ctx))
        messages.forAudience(sender).command.body.create.moving.box(
            count = count,
            positionX = positionX, positionY = positionY, positionZ = positionZ,
        ).sendTo(sender)
    }

    private fun bodyCreateMovingCapsule(ctx: CommandContext<C>) {
        val sender = ctx.sender
        val (count, positionX, positionY, positionZ) = bodyCreateMoving(ctx, capsuleGeom(ctx))
        messages.forAudience(sender).command.body.create.moving.capsule(
            count = count,
            positionX = positionX, positionY = positionY, positionZ = positionZ,
        ).sendTo(sender)
    }

    private fun bodyDestroyAll(ctx: CommandContext<C>) {
        // TODO
    }
}
