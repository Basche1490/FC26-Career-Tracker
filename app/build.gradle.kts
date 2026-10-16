plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

// CI sets GITHUB_RUN_NUMBER, so every build gets a higher versionCode than the one before it.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toIntOrNull() ?: 1

android {
  namespace = "com.fctracker.career"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.fctracker.career"
    minSdk = 24
    targetSdk = 36
    versionCode = buildNumber
    versionName = "2.0.$buildNumber"
  }

  signingConfigs {
    // One fixed key, kept in the repository (signing/), so every APK this project builds is signed
    // the same way and can be installed over the previous one without uninstalling (and losing data).
    // It is a sideloading key, not a Play Store key.
    create("shared") {
      storeFile = rootProject.file("signing/fctracker.keystore")
      storePassword = "fctracker"
      keyAlias = "fctracker"
      keyPassword = "fctracker"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("shared")
    }
    debug {
      applicationIdSuffix = ".debug"
      signingConfig = signingConfigs.getByName("shared")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures { compose = true }

  testOptions { unitTests { isReturnDefaultValues = true } }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation("com.google.code.gson:gson:2.10.1")
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.coil.compose)
  implementation(libs.coil.svg)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.okhttp)
  debugImplementation(libs.androidx.compose.ui.tooling)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  // The Android SDK's org.json is a stub in plain JVM unit tests, so tests use the real implementation.
  testImplementation("org.json:json:20240303")
}
