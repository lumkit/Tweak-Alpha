import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
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
            implementation(projects.androidSharedNative)
            implementation(libs.shizuku.api)
            api(libs.shizuku.provider)
            implementation(projects.adlib)
            implementation(libs.coil.network.okhttp)
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
            implementation(libs.ktor.utils)
            // miuix
            implementation(libs.miuix.ui)
            implementation(libs.miuix.squircle)
            implementation(libs.miuix.icons)
            implementation(libs.miuix.preference)
            // data store
            implementation(libs.androidx.datastore.preferences)
            // coil3
            implementation(libs.coil.compose)
            implementation(libs.coil.network.cache.control)
            implementation(libs.coil.gif)
            implementation(libs.coil.svg)

            // liquid glass
//            implementation(libs.liquid.glass)
            implementation(projects.backdrop)
            implementation(libs.kyant.shapes)
            implementation(libs.androidx.documentfile)

            // room
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)

            // reorderable
            implementation(libs.reorderable)

        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)

    add("kspAndroid", libs.androidx.room.compiler)
}
