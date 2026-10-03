import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val releaseSigningFile = file("${System.getProperty("user.home")}/.android/touchlock-release.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.exists()) releaseSigningFile.inputStream().use { load(it) }
}
android {
    namespace = "com.yeeyon.touchlock"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.yeeyon.touchlock"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        create("release") {
            if (releaseSigningFile.exists()) {
                storeFile = file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }
}
dependencies { testImplementation("junit:junit:4.13.2") }
