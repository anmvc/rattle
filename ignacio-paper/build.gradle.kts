plugins {
    id("kotlin-conventions")
    id("io.papermc.paperweight.userdev")
    id("com.github.johnrengelman.shadow")
    id("xyz.jpenilla.run-paper")
}

val minecraft = libs.versions.minecraft.get()

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
}

dependencies {
    implementation(projects.ignacioCore)
    implementation(projects.ignacioJolt)
    implementation(projects.ignacioPhysx)
    implementation(projects.ignacioBullet)
    paperweight.paperDevBundle("$minecraft-R0.1-SNAPSHOT")
    implementation(libs.adventureSerializerConfigurate)
    implementation(libs.cloudCore)
    implementation(libs.cloudPaper)
    implementation(libs.cloudMinecraftExtras)
    implementation(libs.configurateCore)
    implementation(libs.configurateExtraKotlin)
    implementation(libs.configurateYaml)
    implementation(libs.alexandriaCore)
    implementation(libs.alexandriaPaper)
    implementation(libs.glossaCore)
    implementation(libs.glossaConfigurate)
    implementation(libs.packetEventsSpigot)
}

tasks {
    assemble {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion(minecraft)
    }
}