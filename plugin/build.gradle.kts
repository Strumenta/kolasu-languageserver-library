plugins {
    id("java-gradle-plugin")
    id("maven-publish")
    id("org.jetbrains.kotlin.jvm") version "1.8.22"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.0"
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("language-server-plugin") { id = "language-server-plugin"; implementationClass = "com.strumenta.languageserver.LanguageServerPlugin" }
    }
}

group = "com.strumenta"
version = "0.0.0"
