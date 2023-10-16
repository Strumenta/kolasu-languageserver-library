plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.22"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.0"
    id("maven-publish")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.strumenta.kolasu:kolasu-core:1.5.31")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.21")
    implementation("org.apache.lucene:lucene-core:9.8.0")
}

group = "com.strumenta"
version = "0.0.0"

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
