import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // No kotlin.android plugin: AGP 9 has built-in Kotlin support and rejects it.
    alias(libs.plugins.kotlin.compose)
}

/**
 * RevenueCat's public key, read from local.properties (gitignored) or the environment.
 *
 * Empty is a valid state, not a build failure. Anyone cloning this repo without credentials must
 * still get a working app — every coaching feature runs regardless, and billing simply reports
 * "not subscribed".
 */
val revenueCatKey: String = run {
    val local = rootProject.file("local.properties")
    val fromLocal = if (local.exists()) {
        Properties().apply { local.inputStream().use(::load) }.getProperty("REVENUECAT_KEY")
    } else {
        null
    }
    fromLocal ?: System.getenv("REVENUECAT_KEY") ?: ""
}

android {
    namespace = "com.spotter"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.spotter"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "REVENUECAT_KEY", "\"$revenueCatKey\"")

        ndk {
            // arm64 only. The pose model ships a native library per ABI, and every foldable this
            // app is built for is arm64 — carrying the others is tens of megabytes serving nobody.
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.window)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.camera.compose)
    implementation(libs.mlkit.pose.detection)
    implementation(libs.revenuecat.purchases)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.kotlinx.coroutines.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

/**
 * The reachability gate, carried forward from the two projects before this one.
 *
 * In the first it was written after six separate layers turned out to be built, named, sometimes
 * tested, and reached by no screen. In the second it caught a dead helper on day one and a real
 * state-machine bug on day two. It costs nothing to bring along and it has never yet been quiet
 * for a whole project.
 */
val checkReachable = tasks.register<Exec>("checkReachable") {
    group = "verification"
    description = "Fails if production code declares something no screen reaches."
    commandLine("python3", "${rootProject.projectDir}/tools/check_reachable.py")
}

tasks.named("check") { dependsOn(checkReachable) }
