pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://alphacephei.com/maven/")
    }
}
rootProject.name = "Tadashi"
include(":app")
