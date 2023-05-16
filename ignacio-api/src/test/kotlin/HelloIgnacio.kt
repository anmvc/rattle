import io.github.aecsocket.ignacio.*
import io.github.aecsocket.ignacio.physx.PhysxEngine
import io.github.aecsocket.ignacio.rapier.RapierEngine
import java.util.logging.Logger
import kotlin.test.Test

class HelloIgnacio {
    @Test
    fun helloRapier() {
        runTest(RapierEngine(RapierEngine.Settings()))
    }

    private fun runTest(engine: PhysicsEngine) {
        // sets up a space - an independent structure holding simulation data
        val physics: PhysicsSpace = engine.createSpace(PhysicsSpace.Settings())

        // create a non-moving floor body at (0, 0, 0)

        // describes physical properties that can be applied to stuff, used in simulation
        val floorMat: PhysicsMaterial = engine.createMaterial(
            friction = 0.25,
            restitution = 0.75,
        )

        // geometry: descriptor for a volume in 3D space
        // to define a box, we provide the half-extents - the half-size of the box
        val floorGeom: Geometry = Box(Vec(100.0, 0.5, 100.0))

        // shape: baked form of a geometry, physics-ready
        val floorShape: Shape = engine.createShape(floorGeom)

        // collider: a shape with properties determining how it is collided with
        val floorColl: Collider = engine.createCollider(
            shape = floorShape,
            material = floorMat,
        )


        // create a moving ball body starting at (0, 5, 0)

        val ballBody = engine.createMovingBody(
            position = Iso(Vec(0.0, 5.0, 0.0)),
        )



        val ballMaterial = engine.createMaterial(
            friction = 0.5,
            restitution = 0.75,
        )

        val ballShape = engine.createShape(Sphere(0.5))

        val ballCollider = physics.addCollider(ballShape, ballMaterial)

        val ball: MovingBody<*, *> = physics.addMovingBody(
            // start at (0, 5, 0) units
            position = Iso(Vec(0.0, 5.0, 0.0)),
            // this body will only ever have a single collider attached to it
            volume = Volume.Mutable(ballCollider),
        )

        ball.write { access ->
            // apply an upwards velocity of (0, 1, 0) units/sec
            access.linearVelocity = Vec(0.0, 1.0, 0.0)
        }

        // simulate
        val dt = 1.0 / 60.0
        repeat(200) { step ->
            // start the update step, but we don't block and wait for it (yet)
            // this is in 2 separate steps, so you can start multiple updates for multiple spaces
            // at the same time, then wait for them to finish all at once
            physics.startStep(dt)
            // block the current thread and wait for the update to finish
            physics.finishStep()

            ball.read { access ->
                println("[$step] ball @ ${access.position.translation}")
            }
        }

        floorMat.destroy()
        physics.destroy()
        engine.destroy()
    }
}
