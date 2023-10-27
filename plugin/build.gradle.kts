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
            id = "com.strumenta.language-server-plugin"
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
                username = (if (extra.has("starlasu.github.user")) extra["starlasu.github.user"] else System.getenv("STRUMENTA_PACKAGES_USER")) as String?
                password = (if (extra.has("starlasu.github.token")) extra["starlasu.github.token"] else System.getenv("STRUMENTA_PACKAGES_TOKEN")) as String?
            }
        }
    }
}
