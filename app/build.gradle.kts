plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sunrocket.quotewallpaper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sunrocket.quotewallpaper"
        minSdk = 26
        targetSdk = 34
        // GitHub Actions가 빌드할 때마다 번호를 올려줌
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"
    }

    // 매번 같은 키로 서명해야 새 버전을 덮어서 설치할 수 있음
    signingConfigs {
        create("shared") {
            storeFile = file("shared.keystore")
            storePassword = "android"
            keyAlias = "quotewallpaper"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
