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
        versionCode = 4
        versionName = "0.4.0-loader-diagnostics"
        manifestPlaceholders["appLabel"] = "Rock Band 4 Deluxe"
    }

    // The diagnostic APK installs next to the already-working importer,
    // preserving the user's 598 MiB of separately imported Deluxe assets.
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".probe2"
            manifestPlaceholders["appLabel"] = "RB4DX Loader Diagnostics"
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
}
