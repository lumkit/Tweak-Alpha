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

    // cmake POST_BUILD 会把 tweakd 拷到此处，以 libtweakd.so 打进 AAR
    sourceSets {
        getByName("main") {
            jniLibs.srcDir("src/main/jniLibs")
            assets.srcDir("src/main/assets")
        }
    }

    dependencies {
        implementation(libs.androidx.activity.compose)
        compileOnly(libs.libsu.core)
        compileOnly(libs.libsu.service)
        compileOnlyApi(fileTree("dir" to "libs", "include" to listOf("*.jar")))
    }
}
