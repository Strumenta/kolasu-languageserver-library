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
