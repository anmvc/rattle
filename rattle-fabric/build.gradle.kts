plugins {
    id("kotlin-conventions")
    id("publishing-conventions")
    alias(libs.plugins.fabric.loom)
}

repositories {
    sonatype.ossSnapshots()
}

dependencies {
    minecraft("com.mojang", "minecraft", "1.20.1")
    mappings(loom.officialMojangMappings())
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)
    modImplementation(libs.fabric.language.kotlin)
    api(projects.rattleCommon)

    modApi(libs.alexandria.fabric)
    include(libs.alexandria.fabric)
}

loom {
    splitEnvironmentSourceSets()

    mods {
        create("rattle").apply {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        get("client").apply {
            vmArgs.addAll(listOf("--enable-preview", "--enable-native-access=ALL-UNNAMED"))
        }
    }
}
