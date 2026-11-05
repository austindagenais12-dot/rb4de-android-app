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
        versionCode = 2
        versionName = "0.2.0-runtime-stage"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
