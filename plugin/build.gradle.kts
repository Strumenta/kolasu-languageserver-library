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
        create("language-server-plugin") {
            group = "com.strumenta"
            id = "language-server-plugin"
            version = "0.0.0"
            implementationClass = "com.strumenta.languageserver.LanguageServerPlugin"
        }
    }
}

publishing {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/Strumenta/kolasu-languageserver-library")
            credentials {
                username = project.findProperty("starlasu.github.user").toString()
                password = project.findProperty("starlasu.github.token").toString()
            }
        }
    }
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "language-server-plugin"
            from(components["java"])
        }
    }
}
