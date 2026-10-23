import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "si.timrekelj.thegrapevine"
    compileSdk = 37

    defaultConfig {
        applicationId = "si.timrekelj.thegrapevine"
        minSdk = 26
        targetSdk = 37
        // Bumped by CI from the run number; local dev builds stay on 1.
        versionCode = (System.getenv("ANDROID_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("ANDROID_VERSION_NAME") ?: "0.1.0"
    }

    signingConfigs {
        // Release signing comes from CI secrets (or local env vars); the keystore
        // itself is never committed. Debug builds stay unsigned-by-config.
        val storeFilePath = System.getenv("ANDROID_KEYSTORE_PATH")
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("ANDROID_STORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    // Same split as the iOS app: Debug talks to `supabase start` on the host via
    // `adb reverse` (API 37 blocks apps from LAN addresses like 10.0.2.2),
    // Release to the hosted project.
    buildTypes {
        debug {
            buildConfigField("String", "SUPABASE_URL", "\"http://localhost:55321\"")
            buildConfigField(
                "String",
                "SUPABASE_PUBLISHABLE_KEY",
                "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0\"",
            )
            buildConfigField("String", "WEB_URL", "\"http://localhost:3000\"")
        }
        release {
            buildConfigField("String", "SUPABASE_URL", "\"https://ivjaenrmhsqnsvzemoez.supabase.co\"")
            buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"sb_publishable_oT-H6EAfuoNp8dcVdTHJow_l1-Skd1C\"")
            buildConfigField("String", "WEB_URL", "\"https://thegrapevine.si\"")
            // R8: kotlinx.serialization, OkHttp, OneSignal and PostHog ship their own keep rules.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The app is written in English only: keep libraries' translations (Material, OneSignal, Play
    // services) out of the APK rather than shipping dozens of languages for a few strings each.
    androidResources {
        localeFilters += "en"
    }

    packaging {
        // Build metadata nothing reads at runtime: licence copies, library version stamps, GraalVM
        // configs, and the Kotlin builtins only kotlin-reflect (not a dependency) would load.
        resources.excludes += listOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/androidx/**",
            "/META-INF/*.version",
            "/META-INF/native-image/**",
            "/META-INF/version-control-info.textproto",
            "/kotlin/**.kotlin_builtins",
            "DebugProbesKt.bin",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.onesignal.core)
    implementation(libs.onesignal.notifications)
    implementation(libs.onesignal.iam)
    implementation(libs.posthog)

    testImplementation(libs.junit)
}
