plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace="app.flvy.android"
    compileSdk=35
    defaultConfig {
        applicationId="app.flvy.android"
        minSdk=26
        targetSdk=35
        versionCode=1
        versionName="0.1.0"
    }
    buildTypes { release { isMinifyEnabled=false; isDebuggable=false } }
}
kotlin { jvmToolchain(17) }
