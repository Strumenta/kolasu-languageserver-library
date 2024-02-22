plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.22"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.0"
    id("maven-publish")
    id("java-gradle-plugin")
    id("net.researchgate.release") version "3.0.2"
    id("com.gradle.plugin-publish") version "1.2.1"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.github.johnrengelman.shadow:com.github.johnrengelman.shadow.gradle.plugin:7.1.2")
    implementation("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:1.6.0-M1")
}

gradlePlugin {
    website = "https://strumenta.com"
    vcsUrl = "https://github.com/Strumenta/kolasu-languageserver-library"
    plugins {
        create("com.strumenta.kolasu.language-server-plugin") {
            id = "com.strumenta.kolasu.languageserver.plugin"
            version = project.version
            displayName = "Kolasu language server plugin"
            description = "Create language servers from Kolasu parsers"
            tags = listOf("kolasu", "language-server", "parser")
            implementationClass = "com.strumenta.kolasu.languageserver.plugin.LanguageServerPlugin"
        }
    }
}
