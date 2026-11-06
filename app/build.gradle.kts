plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dsh.brickbash"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dsh.brickbash"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            // 调试包直接可装
        }
        release {
            isMinifyEnabled = false
            // 先用 debug 签名，方便 sideload；上架前换成自己的 keystore
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// 这个游戏不依赖任何第三方库：连 AndroidX 都不用，构建快、体积小
dependencies {
}
