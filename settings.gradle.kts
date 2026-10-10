pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HP-HealthPoints"

include(":shared") // Modelos del juego + sistema de diseño (tokens) compartidos
include(":wear")   // App de Wear OS: el "mando" del juego
include(":mobile") // App de teléfono: monitoreo, bloqueo y configuración
