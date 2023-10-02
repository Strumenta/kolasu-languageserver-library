pluginManagement {
    val kotlinVersion: String by settings
    val dokkaVersion: String by settings
    plugins {
        id("org.jetbrains.kotlin.jvm") version "$kotlinVersion"
        id("org.jetbrains.dokka") version "$dokkaVersion"
    }
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "rpg-to-python-transpiler"
