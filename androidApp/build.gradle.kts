import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("tweak.android.application.flavors")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(projects.shared)

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "io.github.lumkit.tweak"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    signingConfigs {
        create("tweakRelease") {
            keyAlias =  "lumkit"
            keyPassword =  "0409.kaly"
            storeFile =  file("./sign/tweak-alpha")
            storePassword = "0409.kaly"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    defaultConfig {
        applicationId = "io.github.lumkit.tweak"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode =1000
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isJniDebuggable = false
            multiDexEnabled = false

            signingConfig = signingConfigs["tweakRelease"]

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "release-rules.pro"
            )
        }

        create("dict") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")

            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isJniDebuggable = false
            multiDexEnabled = false

            signingConfig = signingConfigs["tweakRelease"]

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "prod-rules.pro"
            )
        }

        debug {
            isMinifyEnabled = false

            signingConfig = signingConfigs["tweakRelease"]
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        aidl = true
        buildConfig = true
    }
}
