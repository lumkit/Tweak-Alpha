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

    buildTypes {
        release {
            externalNativeBuild {
                cmake {
                    cppFlags += listOf(
                        "-O2",
                        "-fvisibility=hidden",
                        "-fvisibility-inlines-hidden",
                        "-ffunction-sections",
                        "-fdata-sections",
                        "-flto",
                    )
                    cFlags += listOf(
                        "-O2",
                        "-fvisibility=hidden",
                        "-ffunction-sections",
                        "-fdata-sections",
                        "-flto",
                    )
                    arguments += listOf(
                        "-DCMAKE_BUILD_TYPE=Release",
                    )
                }
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

    // cmake 产出 libtweak_starter.so；tweakd 已移除（C2）
    sourceSets {
        getByName("main") {
            assets.srcDir("src/main/assets")
        }
    }

    dependencies {
        implementation(libs.androidx.activity.compose)
        compileOnly(libs.libsu.core)
        compileOnly(libs.libsu.service)
        // Hidden API stubs：仅本模块编译期可见，禁止泄漏到 App R8 classpath
        compileOnly(fileTree("dir" to "libs", "include" to listOf("*.jar")))
    }
}
