import java.net.URI

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.22"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.0"
    id("maven-publish")
    id("signing")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.21")
    implementation("com.strumenta.kolasu:kolasu-core:1.5.45")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.1")
    implementation("org.apache.lucene:lucene-core:9.8.0")
}

java {
    withSourcesJar()
    withJavadocJar()
}

val isReleaseVersion = !(project.version as String).endsWith("SNAPSHOT")

publishing {
    repositories {
        maven {
            val releaseRepo = URI("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotRepo = URI("https://oss.sonatype.org/content/repositories/snapshots/")
            url = if (isReleaseVersion) releaseRepo else snapshotRepo
            credentials {
                username = project.properties["ossrhUsername"] as? String
                password = project.properties["ossrhPassword"] as? String
            }
        }
    }
    publications {
        create<MavenPublication>("language-server-library") {
            groupId = "com.strumenta.kolasu"
            artifactId = "language-server"
            version = project.version as String

            artifact(tasks.getByName("jar"))
            artifact(tasks.getByName("sourcesJar"))
            artifact(tasks.getByName("javadocJar"))

            pom {
                name = "Kolasu language server"
                description = "Create a language server for parsers created with Kolasu"
                inceptionYear = "2023"
                url = "https://github.com/Strumenta/kolasu-languageserver-library"
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://opensource.org/license/apache-2-0/"
                    }
                }
                developers {
                    developer {
                        id = "martin-azpillaga"
                        name = "Martin Azpillaga Aldalur"
                        email = "martin.azpillaga@strumenta.com"
                    }
                }
                scm {
                    url = "https://github.com/Strumenta/kolasu-languageserver-library.git"
                    connection = "scm:git:git:github.com/Strumenta/kolasu-languageserver-library.git"
                    developerConnection = "scm:git:ssh:github.com/Strumenta/kolasu-languageserver-library.git"
                }
            }
        }
    }
}

signing {
    sign(publishing.publications.getByName("language-server-library"))
}
