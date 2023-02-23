package io.github.aecsocket.ignacio.jolt

import io.github.aecsocket.ignacio.core.*
import io.github.aecsocket.ignacio.core.math.Ray
import io.github.aecsocket.ignacio.core.math.Transform
import io.github.aecsocket.ignacio.core.math.Vec3d
import jolt.core.TempAllocator
import jolt.physics.Activation
import jolt.physics.PhysicsSystem
import jolt.kotlin.*
import jolt.physics.body.MotionType
import jolt.physics.collision.ObjectLayerFilter
import jolt.physics.collision.RayCast3f
import jolt.physics.collision.broadphase.BroadPhaseCastResult
import jolt.physics.collision.broadphase.BroadPhaseLayerFilter
import jolt.physics.collision.broadphase.RayCastBodyCollector

class JtPhysicsSpace(
    private val engine: JoltEngine,
    val handle: PhysicsSystem,
    val tempAllocator: TempAllocator,
) : PhysicsSpace {
    val bodies = HashMap<BodyId, PhysicsBody>()
    override val numBodies get() = handle.numBodies
    override val numActiveBodies get() = handle.numActiveBodies

    fun destroy() {
        // todo destroy bodies
        handle.delete()
        tempAllocator.delete()
    }

    fun bodyOf(id: BodyId) = bodies[id]
        ?: throw IllegalStateException("ID $id does not have a corresponding Ignacio wrapper")

    override fun addStaticBody(geometry: Geometry, transform: Transform): StaticBody {
        val settings = BodyCreationSettingsDp(
            engine.shapeOf(geometry),
            transform.position.jolt(),
            transform.rotation.jolt(),
            MotionType.STATIC,
            OBJECT_LAYER_NON_MOVING
        )
        val body = BodyId(handle.bodyInterface.createBody(settings).id)
        handle.bodyInterface.addBody(body, Activation.DONT_ACTIVATE)
        return JtStaticBody(engine, this, body).also {
            bodies[body] = it
        }
    }

    override fun addDynamicBody(geometry: Geometry, transform: Transform, dynamics: BodyDynamics): DynamicBody {
        val settings = BodyCreationSettingsDp(
            engine.shapeOf(geometry),
            transform.position.jolt(),
            transform.rotation.jolt(),
            MotionType.DYNAMIC,
            OBJECT_LAYER_MOVING
        )
        val body = BodyId(handle.bodyInterface.createBody(settings).id)
        handle.bodyInterface.addBody(body, Activation.ofValue(dynamics.activate))
        return JtDynamicBody(engine, this, body).also {
            bodies[body] = it
        }
    }

    override fun removeBody(body: PhysicsBody) {
        body as JtPhysicsBody
        bodies.remove(body.id)
        handle.bodyInterface.removeBody(body.id)
    }

    override fun rayCastBodies(ray: Ray, distance: Float): Collection<PhysicsBody> {
        val hits = ArrayList<BodyId>()
        val collector = object : RayCastBodyCollector() {
            override fun addHit(result: BroadPhaseCastResult) {
                hits += BodyId(result.bodyId)
            }
        }
        handle.broadPhaseQuery.castRay(
            RayCast3f(ray.origin.joltSp(), (ray.direction * distance).jolt()),
            collector, BroadPhaseLayerFilter.passthrough(), ObjectLayerFilter.passthrough()
        )
        return hits.map { bodyOf(it) }
    }

    override fun bodiesNear(position: Vec3d, radius: Float): Set<PhysicsBody> {
        TODO("...")
    }

    override fun update(deltaTime: Float) {
        handle.update(
            deltaTime,
            engine.settings.spaces.collisionSteps,
            engine.settings.spaces.integrationSubSteps,
            tempAllocator,
            engine.jobSystem
        )
    }
}
