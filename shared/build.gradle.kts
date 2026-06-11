import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {

    android {
        namespace = "io.github.lumkit.tweak.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        buildToolsVersion = "37.0.0"

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.libsu.core)
            implementation(libs.libsu.service)
            implementation(projects.sharedNative)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.graphics)
            implementation(libs.compose.material.ripple)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // navigation3
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel.navigation3)
            // kotlin serialization
            implementation(libs.kotlinx.serialization.json)
            // miuix
            implementation(libs.miuix.ui)
            implementation(libs.miuix.squircle)
            implementation(libs.miuix.icons)
            // data store
            implementation(libs.androidx.datastore.preferences)
            // coil3
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.coil.network.cache.control)
            implementation(libs.coil.gif)
            implementation(libs.coil.svg)

            // liquid glass
//            implementation(libs.liquid.glass)
            implementation(projects.backdrop)
            implementation(libs.kyant.shapes)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
