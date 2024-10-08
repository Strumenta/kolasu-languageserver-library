import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    `maven-publish`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "1.2.1"
    id("com.github.gmazzo.buildconfig") version "5.3.5"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.shadow)
    implementation(libs.kotlin.jvm)
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
    buildConfigField("KOLASU_VERSION", libs.versions.kolasu)
    buildConfigField("LUCENE_VERSION", libs.versions.lucene)
    buildConfigField("LSP4J_VERSION", libs.versions.lsp4j)
    buildConfigField("JUNIT_VERSION", libs.versions.junit5)
}

ktlint {
    filter {
        exclude { it.file.path.contains(layout.buildDirectory.dir("generated").get().toString()) }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType(KotlinCompile::class).all {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

val isReleaseVersion = !(project.version as String).endsWith("SNAPSHOT")

tasks.withType(Sign::class) {
    enabled = isReleaseVersion
}
