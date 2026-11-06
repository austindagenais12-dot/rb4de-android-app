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
        versionCode = 21
        versionName = "0.12.15-controller-input"
        ndk {
            abiFilters.add("arm64-v8a")
        }
        manifestPlaceholders["appLabel"] = "Rock Band 4 Deluxe"
    }

    // Controller-input v0.12.15 stays independent of the last verified UI APK.
    // It cannot overwrite previous app installs or their app-private data.
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
