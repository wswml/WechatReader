plugins {
    id("com.android.application")
}

android {
    namespace = "com.nous.wechatreader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nous.wechatreader"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 不生成默认的 Application 类
    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    compileOnly("de.robv.android.xposed:api:82:sources")
    implementation("androidx.core:core:1.12.0")
}
