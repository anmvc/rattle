import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("kotlin-conventions")
    id("publishing-conventions")
    alias(libs.plugins.shadow)
    //alias(libs.plugins.paperweight.userdev)
    alias(libs.plugins.run.paper)
}

val minecraft: String = libs.versions.minecraft.get()

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
}

dependencies {
    //paperweight.foliaDevBundle("$minecraft-R0.1-SNAPSHOT")
    api("io.papermc.paper", "paper-api", "$minecraft-R0.1-SNAPSHOT")
    api(projects.rattleServer)
    api(libs.alexandria.paper)
}

tasks {
    assemble {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion(minecraft)
    }
}
