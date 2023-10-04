plugins {
    id("java-gradle-plugin")
    id("maven-publish")
    id("org.jetbrains.kotlin.jvm") version "1.8.22"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.0"
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
    plugins {
        create("language-server-plugin") { id = "language-server-plugin"; implementationClass = "com.strumenta.languageserver.LanguageServerPlugin" }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Strumenta/kolasu-languageserver-library")
            credentials {
                username = project.findProperty("starlasu.github.user").toString()
                password = project.findProperty("starlasu.github.token").toString()
            }
        }
    }
    publications { create<MavenPublication>("mavenJava") { from(components["java"]) } }
}

group = "com.strumenta"
version = "0.0.0"
