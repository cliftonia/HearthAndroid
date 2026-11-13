import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Addresses and the application id are personal, so they come from local.properties, which git ignores.
val local = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun quoted(key: String): String = "\"" + local.getProperty(key, "").replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "app.hearth"
    compileSdk = 34

    defaultConfig {
        applicationId = local.getProperty("hearth.applicationId", "app.hearth.android")
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
        buildConfigField("String", "OLLAMA_ADDRESS", quoted("hearth.ollamaAddress"))
        buildConfigField("String", "SEARCH_ADDRESS", quoted("hearth.searchAddress"))
        buildConfigField("String", "SPEECH_ADDRESS", quoted("hearth.speechAddress"))
        buildConfigField("String", "IMAGE_ADDRESS", quoted("hearth.imageAddress"))
        buildConfigField("String", "DEFAULT_MODEL", quoted("hearth.defaultModel"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    // Unlocking with a fingerprint, face or PIN.
    implementation(libs.androidx.biometric)
    // Android can draw a PDF but not read its text; this reads it.
    implementation(libs.pdfbox.android)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
