plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
// Keep ordinary CI/test APKs buildable before a Firebase project is connected.
// A real connected APK requires app/google-services.json at build time.
if (file("google-services.json").isFile) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.denemelab.preview"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    // Debug builds must use the exact test key registered in Firebase.
    // AGP may otherwise generate/use a different default signing location,
    // causing Google sign-in to fail even though CI printed the right SHA-1.
    signingConfigs {
        getByName("debug") {
            val stableKeystore = file(
                System.getProperty("user.home") + "/.android/debug.keystore"
            )
            if (stableKeystore.isFile) {
                storeFile = stableKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }
    defaultConfig {
        applicationId = "com.denemelab.preview"
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        targetSdk = 36
        versionCode = 23
        versionName = "0.23.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
}
dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.coil.compose)
    implementation(libs.mlkit.text.recognition)
    // Read searchable PDF text directly before falling back to image OCR.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    // Compatibility fallback for Android 16 Credential Manager failures.
    implementation("com.google.android.gms:play-services-auth:21.6.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("io.coil-kt:coil-svg:2.7.0")
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.core)
    androidTestImplementation(libs.androidx.runner)
}
