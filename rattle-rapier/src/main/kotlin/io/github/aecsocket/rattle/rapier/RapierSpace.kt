package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.alexandria.EventDispatch
import io.github.aecsocket.rattle.*
import io.github.aecsocket.rattle.ContactPair
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

interface RapierPhysicsNative {
    var space: RapierSpace?
}

class RapierSpace internal constructor(
    val engine: RapierEngine,
    settings: PhysicsSpace.Settings,
) : RapierNative(), PhysicsSpace {
    override val nativeType get() = "RapierSpace"

    private val destroyed = DestroyFlag()

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
            onCollision.dispatch(when (event) {
                is CollisionEvent.Started -> PhysicsSpace.OnCollision(
                    state = PhysicsSpace.OnCollision.State.STARTED,
                    pair = ContactPair(
                        colliderA = RapierColliderKey(event.coll1),
                        colliderB = RapierColliderKey(event.coll2),
                    )
                    colliderA = RapierColliderKey(event.coll1),
                    colliderB = RapierColliderKey(event.coll2),
                    // todo manifolds
                )
                is CollisionEvent.Stopped -> PhysicsSpace.OnCollision(
                    state = PhysicsSpace.OnCollision.State.STOPPED,
                    colliderA = RapierColliderKey(event.coll1),
                    colliderB = RapierColliderKey(event.coll2),
                    // todo manifolds
                )
            })
        }

        override fun handleContactForceEvent(
            dt: Double,
            bodies: RigidBodySet,
            colliders: ColliderSet,
            contactPair: ContactPair,
            totalForceMagnitude: Double
        ) {
            onContactForce.dispatch(PhysicsSpace.OnContactForce(
                dt = dt,
                totalMagnitude = totalForceMagnitude,
                colliderA = RapierColliderKey(contactPair.collider1),
                colliderB = RapierColliderKey(contactPair.collider2),
                // todo manifolds
            ))
            // todo
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

    val gravity = settings.gravity.toVector(arena)

    override val handle: Native
        get() = pipeline

    override var settings = settings
        set(value) {
            field = value
            gravity.copyFrom(settings.gravity)
        }

    fun createIntegrationParametersDesc() = IntegrationParametersDesc.create(arena).apply {
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

    override fun destroy() {
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
            get() = colliderSet.size().toInt()

        override fun read(key: ColliderKey): Collider? {
            key as RapierColliderKey
            return colliderSet.get(key.id)?.let { RapierCollider.Read(it, this@RapierSpace) }
        }

        override fun write(key: ColliderKey): Collider.Mut? {
            key as RapierColliderKey
            return colliderSet.getMut(key.id)?.let { RapierCollider.Write(it, this@RapierSpace) }
        }

        override fun all(): Collection<ColliderKey> {
            return colliderSet.all().map { RapierColliderKey(it.handle) }
        }

        override fun add(value: Collider.Own): ColliderKey {
            value as RapierCollider.Write
            assignSpace(value)
            return RapierColliderKey(colliderSet.insert(value.handle))
        }

        override fun remove(key: ColliderKey): Collider.Own? {
            key as RapierColliderKey
            return colliderSet.remove(
                key.id,
                islands,
                rigidBodySet,
                false,
            )?.let { RapierCollider.Write(it, space = null) }
        }

        override fun attach(coll: ColliderKey, to: RigidBodyKey) {
            coll as RapierColliderKey
            to as RapierRigidBodyKey
            colliderSet.setParent(coll.id, to.id, rigidBodySet)
        }

        override fun detach(coll: ColliderKey) {
            coll as RapierColliderKey
            colliderSet.setParent(coll.id, null, rigidBodySet)
        }
    }

    override val rigidBodies = object : PhysicsSpace.ActiveContainer<RigidBody, RigidBody.Mut, RigidBody.Own, RigidBodyKey> {
        override val count: Int
            get() = rigidBodySet.size().toInt()

        override val activeCount: Int
            get() = islands.activeDynamicBodies.size

        override fun read(key: RigidBodyKey): RigidBody? {
            key as RapierRigidBodyKey
            return rigidBodySet.get(key.id)?.let { RapierRigidBody.Read(it, this@RapierSpace) }
        }

        override fun write(key: RigidBodyKey): RigidBody.Mut? {
            key as RapierRigidBodyKey
            return rigidBodySet.getMut(key.id)?.let { RapierRigidBody.Write(it, this@RapierSpace) }
        }

        override fun all(): Collection<RigidBodyKey> {
            return rigidBodySet.all().map { RapierRigidBodyKey(it.handle) }
        }

        override fun active(): Collection<RigidBodyKey> {
            return islands.activeDynamicBodies.map { RapierRigidBodyKey(it) }
        }

        override fun add(value: RigidBody.Own): RigidBodyKey {
            value as RapierRigidBody.Write
            assignSpace(value)
            return RapierRigidBodyKey(rigidBodySet.insert(value.handle))
        }

        override fun remove(key: RigidBodyKey): RigidBody.Own? {
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
            get() = impulseJointSet.size().toInt()

        override fun read(key: ImpulseJointKey): ImpulseJoint? {
            key as RapierImpulseJointKey
            return impulseJointSet.get(key.id)?.let { RapierImpulseJoint.Read(it, this@RapierSpace) }
        }

        override fun write(key: ImpulseJointKey): ImpulseJoint.Mut? {
            key as RapierImpulseJointKey
            return impulseJointSet.getMut(key.id)?.let { RapierImpulseJoint.Write(it, this@RapierSpace) }
        }

        override fun all(): Collection<ImpulseJointKey> {
            return impulseJointSet.all().map { RapierImpulseJointKey(it.handle) }
        }

        override fun add(value: Joint.Own, bodyA: RigidBodyKey, bodyB: RigidBodyKey): ImpulseJointKey {
            value as RapierJoint.Write
            bodyA as RapierRigidBodyKey
            bodyB as RapierRigidBodyKey
            assignSpace(value)
            return RapierImpulseJointKey(impulseJointSet.insert(bodyA.id, bodyB.id, value.handle, false))
        }

        override fun remove(key: ImpulseJointKey): Joint.Own? {
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
            value as RapierJoint.Write
            bodyA as RapierRigidBodyKey
            bodyB as RapierRigidBodyKey
            assignSpace(value)
            multibodyJointSet.insert(bodyA.id, bodyB.id, value.handle, false)
        }

        override fun removeOn(bodyKey: RigidBodyKey) {
            bodyKey as RapierRigidBodyKey
            multibodyJointSet.removeJointsAttachedToRigidBody(bodyKey.id)
        }
    }

    override val query = object : PhysicsSpace.Query {
        override fun intersectBounds(
            bounds: Aabb,
            fn: (ColliderKey) -> QueryResult,
        ) {
            pushArena { arena ->
                queryPipeline.collidersWithAabbIntersectingAabb(
                    bounds.toRapier(arena),
                ) { collHandle ->
                    fn(RapierColliderKey(collHandle)).shouldContinue
                }
            }
        }

        override fun intersectPoint(
            point: Vec,
            filter: QueryFilter,
            fn: (ColliderKey) -> QueryResult,
        ) {
            pushArena { arena ->
                queryPipeline.intersectionsWithPoint(
                    rigidBodySet,
                    colliderSet,
                    point.toVector(arena),
                    filter.convert(arena, this@RapierSpace),
                ) { res ->
                    fn(RapierColliderKey(res)).shouldContinue
                }
            }
        }

        override fun intersectPoint(
            point: Vec,
            filter: QueryFilter,
        ): ColliderKey? {
            var coll: ColliderKey? = null
            intersectPoint(point, filter) {
                coll = it
                QueryResult.STOP
            }
            return coll
        }

        override fun intersectShape(
            shape: Shape,
            shapePos: Iso,
            filter: QueryFilter
        ): ColliderKey? {
            shape as RapierShape
            return pushArena { arena ->
                queryPipeline.intersectionWithShape(
                    arena,
                    rigidBodySet,
                    colliderSet,
                    shapePos.toIsometry(arena),
                    shape.handle,
                    filter.convert(arena, this@RapierSpace),
                )?.let { RapierColliderKey(it) }
            }
        }

        override fun intersectShape(
            shape: Shape,
            shapePos: Iso,
            filter: QueryFilter,
            fn: (ColliderKey) -> QueryResult,
        ) {
            shape as RapierShape
            pushArena { arena ->
                queryPipeline.intersectionsWithShape(
                    rigidBodySet,
                    colliderSet,
                    shapePos.toIsometry(arena),
                    shape.handle,
                    filter.convert(arena, this@RapierSpace),
                ) { res ->
                    fn(RapierColliderKey(res)).shouldContinue
                }
            }
        }

        override fun rayCast(
            ray: Ray,
            maxDistance: Real,
            settings: RayCastSettings,
            filter: QueryFilter
        ): RayCast.Simple? {
            return pushArena { arena ->
                queryPipeline.castRay(
                    arena,
                    rigidBodySet,
                    colliderSet,
                    ray.toRapier(arena),
                    maxDistance,
                    settings.isSolid,
                    filter.convert(arena, this@RapierSpace),
                )?.convert()
            }
        }

        override fun rayCastComplex(
            ray: Ray,
            maxDistance: Real,
            settings: RayCastSettings,
            filter: QueryFilter,
        ): RayCast.Complex? {
            return pushArena { arena ->
                queryPipeline.castRayAndGetNormal(
                    arena,
                    rigidBodySet,
                    colliderSet,
                    ray.toRapier(arena),
                    maxDistance,
                    settings.isSolid,
                    filter.convert(arena, this@RapierSpace),
                )?.convert()
            }
        }

        override fun rayCastComplex(
            ray: Ray,
            maxDistance: Real,
            settings: RayCastSettings,
            filter: QueryFilter,
            fn: (RayCast.Complex) -> QueryResult
        ) {
            pushArena { arena ->
                queryPipeline.intersectionWithRay(
                    rigidBodySet,
                    colliderSet,
                    ray.toRapier(arena),
                    maxDistance,
                    settings.isSolid,
                    filter.convert(arena, this@RapierSpace),
                ) { res ->
                    fn(res.convert()).shouldContinue
                }
            }
        }

        override fun shapeCast(
            shape: Shape,
            shapePos: Iso,
            shapeVel: Vec,
            maxDistance: Real,
            settings: ShapeCastSettings,
            filter: QueryFilter,
        ): ShapeCast? {
            shape as RapierShape
            return pushArena { arena ->
                queryPipeline.castShape(
                    arena,
                    rigidBodySet,
                    colliderSet,
                    shapePos.toIsometry(arena),
                    shapeVel.toVector(arena),
                    shape.handle,
                    maxDistance,
                    settings.stopAtPenetration,
                    filter.convert(arena, this@RapierSpace),
                )?.convert()
            }
        }

        override fun shapeCastNonLinear(
            shape: Shape,
            shapePos: Iso,
            shapeLocalCenter: Vec,
            shapeLinVel: Vec,
            shapeAngVel: Vec,
            timeStart: Real,
            timeEnd: Real,
            settings: ShapeCastSettings,
            filter: QueryFilter,
        ): ShapeCast? {
            shape as RapierShape
            return pushArena { arena ->
                val motion = NonlinearRigidMotion.of(arena).also {
                    it.start = shapePos.toIsometry(arena)
                    it.localCenter = shapeLocalCenter.toVector(arena)
                    it.linVel = shapeLinVel.toVector(arena)
                    it.angVel = shapeAngVel.toAngVector(arena)
                }
                queryPipeline.nonlinearCastShape(
                    arena,
                    rigidBodySet,
                    colliderSet,
                    motion,
                    shape.handle,
                    timeStart,
                    timeEnd,
                    settings.stopAtPenetration,
                    filter.convert(arena, this@RapierSpace)
                )?.convert()
            }
        }

        override fun projectPointSimple(
            point: Vec,
            settings: PointProjectSettings,
            filter: QueryFilter,
        ): PointProject.Simple? {
            return pushArena { arena ->
                queryPipeline.projectPoint(
                    arena,
                    rigidBodySet,
                    colliderSet,
                    point.toVector(arena),
                    settings.isSolid,
                    filter.convert(arena, this@RapierSpace)
                )?.convert()
            }
        }

        override fun projectPointComplex(
            point: Vec,
            settings: PointProjectSettings,
            filter: QueryFilter,
        ): PointProject.Complex? {
            return pushArena { arena ->
                queryPipeline.projectPointAndGetFeature(
                    arena,
                    rigidBodySet,
                    colliderSet,
                    point.toVector(arena),
                    // settings.isSolid, // TODO Rapier screwed this one up. Not us.
                    filter.convert(arena, this@RapierSpace),
                )?.convert()
            }
        }
    }
}
