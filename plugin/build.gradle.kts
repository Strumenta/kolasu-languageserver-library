plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.22"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.0"
    id("maven-publish")
    id("java-gradle-plugin")
    id("net.researchgate.release") version "3.0.2"
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
        create("com.strumenta.kolasu.language-server-plugin") {
            group = "com.strumenta.kolasu"
            id = "com.strumenta.kolasu.language-server-plugin"
            version = project.version
            implementationClass = "com.strumenta.kolasu.languageserver.plugin.LanguageServerPlugin"
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

release {
    buildTasks.set(listOf(":publish"))
    git {
        requireBranch.set("")
        pushToRemote.set("origin")
    }
}