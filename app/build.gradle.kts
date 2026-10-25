plugins {
    id("com.android.application")
}

android {
    namespace = "com.codebridge.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.codebridge.app"
        minSdk = 21
        targetSdk = 34
        versionCode = 4
        versionName = "1.0.3"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keys/codebridge.keystore")
            storePassword = "codebridge2026"
            keyAlias = "codebridge"
            keyPassword = "codebridge2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
