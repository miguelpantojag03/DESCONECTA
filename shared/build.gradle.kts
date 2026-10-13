import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Credenciales de Supabase: SOLO en local.properties (no se sube a git).
//   supabase.url=https://xxxx.supabase.co
//   supabase.key=<anon / publishable key>
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun localProp(name: String) = (localProps.getProperty(name) ?: "").replace("\"", "")

android {
    namespace = "com.desconecta.hp.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "SUPABASE_URL", "\"${localProp("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${localProp("supabase.key")}\"")
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

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    // El sistema de diseño solo depende de Compose "base" (sin Material) para que
    // lo puedan envolver tanto Material 3 (teléfono) como Wear Material 3 (reloj).
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.foundation)
    api(libs.compose.animation)
    api(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    api(libs.androidx.core.ktx)
    api(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.serialization.json)

    // Nube: respaldo del estado/historial/logros y sync en tiempo real con el reloj.
    api(platform(libs.supabase.bom))
    api(libs.supabase.auth)
    api(libs.supabase.postgrest)
    api(libs.supabase.realtime)
    api(libs.ktor.client.okhttp)

    testImplementation(libs.junit)
}
