import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose)
    alias(libs.plugins.serialization)
}

android {
    signingConfigs {
        create("defaultSignature") {
            storeFile = file(project.findProperty("StoreFile") ?: "testkey.jks")
            storePassword = (project.findProperty("StorePassword") as String?) ?: "testkey"
            keyPassword = (project.findProperty("KeyPassword") as String?) ?: "testkey"
            keyAlias = (project.findProperty("KeyAlias") as String?) ?: "testkey"
        }
    }
    namespace = "lesser.evil"
    compileSdk = 36

    lint.checkReleaseBuilds = false
    lint.disable += "All"

    defaultConfig {
        applicationId = "lesser.evil"
        minSdk = 21
        targetSdk = 36
        versionCode = 42
        versionName = "7.3"
        multiDexEnabled = false
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("defaultSignature")
        }
        debug {
            signingConfig = signingConfigs.getByName("defaultSignature")
        }
        create("fastDebug") {
            initWith(getByName("debug"))
            isDebuggable = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        aidl = true
    }
    androidResources {
        generateLocaleConfig = true
    }
    dependenciesInfo {
        includeInApk = false
    }
    composeCompiler {
        includeSourceInformation = false
        includeTraceMarkers = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
    sourceSets {
        all {
            languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
        }
    }
}

gradle.taskGraph.whenReady {
    // Building doesn't wait for the tests, but asking for them still runs them
    val testsAsked = gradle.startParameter.taskNames.any { it.contains("test", ignoreCase = true) }
    if (!testsAsked) project.tasks.findByPath(":app:test")?.enabled = false
    project.tasks.findByPath(":app:lint")?.enabled = false
    project.tasks.findByPath(":app:lintAnalyzeDebug")?.enabled = false
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.accompanist.drawablepainter)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.material.icons.core)
    implementation(libs.shizuku.provider)
    implementation(libs.shizuku.api)
    implementation(libs.dhizuku.api)
    implementation(libs.dhizuku.server.api)
    implementation(libs.androidx.fragment)
    implementation(libs.hiddenApiBypass)
    implementation(libs.libsu)
    implementation(libs.reoderable)
    implementation(libs.serialization)
    implementation(libs.bouncycastle)
    implementation(kotlin("reflect"))
    testImplementation(libs.junit)
}