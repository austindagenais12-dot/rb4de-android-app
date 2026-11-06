plugins {
    id("com.android.application")
}

android {
    namespace = "dev.rb4dx.standalone"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.rb4dx.standalone"
        minSdk = 31
        targetSdk = 35
        versionCode = 25
        versionName = "0.12.19-gpu-wait-backoff"
        ndk {
            abiFilters.add("arm64-v8a")
        }
        manifestPlaceholders["appLabel"] = "Rock Band 4 Deluxe"
    }

    // Upgrade the verified input-bridge test package in place, preserving its
    // game settings and data while leaving older package variants untouched.
    signingConfigs {
        create("rb4dxCachedDebug") {
            storeFile = rootProject.file(".ci-signing/rb4dx-test.keystore")
            storePassword = "android"
            keyAlias = "rb4dx-test"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".linuxvk.turnip.inputbridge"
            manifestPlaceholders["appLabel"] = "RB4DX Controller Input"
            if (rootProject.file(".ci-signing/rb4dx-test.keystore").isFile) {
                signingConfig = signingConfigs.getByName("rb4dxCachedDebug")
            }
        }
    }

    packaging {
        jniLibs {
            // Extract our glibc ELF loader to nativeLibraryDir; Android does
            // not allow executing binaries from writable app-data storage.
            useLegacyPackaging = true
            keepDebugSymbols.add("**/librb4dx_host_loader.so")
            keepDebugSymbols.add("**/librb4dx_glibc_fileprobe.so")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    ndkVersion = "26.3.11579264"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
