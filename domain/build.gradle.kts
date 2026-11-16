plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // The data layer is an implementation detail of the domain: the app sees providers, never services.
    implementation(project(":data"))
    implementation(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    // SpecCopyTests compares the copied vectors with the Spec folder beside this repository, so a change there runs the tests again.
    inputs.files(fileTree(rootProject.file("../Hearth/Spec"))).withPropertyName("spec").withPathSensitivity(PathSensitivity.RELATIVE)
}
