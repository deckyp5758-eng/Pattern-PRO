import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.hubpatternengine.qxtrva"
    minSdk = 24
    targetSdk = 36
    val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
    versionCode = if (ciRunNumber != null && ciRunNumber > 0) 100 + ciRunNumber else 1
    versionName = if (ciRunNumber != null && ciRunNumber > 0) "1.0.$ciRunNumber" else "1.0.0"

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
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
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
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.addAll(
    listOf(
      "FIREBASE_APPCHECK_DEBUG_TOKEN",
      "METAAPI_ACCOUNT_ID",
      "METAAPI_AUTH_TOKEN",
      "KEYSTORE_BASE64",
      "STORE_PASSWORD",
      "KEY_PASSWORD",
      "KEY_ALIAS",
      "GITHUB_TOKEN",
      "GEMINI_API_KEY"
    )
  )
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

val verifyNoEmbeddedSecrets by tasks.registering {
  group = "verification"
  description = "Fails build if non-placeholder MetaApi credentials, tokens, or private keys exist in source/env/BuildConfig."
  val rootDirSnapshot = rootProject.projectDir
  val srcMainDirSnapshot = file("src/main")
  val buildConfigDirProvider = layout.buildDirectory.dir("generated/source/buildConfig")
  doLast {
    val scanTargets = mutableListOf<File>()
    listOf(".env", ".env.example").forEach { rel ->
      val f = File(rootDirSnapshot, rel)
      if (f.exists()) scanTargets.add(f)
    }
    if (srcMainDirSnapshot.exists()) {
      srcMainDirSnapshot.walkTopDown()
        .filter { it.isFile && it.extension in listOf("kt", "java", "xml", "properties", "json") }
        .forEach { scanTargets.add(it) }
    }
    buildConfigDirProvider.get().asFile
      .takeIf { it.exists() }
      ?.walkTopDown()
      ?.filter { it.isFile }
      ?.forEach { scanTargets.add(it) }

    val forbiddenPropRegex = Regex("""(?m)^\s*(METAAPI_ACCOUNT_ID|METAAPI_AUTH_TOKEN)\s*=""")
    val forbiddenBuildConfigRegex = Regex("""\bMETAAPI_(ACCOUNT_ID|AUTH_TOKEN)\b""")
    val privateKeyRegex = Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----""")
    val jwtRegex = Regex("""eyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{10,}""")

    val violations = mutableListOf<String>()
    for (file in scanTargets) {
      val content = file.readText()
      if (file.name == ".env" || file.name == ".env.example") {
        if (forbiddenPropRegex.containsMatchIn(content)) {
          violations.add("${file.relativeTo(rootDirSnapshot)}: contains METAAPI_ACCOUNT_ID/METAAPI_AUTH_TOKEN key")
        }
      }
      if (file.name == "BuildConfig.java" && forbiddenBuildConfigRegex.containsMatchIn(content)) {
        violations.add("${file.relativeTo(rootDirSnapshot)}: BuildConfig embeds METAAPI credential field")
      }
      if (privateKeyRegex.containsMatchIn(content)) {
        violations.add("${file.relativeTo(rootDirSnapshot)}: embeds private key block")
      }
      if (jwtRegex.containsMatchIn(content)) {
        violations.add("${file.relativeTo(rootDirSnapshot)}: embeds hardcoded JWT-like token")
      }
    }
    if (violations.isNotEmpty()) {
      throw GradleException(
        "Security build check failed — embedded secrets detected:\n - " +
          violations.joinToString("\n - ")
      )
    }
  }
}

tasks.configureEach {
  if (name == "verifyNoEmbeddedSecrets") {
    mustRunAfter("generateDebugBuildConfig", "generateReleaseBuildConfig")
  }
  if (name == "assembleRelease" || name == "packageRelease" || name == "bundleRelease" || name == "preReleaseBuild") {
    if (name != "preReleaseBuild") {
      dependsOn(verifyNoEmbeddedSecrets)
    }
  }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
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
