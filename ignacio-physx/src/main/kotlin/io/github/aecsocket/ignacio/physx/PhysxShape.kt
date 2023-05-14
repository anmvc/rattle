package io.github.aecsocket.ignacio.physx

import io.github.aecsocket.ignacio.DestroyFlag
import io.github.aecsocket.ignacio.Shape
import physx.geometry.PxGeometry

class PhysxShape internal constructor(
    val geom: PxGeometry
) : Shape {
    private val destroyed = DestroyFlag()

    override fun destroy() {
        destroyed()
        geom.destroy()
    }
}
