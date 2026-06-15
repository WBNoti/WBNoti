import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.wbnoti"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.wbnoti"
        minSdk = 26
        targetSdk = 37
        versionCode = 9
        versionName = "0.18"
    }

    signingConfigs {
        create("release") {
            // Local: read from keystore.properties. CI: read from environment variables.
            storeFile = file(keystoreProps["storeFile"] as? String ?: System.getenv("SIGNING_STORE_FILE") ?: "release.jks")
            storePassword = keystoreProps["storePassword"] as? String ?: System.getenv("SIGNING_STORE_PASSWORD") ?: ""
            keyAlias = keystoreProps["keyAlias"] as? String ?: System.getenv("SIGNING_KEY_ALIAS") ?: ""
            keyPassword = keystoreProps["keyPassword"] as? String ?: System.getenv("SIGNING_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
