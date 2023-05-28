package io.github.aecsocket.rattle.paper

import io.github.aecsocket.rattle.EntityStrategy
import io.github.aecsocket.rattle.PhysicsSpace
import io.github.aecsocket.rattle.TerrainStrategy
import io.github.aecsocket.rattle.WorldPhysics
import org.bukkit.World

class PaperWorldPhysics(
    val world: World,
    override val physics: PhysicsSpace,
    override val terrain: TerrainStrategy,
    override val entities: EntityStrategy,
) : WorldPhysics
