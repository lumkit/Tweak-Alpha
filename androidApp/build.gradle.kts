import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("tweak.android.application.flavors")
}

val tweakReleaseKeyAlias = System.getenv("TWEAK_RELEASE_KEY_ALIAS").orEmpty()
val tweakReleaseKeyPassword = System.getenv("TWEAK_RELEASE_KEY_PASSWORD").orEmpty()
val tweakReleaseStorePassword = System.getenv("TWEAK_RELEASE_STORE_PASSWORD").orEmpty()

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
            keyAlias = tweakReleaseKeyAlias
            keyPassword = tweakReleaseKeyPassword
            storeFile = file("./sign/tweak-alpha")
            storePassword = tweakReleaseStorePassword
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
        versionCode = 1028
        versionName = "1.0.28"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // 解压到 nativeLibraryDir，便于 Root/Shizuku exec libtweak_starter.so
        jniLibs {
            useLegacyPackaging = true
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
