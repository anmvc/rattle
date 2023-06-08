package io.github.aecsocket.rattle.rapier

import io.github.aecsocket.rattle.*
import rapier.data.ArenaKey

@JvmInline
value class RapierRigidBodyKey(val id: Long) : RigidBodyKey {
    override fun toString(): String = ArenaKey.asString(id)
}

sealed class RapierRigidBody(
    override val handle: rapier.dynamics.RigidBody,
    override var space: RapierSpace?,
) : RapierNative(), RapierPhysicsNative, RigidBody {
    override val type: RigidBodyType
        get() = handle.bodyType.convert()

    override val colliders: Collection<RapierColliderKey>
        get() = handle.colliders.map { RapierColliderKey(it) }

    override val position: Iso
        get() = pushArena { arena ->
            handle.getPosition(arena).toIso()
        }

    override val linearVelocity: Vec
        get() = pushArena { arena ->
            handle.getLinearVelocity(arena).toVec()
        }

    override val angularVelocity: Vec
        get() = pushArena { arena ->
            handle.getAngularVelocity(arena).toVec()
        }

    override val isCcdEnabled: Boolean
        get() = handle.isCcdEnabled

    override val isCcdActive: Boolean
        get() = handle.isCcdActive

    override val gravityScale: Real
        get() = handle.gravityScale

    override val linearDamping: Real
        get() = handle.linearDamping

    override val angularDamping: Real
        get() = handle.angularDamping

    override val isSleeping: Boolean
        get() = handle.isSleeping

    override val appliedForce: Vec
        get() = pushArena { arena ->
            handle.getUserForce(arena).toVec()
        }

    override val appliedTorque: Vec
        get() = pushArena { arena ->
            handle.getUserTorque(arena).toVec()
        }

    override fun kineticEnergy(): Real {
        return handle.kineticEnergy
    }

    class Read internal constructor(
        handle: rapier.dynamics.RigidBody,
        space: RapierSpace?,
    ) : RapierRigidBody(handle, space) {
        override val nativeType get() = "RapierRigidBody.Read"
    }

    class Write internal constructor(
        override val handle: rapier.dynamics.RigidBody.Mut,
        space: RapierSpace?,
    ) : RapierRigidBody(handle, space), RigidBody.Own {
        override val nativeType get() = "RapierRigidBody.Write"

        private val destroyed = DestroyFlag()

        override fun destroy() {
            destroyed()
            space?.let { space ->
                throw IllegalStateException("Attempting to remove $this while still attached to $space")
            }
            handle.drop()
        }

        override fun type(value: RigidBodyType): Write {
            handle.setBodyType(value.convert(), false)
            return this
        }

        override fun position(value: Iso): Write {
            pushArena { arena ->
                handle.setPosition(value.toIsometry(arena), false)
            }
            return this
        }

        override fun linearVelocity(value: Vec): Write {
            pushArena { arena ->
                handle.setLinearVelocity(value.toVector(arena), false)
            }
            return this
        }

        override fun angularVelocity(value: Vec): Write {
            pushArena { arena ->
                handle.setAngularVelocity(value.toAngVector(arena), false)
            }
            return this
        }

        override fun isCcdEnabled(value: Boolean): Write {
            handle.enableCcd(value)
            return this
        }

        override fun gravityScale(value: Real): Write {
            handle.setGravityScale(value, false)
            return this
        }

        override fun linearDamping(value: Real): Write {
            handle.linearDamping = value
            return this
        }

        override fun angularDamping(value: Real): Write {
            handle.angularDamping = value
            return this
        }

        override fun canSleep(value: Boolean): Write {
            // values taken from rigid_body_components.rs > impl RigidBodyActivation
            when (value) {
                false -> {
                    handle.activation.linearThreshold = -1.0
                    handle.activation.angularThreshold = -1.0
                }
                true -> {
                    handle.activation.linearThreshold = 0.4
                    handle.activation.angularThreshold = 0.5
                }
            }
            return this
        }

        override fun sleep() {
            handle.sleep()
        }

        override fun wakeUp(strong: Boolean) {
            handle.wakeUp(strong)
        }

        override fun applyForce(force: Vec) {
            pushArena { arena ->
                handle.addForce(force.toVector(arena), false)
            }
        }

        override fun applyForceAt(force: Vec, at: Vec) {
            pushArena { arena ->
                handle.addForceAtPoint(force.toVector(arena), at.toVector(arena), false)
            }
        }

        override fun applyImpulse(impulse: Vec) {
            pushArena { arena ->
                handle.applyImpulse(impulse.toVector(arena), false)
            }
        }

        override fun applyImpulseAt(impulse: Vec, at: Vec) {
            pushArena { arena ->
                handle.applyImpulseAtPoint(impulse.toVector(arena), at.toVector(arena), false)
            }
        }

        override fun applyTorque(torque: Vec) {
            pushArena { arena ->
                handle.addTorque(torque.toAngVector(arena), false)
            }
        }

        override fun applyTorqueImpulse(torqueImpulse: Vec) {
            pushArena { arena ->
                handle.applyTorqueImpulse(torqueImpulse.toAngVector(arena), false)
            }
        }

        override fun kinematicMoveTo(to: Iso) {
            pushArena { arena ->
                handle.setNextKinematicPosition(to.toIsometry(arena))
            }
        }
    }
}
