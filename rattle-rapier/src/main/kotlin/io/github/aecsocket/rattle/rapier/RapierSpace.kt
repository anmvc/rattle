package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.alexandria.EventDispatch
import io.github.aecsocket.klam.*
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.QueryFilter
import io.github.aecsocket.rattle.ShapeCast
import rapier.Native
import rapier.dynamics.CCDSolver
import rapier.dynamics.IntegrationParametersDesc
import rapier.dynamics.IslandManager
import rapier.dynamics.RigidBodySet
import rapier.dynamics.joint.impulse.ImpulseJointSet
import rapier.dynamics.joint.multibody.MultibodyJointSet
import rapier.geometry.BroadPhase
import rapier.geometry.ColliderSet
import rapier.geometry.NarrowPhase
import rapier.pipeline.*
import java.util.concurrent.locks.ReentrantLock

interface RapierPhysicsNative {
    var space: RapierSpace?
}

class RapierSpace internal constructor(
    val engine: RapierEngine,
    override var settings: PhysicsSpace.Settings,
) : RapierNative(), PhysicsSpace {
    override val nativeType get() = "RapierSpace"

    private val destroyed = DestroyFlag()

    override var lock: ReentrantLock? = null

    val arena: Arena = Arena.openShared()
    val pipeline = PhysicsPipeline.create()
    val islands = IslandManager.create()
    val broadPhase = BroadPhase.create()
    val narrowPhase = NarrowPhase.create()
    val rigidBodySet = RigidBodySet.create()
    val colliderSet = ColliderSet.create()
    val impulseJointSet = ImpulseJointSet.create()
    val multibodyJointSet = MultibodyJointSet.create()
    val ccdSolver = CCDSolver.create()
    val queryPipeline = QueryPipeline.create()

    override val onCollision = EventDispatch<PhysicsSpace.OnCollision>()
    override val onContactForce = EventDispatch<PhysicsSpace.OnContactForce>()

    val events = EventHandler.of(arena, object : EventHandler.Fn {
        override fun handleCollisionEvent(
            bodies: RigidBodySet,
            colliders: ColliderSet,
            event: CollisionEvent,
            contactPair: rapier.pipeline.ContactPair?,
        ) {
            /*
                /// * `contact_pair` - The current state of contacts between the two colliders. This is set ot `None`
                ///                    if at least one of the collider is a sensor (in which case no contact information
                ///                    is ever computed).
             */
            onCollision.dispatch(when (event) {
                is CollisionEvent.Started -> PhysicsSpace.OnCollision(
                    state = PhysicsSpace.OnCollision.State.STARTED,
                    colliderA = RapierColliderKey(event.coll1),
                    colliderB = RapierColliderKey(event.coll2),
                    manifolds = emptyList(), // todo
                )
                is CollisionEvent.Stopped -> PhysicsSpace.OnCollision(
                    state = PhysicsSpace.OnCollision.State.STOPPED,
                    colliderA = RapierColliderKey(event.coll1),
                    colliderB = RapierColliderKey(event.coll2),
                    manifolds = emptyList(), // todo
                )
            })
        }

        override fun handleContactForceEvent(
            dt: Double,
            bodies: RigidBodySet,
            colliders: ColliderSet,
            contactPair: rapier.pipeline.ContactPair,
            totalForceMagnitude: Double
        ) {
            onContactForce.dispatch(PhysicsSpace.OnContactForce(
                dt = dt,
                totalMagnitude = totalForceMagnitude,
                colliderA = RapierColliderKey(contactPair.collider1),
                colliderB = RapierColliderKey(contactPair.collider2),
                manifolds = emptyList(), // TODO
            ))
        }
    })

    val hooks = PhysicsHooks.of(arena, object : PhysicsHooks.Fn {
        override fun filterContactPair(context: PairFilterContext): Int {
            // todo
            return SolverFlags.COMPUTE_IMPULSES
        }

        override fun filterIntersectionPair(context: PairFilterContext): Boolean {
            // todo
            return true
        }

        override fun modifySolverContacts(context: ContactModificationContext) {
            // todo
        }
    })

    override val handle: Native
        get() = pipeline

    internal fun createIntegrationParametersDesc() = IntegrationParametersDesc.create(arena).apply {
        erp = engine.settings.integration.erp
        dampingRatio = engine.settings.integration.dampingRatio
        jointErp = engine.settings.integration.jointErp
        jointDampingRatio = engine.settings.integration.jointDampingRatio
        allowedLinearError = engine.settings.integration.allowedLinearError
        maxPenetrationCorrection = engine.settings.integration.maxPenetrationCorrection
        predictionDistance = engine.settings.integration.predictionDistance
        maxVelocityIterations = engine.settings.integration.maxVelocityIterations
        maxVelocityFrictionIterations = engine.settings.integration.maxVelocityFrictionIterations
        maxStabilizationIterations = engine.settings.integration.maxStabilizationIterations
        interleaveRestitutionAndFrictionResolution = engine.settings.integration.interleaveRestitutionAndFrictionResolution
        minIslandSize = engine.settings.integration.minIslandSize
        maxCcdSubsteps = engine.settings.integration.maxCcdSubsteps
    }

    private fun checkLock() {
        val lock = lock ?: return
        if (!lock.isHeldByCurrentThread)
            throw IllegalStateException("${Thread.currentThread().name}: Attempting to read/write physics space while not locked by this thread")
    }

    override fun destroy() {
        checkLock()
        destroyed()

        pipeline.drop()
        islands.drop()
        broadPhase.drop()
        narrowPhase.drop()
        rigidBodySet.drop()
        colliderSet.drop()
        impulseJointSet.drop()
        multibodyJointSet.drop()
        ccdSolver.drop()
        queryPipeline.drop()

        arena.close()
    }

    private fun assignSpace(obj: RapierPhysicsNative) {
        obj.space?.let { existing ->
            throw IllegalStateException("$obj is attempting to be added to $this but is already in $existing")
        }
        obj.space = this
    }

    override val colliders = object : PhysicsSpace.ColliderContainer {
        override val count: Int
            get() {
                checkLock()
                return colliderSet.size().toInt()
            }

        override fun read(key: ColliderKey): Collider? {
            checkLock()
            key as RapierColliderKey
            return colliderSet.get(key.id)?.let { RapierCollider.Read(it, this@RapierSpace) }
        }

        override fun write(key: ColliderKey): Collider.Mut? {
            checkLock()
            key as RapierColliderKey
            return colliderSet.getMut(key.id)?.let { RapierCollider.Write(it, this@RapierSpace) }
        }

        override fun all(): Collection<ColliderKey> {
            checkLock()
            return colliderSet.all().map { RapierColliderKey(it.handle) }
        }

        override fun add(value: Collider.Own): ColliderKey {
            checkLock()
            value as RapierCollider.Write
            assignSpace(value)
            return RapierColliderKey(colliderSet.insert(value.handle))
        }

        override fun remove(key: ColliderKey): Collider.Own? {
            checkLock()
            key as RapierColliderKey
            return colliderSet.remove(
                key.id,
                islands,
                rigidBodySet,
                false,
            )?.let { RapierCollider.Write(it, space = null) }
        }

        override fun attach(coll: ColliderKey, to: RigidBodyKey) {
            checkLock()
            coll as RapierColliderKey
            to as RapierRigidBodyKey
            colliderSet.setParent(coll.id, to.id, rigidBodySet)
        }

        override fun detach(coll: ColliderKey) {
            checkLock()
            coll as RapierColliderKey
            colliderSet.setParent(coll.id, null, rigidBodySet)
        }
    }

    override val rigidBodies = object : PhysicsSpace.ActiveContainer<RigidBody, RigidBody.Mut, RigidBody.Own, RigidBodyKey> {
        override val count: Int
            get() {
                checkLock()
                return rigidBodySet.size().toInt()
            }

        override val activeCount: Int
            get() {
                checkLock()
                return islands.activeDynamicBodies.size
            }

        override fun read(key: RigidBodyKey): RigidBody? {
            checkLock()
            key as RapierRigidBodyKey
            return rigidBodySet.get(key.id)?.let { RapierRigidBody.Read(it, this@RapierSpace) }
        }

        override fun write(key: RigidBodyKey): RigidBody.Mut? {
            checkLock()
            key as RapierRigidBodyKey
            return rigidBodySet.getMut(key.id)?.let { RapierRigidBody.Write(it, this@RapierSpace) }
        }

        override fun all(): Collection<RigidBodyKey> {
            checkLock()
            return rigidBodySet.all().map { RapierRigidBodyKey(it.handle) }
        }

        override fun active(): Collection<RigidBodyKey> {
            checkLock()
            return islands.activeDynamicBodies.map { RapierRigidBodyKey(it) }
        }

        override fun add(value: RigidBody.Own): RigidBodyKey {
            checkLock()
            value as RapierRigidBody.Write
            assignSpace(value)
            return RapierRigidBodyKey(rigidBodySet.insert(value.handle))
        }

        override fun remove(key: RigidBodyKey): RigidBody.Own? {
            checkLock()
            key as RapierRigidBodyKey
            return rigidBodySet.remove(
                key.id,
                islands,
                colliderSet,
                impulseJointSet,
                multibodyJointSet,
                false,
            )?.let { RapierRigidBody.Write(it, space = null) }
        }
    }

    override val impulseJoints = object : PhysicsSpace.ImpulseJointContainer {
        override val count: Int
            get() {
                checkLock()
                return impulseJointSet.size().toInt()
            }

        override fun read(key: ImpulseJointKey): ImpulseJoint? {
            checkLock()
            key as RapierImpulseJointKey
            return impulseJointSet.get(key.id)?.let { RapierImpulseJoint.Read(it, this@RapierSpace) }
        }

        override fun write(key: ImpulseJointKey): ImpulseJoint.Mut? {
            checkLock()
            key as RapierImpulseJointKey
            return impulseJointSet.getMut(key.id)?.let { RapierImpulseJoint.Write(it, this@RapierSpace) }
        }

        override fun all(): Collection<ImpulseJointKey> {
            checkLock()
            return impulseJointSet.all().map { RapierImpulseJointKey(it.handle) }
        }

        override fun add(value: Joint.Own, bodyA: RigidBodyKey, bodyB: RigidBodyKey): ImpulseJointKey {
            checkLock()
            value as RapierJoint.Write
            bodyA as RapierRigidBodyKey
            bodyB as RapierRigidBodyKey
            assignSpace(value)
            return RapierImpulseJointKey(impulseJointSet.insert(bodyA.id, bodyB.id, value.handle, false))
        }

        override fun remove(key: ImpulseJointKey): Joint.Own? {
            checkLock()
            key as RapierImpulseJointKey
            return impulseJointSet.remove(
                key.id,
                false,
            )?.let {
                // `.remove` returns an ImpulseJoint.Mut, which contains some extra data relating to that joint
                // we only want to return the `GenericJoint`, so we use a little hack and have `.retainData()`,
                // a native function designed specifically for our use
                RapierJoint.Write(it.retainData(), space = null)
            }
        }
    }

    override val multibodyJoints = object : PhysicsSpace.MultibodyJointContainer {
        override fun add(value: Joint.Own, bodyA: RigidBodyKey, bodyB: RigidBodyKey) {
            checkLock()
            value as RapierJoint.Write
            bodyA as RapierRigidBodyKey
            bodyB as RapierRigidBodyKey
            assignSpace(value)
            multibodyJointSet.insert(bodyA.id, bodyB.id, value.handle, false)
        }

        override fun removeOn(bodyKey: RigidBodyKey) {
            checkLock()
            bodyKey as RapierRigidBodyKey
            multibodyJointSet.removeJointsAttachedToRigidBody(bodyKey.id)
        }
    }

    override val query = object : PhysicsSpace.Query {
        override fun intersectBounds(
            bounds: DAabb3,
            fn: (ColliderKey) -> QueryResult,
        ) {
            checkLock()
            queryPipeline.collidersWithAabbIntersectingAabb(
                bounds.toRapier(),
            ) { collHandle ->
                fn(RapierColliderKey(collHandle)).shouldContinue
            }
        }

        override fun intersectPoint(
            point: DVec3,
            filter: QueryFilter,
            fn: (ColliderKey) -> QueryResult,
        ) {
            checkLock()
            queryPipeline.intersectionsWithPoint(
                rigidBodySet,
                colliderSet,
                point.toVector(),
                filter.toRapier(arena, this@RapierSpace),
            ) { res ->
                fn(RapierColliderKey(res)).shouldContinue
            }
        }

        override fun intersectPoint(
            point: DVec3,
            filter: QueryFilter,
        ): ColliderKey? {
            checkLock()
            var coll: ColliderKey? = null
            intersectPoint(point, filter) {
                coll = it
                QueryResult.STOP
            }
            return coll
        }

        override fun intersectShape(
            shape: Shape,
            shapePos: DIso3,
            filter: QueryFilter
        ): ColliderKey? {
            checkLock()
            shape as RapierShape
            return queryPipeline.intersectionWithShape(
                arena,
                rigidBodySet,
                colliderSet,
                shapePos.toIsometry(),
                shape.handle,
                filter.toRapier(arena, this@RapierSpace),
            )?.let { RapierColliderKey(it) }
        }

        override fun intersectShape(
            shape: Shape,
            shapePos: DIso3,
            filter: QueryFilter,
            fn: (ColliderKey) -> QueryResult,
        ) {
            checkLock()
            shape as RapierShape
            return queryPipeline.intersectionsWithShape(
                rigidBodySet,
                colliderSet,
                shapePos.toIsometry(),
                shape.handle,
                filter.toRapier(arena, this@RapierSpace),
            ) { res ->
                fn(RapierColliderKey(res)).shouldContinue
            }
        }

        override fun rayCast(
            ray: DRay3,
            maxDistance: Double,
            settings: RayCastSettings,
            filter: QueryFilter
        ): RayCast.Simple? {
            checkLock()
            return queryPipeline.castRay(
                rigidBodySet,
                colliderSet,
                ray.toRapier(),
                maxDistance,
                settings.isSolid,
                filter.toRapier(arena, this@RapierSpace),
            )?.toRattle()
        }

        override fun rayCastComplex(
            ray: DRay3,
            maxDistance: Double,
            settings: RayCastSettings,
            filter: QueryFilter,
        ): RayCast.Complex? {
            checkLock()
            return queryPipeline.castRayAndGetNormal(
                rigidBodySet,
                colliderSet,
                ray.toRapier(),
                maxDistance,
                settings.isSolid,
                filter.toRapier(arena, this@RapierSpace),
            )?.toRattle()
        }

        override fun rayCastComplex(
            ray: DRay3,
            maxDistance: Double,
            settings: RayCastSettings,
            filter: QueryFilter,
            fn: (RayCast.Complex) -> QueryResult
        ) {
            checkLock()
            queryPipeline.intersectionWithRay(
                rigidBodySet,
                colliderSet,
                ray.toRapier(),
                maxDistance,
                settings.isSolid,
                filter.toRapier(arena, this@RapierSpace),
            ) { res ->
                fn(res.toRattle()).shouldContinue
            }
        }

        override fun shapeCast(
            shape: Shape,
            shapePos: DIso3,
            shapeVel: DVec3,
            maxDistance: Double,
            settings: ShapeCastSettings,
            filter: QueryFilter,
        ): ShapeCast? {
            checkLock()
            shape as RapierShape
            return queryPipeline.castShape(
                rigidBodySet,
                colliderSet,
                shapePos.toIsometry(),
                shapeVel.toVector(),
                shape.handle,
                maxDistance,
                settings.stopAtPenetration,
                filter.toRapier(arena, this@RapierSpace),
            )?.toRattle()
        }

        override fun shapeCastNonLinear(
            shape: Shape,
            shapePos: DIso3,
            shapeLocalCenter: DVec3,
            shapeLinVel: DVec3,
            shapeAngVel: DVec3,
            timeStart: Double,
            timeEnd: Double,
            settings: ShapeCastSettings,
            filter: QueryFilter,
        ): ShapeCast? {
            checkLock()
            shape as RapierShape
            val motion = NonlinearRigidMotion(
                shapePos.toIsometry(),
                shapeLocalCenter.toVector(),
                shapeLinVel.toVector(),
                shapeAngVel.toAngVector()
            )
            return queryPipeline.nonlinearCastShape(
                rigidBodySet,
                colliderSet,
                motion,
                shape.handle,
                timeStart,
                timeEnd,
                settings.stopAtPenetration,
                filter.toRapier(arena, this@RapierSpace)
            )?.toRattle()
        }

        override fun projectPointSimple(
            point: DVec3,
            settings: PointProjectSettings,
            filter: QueryFilter,
        ): PointProject.Simple? {
            checkLock()
            return queryPipeline.projectPoint(
                rigidBodySet,
                colliderSet,
                point.toVector(),
                settings.isSolid,
                filter.toRapier(arena, this@RapierSpace)
            )?.toRattle()
        }

        override fun projectPointComplex(
            point: DVec3,
            settings: PointProjectSettings,
            filter: QueryFilter,
        ): PointProject.Complex? {
            checkLock()
            return queryPipeline.projectPointAndGetFeature(
                rigidBodySet,
                colliderSet,
                point.toVector(),
                // settings.isSolid, // TODO Rapier screwed this one up. Not us.
                filter.toRapier(arena, this@RapierSpace),
            )?.toRattle()
        }
    }
}
