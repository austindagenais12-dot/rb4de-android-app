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
        versionCode = 7
        versionName = "0.7.0-direct-native-game-access"
        ndk {
            abiFilters.add("arm64-v8a")
        }
        manifestPlaceholders["appLabel"] = "Rock Band 4 Deluxe"
    }

    // The diagnostic APK installs next to the already-working importer,
    // preserving the user's 598 MiB of separately imported Deluxe assets.
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".bridge"
            manifestPlaceholders["appLabel"] = "RB4DX Native Storage Test"
        }
    }

    packaging {
        jniLibs {
            // Extract our glibc ELF loader to nativeLibraryDir; Android does
            // not allow executing binaries from writable app-data storage.
            useLegacyPackaging = true
            keepDebugSymbols.add("**/librb4dx_host_loader.so")
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
