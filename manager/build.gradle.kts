plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mahroch.manager"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mahroch.manager"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
}

kotlin {
    jvmToolchain(17)
}
