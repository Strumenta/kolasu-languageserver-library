plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.22"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.0"
    id("maven-publish")
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "1.2.1"
    id("com.github.gmazzo.buildconfig") version "5.3.5"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

val kotlinVersion: String by project
val kolasuVersion: String by project
val luceneVersion: String by project
val lsp4jVersion: String by project
val junitVersion: String by project

dependencies {
    implementation("com.github.johnrengelman.shadow:com.github.johnrengelman.shadow.gradle.plugin:7.1.2")
    implementation("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:$kotlinVersion")
}

gradlePlugin {
    website = "https://strumenta.com"
    vcsUrl = "https://github.com/Strumenta/kolasu-languageserver-library"
    plugins {
        create("com.strumenta.kolasu.language-server-plugin") {
            id = "com.strumenta.kolasu.language-server-plugin"
            version = project.version
            displayName = "Kolasu language server plugin"
            description = "Create language servers from Kolasu parsers"
            tags = listOf("kolasu", "language-server", "parser")
            implementationClass = "com.strumenta.kolasu.languageserver.plugin.LanguageServerPlugin"
        }
    }
}

buildConfig {
    packageName = "com.strumenta.kolasu.languageserver.plugin"
    buildConfigField("KOLASU_LSP_VERSION", "${project.version}")
    buildConfigField("KOLASU_VERSION", kolasuVersion)
    buildConfigField("LUCENE_VERSION", luceneVersion)
    buildConfigField("LSP4J_VERSION", lsp4jVersion)
    buildConfigField("JUNIT_VERSION", junitVersion)
}

ktlint {
    version = "1.2.1"
    filter {
        exclude { it.file.path.contains(layout.buildDirectory.dir("generated").get().toString()) }
    }
}
