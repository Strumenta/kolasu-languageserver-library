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

val kolasuVersion: String by project
val lsp4jVersion: String by project
val junitVersion: String by project

dependencies {
    implementation(project(":kolasu-languageserver-library"))
    implementation("com.strumenta.kolasu:kolasu-core:$kolasuVersion")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:$lsp4jVersion")
    implementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
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
        create<MavenPublication>("language-server-testing") {
            groupId = "com.strumenta.kolasu"
            artifactId = "language-server-testing"
            version = project.version as String

            artifact(tasks.getByName("jar"))
            artifact(tasks.getByName("sourcesJar"))
            artifact(tasks.getByName("javadocJar"))

            pom {
                name = "Kolasu language server testing"
                description = "Test Kolasu language servers"
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
    sign(publishing.publications.getByName("language-server-testing"))
}

ktlint {
    version = "1.2.1"
}
