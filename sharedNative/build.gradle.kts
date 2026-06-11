plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "io.github.lumkit.tweak.sharednative"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        aidl = true
    }

    dependencies {
        compileOnly(libs.libsu.core)
        compileOnly(libs.libsu.service)
    }
}
