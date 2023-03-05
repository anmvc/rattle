plugins {
    id("kotlin-conventions")
}

dependencies {
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.kotlinxCoroutinesJdk8)
    implementation(libs.cloudCore)
    implementation(libs.configurateCore)
    implementation(libs.alexandriaCore)

    testImplementation(projects.ignacioJolt)
    testImplementation(libs.joltJava)
}
