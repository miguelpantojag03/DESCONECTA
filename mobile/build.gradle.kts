import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
}

// Credenciales de Supabase: SOLO en local.properties (no se sube a git).
//   supabase.url=https://xxxx.supabase.co
//   supabase.key=<anon / publishable key>      ← nunca la service_role
//   google.webClientId=<ID de cliente WEB de Google Cloud>  (opcional: sin él no se muestra "Continuar con Google")
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun localProp(name: String) = (localProps.getProperty(name) ?: "").replace("\"", "")

val signingProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun signingProp(name: String): String = signingProps.getProperty(name) ?: ""

android {
    namespace = "com.desconecta.hp.mobile"
    compileSdk = 36

    defaultConfig {
        // Mismo applicationId que el reloj: requisito de la Wearable Data Layer API.
        applicationId = "com.desconecta.hp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SUPABASE_URL", "\"${localProp("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${localProp("supabase.key")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProp("google.webClientId")}\"")
    }
    // Firma de release: el teléfono y el reloj usan la MISMA clave (la Data Layer solo
    // comunica apps con el mismo applicationId y la misma firma). Datos en local.properties:
    // release.storeFile / release.storePassword / release.keyAlias / release.keyPassword.
    // Sin ellos, release se compila sin firmar.
    val releaseStore = signingProp("release.storeFile")
    if (releaseStore.isNotBlank() && file(releaseStore).exists()) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = signingProp("release.storePassword")
                keyAlias = signingProp("release.keyAlias")
                keyPassword = signingProp("release.keyPassword")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // Sin R8 por ahora: supabase-kt, Ktor y kotlinx.serialization necesitan reglas
            // propias; se activará con ellas y una pasada de pruebas sobre el APK minificado.
            isMinifyEnabled = false
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
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.systemProperty("hp.integration", System.getProperty("hp.integration") ?: "false") }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)

    // Supabase (solo el teléfono habla con la nube)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.functions)
    implementation(libs.credentials)
    implementation(libs.credentials.play)
    implementation(libs.googleid)
    implementation(libs.ktor.client.okhttp)

    debugImplementation(libs.compose.ui.tooling)

    // Capturas de pantalla en la JVM (Roborazzi + Robolectric): ./gradlew :mobile:recordRoborazziDebug
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
