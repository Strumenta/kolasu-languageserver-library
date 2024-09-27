import java.net.URI
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
    signing
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.reflect)
    implementation(libs.kolasu.core)
    implementation(libs.lsp4j)
    implementation(libs.lucene)
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
                username = project.properties["ossrhTokenUsername"] as? String
                password = project.properties["ossrhTokenPassword"] as? String
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

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType(KotlinCompile::class).all {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

tasks.withType(Sign::class) {
	enabled = isReleaseVersion
}
