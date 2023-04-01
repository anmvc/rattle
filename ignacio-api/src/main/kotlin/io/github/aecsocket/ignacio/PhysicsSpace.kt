package io.github.aecsocket.ignacio

import org.spongepowered.configurate.objectmapping.ConfigSerializable

fun interface StepListener {
    fun onStep(deltaTime: Float)
}

interface PhysicsSpace : Destroyable {
    @ConfigSerializable
    data class Settings(
        val gravity: Vec3 = Vec3(0.0f, -9.81f, 0.0f),
    )

    var settings: Settings

    val bodies: Bodies
    interface Bodies {
        fun createStatic(descriptor: StaticBodyDescriptor, transform: Transform): PhysicsBody

        fun createMoving(descriptor: MovingBodyDescriptor, transform: Transform): PhysicsBody

        fun create(descriptor: BodyDescriptor, transform: Transform) = when (descriptor) {
            is StaticBodyDescriptor -> createStatic(descriptor, transform)
            is MovingBodyDescriptor -> createMoving(descriptor, transform)
        }

        fun destroy(body: PhysicsBody)

        fun destroyAll(bodies: Collection<PhysicsBody>)

        fun add(body: PhysicsBody)

        fun addStatic(descriptor: StaticBodyDescriptor, transform: Transform): PhysicsBody {
            return createStatic(descriptor, transform).also {
                add(it)
            }
        }

        fun addMovingBody(descriptor: MovingBodyDescriptor, transform: Transform): PhysicsBody {
            return createMoving(descriptor, transform).also {
                add(it)
            }
        }

        fun addAll(bodies: Collection<PhysicsBody>)

        fun remove(body: PhysicsBody)

        fun removeAll(bodies: Collection<PhysicsBody>)
    }

    data class RayCast(
        val body: PhysicsBody,
        val hitFraction: Float,
    )

    val broadQuery: BroadQuery
    interface BroadQuery {
        fun rayCastBody(ray: RRay, distance: Float, layerFilter: LayerFilter): RayCast?

        fun rayCastBodies(ray: RRay, distance: Float, layerFilter: LayerFilter): Collection<RayCast>

        fun contactSphere(position: RVec3, radius: Float, layerFilter: LayerFilter): Collection<PhysicsBody>
    }

    val narrowQuery: NarrowQuery
    interface NarrowQuery {
        fun rayCastBody(ray: RRay, distance: Float, layerFilter: LayerFilter, bodyFilter: BodyFilter): RayCast?

        fun rayCastBodies(ray: RRay, distance: Float, layerFilter: LayerFilter, bodyFilter: BodyFilter, shapeFilter: ShapeFilter): Collection<RayCast>
    }

    fun onStep(listener: StepListener)

    fun removeStepListener(listener: StepListener)

    fun update(deltaTime: Float)
}
