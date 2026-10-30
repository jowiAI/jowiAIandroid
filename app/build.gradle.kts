import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Push (ledger 45): the google-services plugin reads app/google-services.json — the owner's
// Firebase project with the Android app `ai.workis.jowi`. Without the file the plugin would
// fail the build, so it is applied only once the file is there; without the plugin FirebaseApp
// never initialises and PushService has no carrier (the Hesap switch says so). Nothing else changes.
if (file("google-services.json").exists()) apply(plugin = libs.plugins.google.services.get().pluginId)
// Crash reports (2026-10-27): Crashlytics rides on the same file — a tester's phone crashed at
// launch with no way to read its log (adb needs the device in hand). Dormant without the file.
if (file("google-services.json").exists()) apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)

// Tester distribution: Firebase App Distribution on the same project. The plugin is applied with
// the other two (it is harmless without google-services.json; the upload task is what needs the
// file). `./gradlew assembleRelease appDistributionUploadRelease` signs with the release key,
// uploads the APK and tells the "ekip" group; auth = the Firebase CLI login on this Mac.
// Bump versionCode before every upload — the same code is refused as a duplicate.
// The block is the project default (`firebaseAppDistributionDefault`); plugin 5.3 deprecates the plain
// `firebaseAppDistribution` extension for root projects.
apply(plugin = libs.plugins.firebase.appdistribution.get().pluginId)
configure<com.google.firebase.appdistribution.gradle.AppDistributionExtension.ProjectDefaultAppDistributionExtension> {
    artifactType = "APK"
    groups = "ekip"
    releaseNotesFile = file("release-notes.txt").path
}

// Release signing: keystore.properties (gitignored) → storeFile / storePassword /
// keyAlias / keyPassword. Without it the release build stays unsigned.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Which code is on the phone (ledger 80, 2026-10-29): the git short sha stamped into BuildConfig at
// build time — "+" when the tree had uncommitted changes, "?" when git is absent. Hesap's footer
// prints it after the version and build; X-Workis-Client stays "app <version> (<code>)".
val gitCommit: String = try {
    // providers.exec, not ProcessBuilder: the configuration cache tracks it as an input and re-runs it.
    fun git(vararg args: String): String {
        val r = providers.exec { commandLine("git", *args); workingDir = rootDir; isIgnoreExitValue = true }
        if (r.result.get().exitValue != 0) throw IllegalStateException(r.standardError.asText.get())
        return r.standardOutput.asText.get().trim()
    }
    val sha = git("rev-parse", "--short", "HEAD")
    if (git("status", "--porcelain").isNotEmpty()) "$sha+" else sha
} catch (e: Exception) { "?" }

android {
    namespace = "ai.workis.jowi"
    compileSdk = 37

    defaultConfig {
        applicationId = "ai.workis.jowi"
        minSdk = 26
        targetSdk = 36
        versionCode = 19
        versionName = "0.8.6"
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.browser)
    // passkeys: the system holds the key, the app only carries JSON (API 28+)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    // the carrier's label scan: Google's code scanner UI — no camera permission in this app
    implementation(libs.play.services.code.scanner)
    // push (ledger 45): the FCM carrier — a token for push/register/, messages → the watching screens
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    debugImplementation(libs.androidx.ui.tooling)
}
