import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Assinatura do release: lida de keystore.properties (na raiz do projeto, fora do git) ou de variáveis
 * de ambiente. Sem nenhum dos dois, o release sai sem assinatura, como antes (ver README › Assinatura).
 */
val signing: Map<String, String>? = run {
    val f = rootProject.file("keystore.properties")
    val p = Properties()
    if (f.exists()) f.inputStream().use { p.load(it) }
    fun v(key: String, env: String) = p.getProperty(key) ?: System.getenv(env)
    val store = v("storeFile", "KALENDRIUM_STORE_FILE") ?: return@run null
    mapOf(
        "storeFile" to store,
        "storePassword" to (v("storePassword", "KALENDRIUM_STORE_PASSWORD") ?: ""),
        "keyAlias" to (v("keyAlias", "KALENDRIUM_KEY_ALIAS") ?: ""),
        "keyPassword" to (v("keyPassword", "KALENDRIUM_KEY_PASSWORD") ?: ""),
    )
}

android {
    namespace = "com.kalendrium"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kalendrium"
        minSdk = 24
        targetSdk = 36
        versionCode = 7
        versionName = "6.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        signing?.let { s ->
            create("release") {
                storeFile = rootProject.file(s.getValue("storeFile"))
                storePassword = s.getValue("storePassword")
                keyAlias = s.getValue("keyAlias")
                keyPassword = s.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signing != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        // java.time (LocalDate) em Android 7.x
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)

    // testes no aparelho/emulador (Android Studio: app/src/androidTest › Run)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
