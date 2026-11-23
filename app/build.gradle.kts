import java.util.Base64
import java.util.Properties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

// Decode debug.keystore if it doesn't exist but the base64 file exists
val keystoreFile = project.file("debug.keystore")
val base64File = project.rootProject.file("debug.keystore.base64")
if (base64File.exists()) {
  try {
    val base64Content = base64File.readText().trim()
    val decodedBytes = Base64.getDecoder().decode(base64Content)
    if (!keystoreFile.exists() || keystoreFile.length() == 0L) {
      keystoreFile.writeBytes(decodedBytes)
      println("Successfully decoded debug.keystore: ${keystoreFile.absolutePath} (${keystoreFile.length()} bytes)")
    }
  } catch (e: Exception) {
    println("Warning: Failed to decode debug.keystore from base64: ${e.message}")
  }
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.inventorymanagement.v2"
    minSdk = 24
    targetSdk = 36
    
    val versionPropsFile = project.rootProject.file("version.properties")
    val versionProps = Properties()
    if (versionPropsFile.exists()) {
      versionPropsFile.inputStream().use { versionProps.load(it) }
    } else {
      versionProps.setProperty("major", "1")
      versionProps.setProperty("minor", "0")
      versionProps.setProperty("patch", "1")
      versionProps.setProperty("iteration", "25")
      versionPropsFile.outputStream().use { versionProps.store(it, "Version properties") }
    }

    val major = versionProps.getProperty("major", "1")
    val minor = versionProps.getProperty("minor", "0")
    var iteration = versionProps.getProperty("iteration", "25").toIntOrNull() ?: 25

    // Detect if we are building an APK/applet and auto-increment iteration
    val isBuilding = gradle.startParameter.taskNames.any {
      it.contains("assemble", ignoreCase = true) || it.contains("package", ignoreCase = true) || it.contains("build", ignoreCase = true)
    }
    if (isBuilding) {
      iteration += 1
      versionProps.setProperty("iteration", iteration.toString())
      versionPropsFile.outputStream().use { versionProps.store(it, "Auto-incremented on build") }
    }

    // Auto-increment versionCode using the build timestamp (seconds since epoch).
    // This produces a versionCode in the ~1.79B range, which is strictly increasing with every build
    // and higher than previous builds (~55M), allowing Android OS to smoothly replace existing installs without uninstalling.
    val buildTimeSeconds = (System.currentTimeMillis() / 1000).toInt()
    versionCode = buildTimeSeconds
    versionName = "$major.$minor.$iteration"

    val buildDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date())
    buildConfigField("String", "BUILD_TIME", "\"$buildDate\"")
    buildConfigField("int", "BUILD_ITERATION", "$iteration")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = keystoreFile
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      if (keystoreFile.exists() || file(System.getProperty("user.home") + "/.android/debug.keystore").exists()) {
        signingConfig = signingConfigs.getByName("debugConfig")
      } else {
        signingConfig = signingConfigs.getByName("debug")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
  implementation(libs.mlkit.barcode.scanning)
  implementation(libs.mlkit.text.recognition)
  implementation("androidx.biometric:biometric:1.2.0-alpha05")
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.work.runtime.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation("com.google.firebase:firebase-firestore")
  implementation("com.google.firebase:firebase-database")
  implementation("com.google.firebase:firebase-auth")
  implementation("com.google.firebase:firebase-storage")
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  // implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
