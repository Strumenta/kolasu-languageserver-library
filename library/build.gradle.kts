plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.22"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.0"
    id("maven-publish")
}

repositories {
    mavenCentral()
    maven {
        name = project.name
        url = uri("https://maven.pkg.github.com/Strumenta/rpg-parser")
        credentials {
            username = (if (extra.has("starlasu.github.user")) extra["starlasu.github.user"] else System.getenv("STRUMENTA_PACKAGES_USER")) as String?
            password = (if (extra.has("starlasu.github.token")) extra["starlasu.github.token"] else System.getenv("STRUMENTA_PACKAGES_TOKEN")) as String?
        }
    }
}

dependencies {
    implementation("com.strumenta.kolasu:kolasu-core:1.5.31")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.21")
    implementation("org.apache.lucene:lucene-core:9.8.0")
    implementation("org.apache.lucene:lucene-queryparser:9.8.0")
    implementation("org.apache.lucene:lucene-codecs:9.8.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.1")
    testImplementation("com.strumenta:rpg-parser:2.1.30")
    testImplementation("com.strumenta:rpg-parser-symbol-resolution:2.1.30")
}

group = "com.strumenta"
version = "0.0.0"

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Strumenta/kolasu-languageserver-library")
            credentials {
                username = (if (extra.has("starlasu.github.user")) extra["starlasu.github.user"] else System.getenv("STRUMENTA_PACKAGES_USER")) as String?
                password = (if (extra.has("starlasu.github.token")) extra["starlasu.github.token"] else System.getenv("STRUMENTA_PACKAGES_TOKEN")) as String?
            }
        }
    }
    publications { create<MavenPublication>("mavenJava") { from(components["java"]) } }
}
