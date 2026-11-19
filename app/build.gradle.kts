import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Addresses and the application id are personal, so they come from local.properties, which git ignores.
val local = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun quoted(key: String): String = "\"" + local.getProperty(key, "").replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "app.hearth"
    compileSdk = 37

    defaultConfig {
        applicationId = local.getProperty("hearth.applicationId", "app.hearth.android")
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        buildConfigField("String", "OLLAMA_ADDRESS", quoted("hearth.ollamaAddress"))
        buildConfigField("String", "SEARCH_ADDRESS", quoted("hearth.searchAddress"))
        buildConfigField("String", "SPEECH_ADDRESS", quoted("hearth.speechAddress"))
        buildConfigField("String", "IMAGE_ADDRESS", quoted("hearth.imageAddress"))
        buildConfigField("String", "DEFAULT_MODEL", quoted("hearth.defaultModel"))
        // Only fills in the Sync page's address; sync itself stays off until it is turned on there.
        buildConfigField("String", "HUB_ADDRESS", quoted("hearth.hubAddress"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        // A presenter logs what went wrong; on the JVM, where there is no log to write to, that does nothing.
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

dependencies {
    // The app sees the domain only; services and DTOs stay behind it.
    implementation(project(":domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    // Unlocking with a fingerprint, face or PIN.
    implementation(libs.androidx.biometric)
    // The biometric library brings an old fragment library whose activity cannot ask for a permission or open a
    // picker the way Compose does: it refuses the request and the app stops. A current one is named here to replace it.
    implementation(libs.androidx.fragment)
    // Android can draw a PDF but not read its text; this reads it.
    implementation(libs.pdfbox.android)
    // A script run at the start of every frame of the canvas's page, not only the page itself.
    implementation(libs.androidx.webkit)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
