plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.22"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.0"
    id("maven-publish")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.21")

    implementation("com.strumenta.kolasu:kolasu-core:1.5.31")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.1")
    implementation("org.apache.lucene:lucene-core:9.8.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.1")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.6.0")
}

tasks.register<Jar>("jarTest") {
    from(tasks.getByName("compileTestKotlin"))
    archiveBaseName = "library-test"
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
    publications {
        create<MavenPublication>("language-server") {
            groupId = "com.strumenta.kolasu"
            artifactId = "language-server"
            version = "0.0.0"
            artifact(tasks.getByName("jar"))
        }
        create<MavenPublication>("language-server-test") {
            groupId = "com.strumenta.kolasu"
            artifactId = "language-server-test"
            version = "0.0.0"
            artifact(tasks.getByName("jarTest"))
        }
    }
}
