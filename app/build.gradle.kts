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
        versionCode=6
        versionName="0.6.0"
    }
    buildTypes { release { isMinifyEnabled=false; isDebuggable=false } }
}
dependencies { implementation("androidx.webkit:webkit:1.12.1") }

kotlin { jvmToolchain(17) }
