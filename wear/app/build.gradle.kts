plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.soundalert.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.soundalert.wear"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // El emulador objetivo y los relojes actuales son arm64.
        ndk { abiFilters += "arm64-v8a" }
        // API pública del backend (Railway). No es un secreto: el reloj nunca lleva
        // claves. Se cambia con -Psoundalert.apiUrl=... o en ~/.gradle/gradle.properties.
        buildConfigField("String", "API_BASE_URL", "\"${providers.gradleProperty("soundalert.apiUrl").get()}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    testOptions {
        // Los tests JVM ejecutan código que escribe en android.util.Log.
        unitTests.isReturnDefaultValues = true
    }

    androidResources {
        // El modelo se mapea en memoria directamente desde el APK.
        noCompress += "tflite"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.litert)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // org.json real: el de android.jar solo tiene stubs en los tests JVM.
    testImplementation(libs.json)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    // ui-test trae Espresso 3.5, que falla en API 37 (InputManager.getInstance ya no existe).
    androidTestImplementation(libs.androidx.test.espresso.core)
}
