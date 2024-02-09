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

java {
    withSourcesJar()
    withJavadocJar()
}

group = "com.strumenta.kolasu"
version = "1.0.0"

publishing {
    repositories {
        maven {
            name = "OSSRH"
            url = URI("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = project.properties["ossrhUser"] as String
                password = project.properties["osshrPassword"] as String
            }
        }
    }
    publications {
        create<MavenPublication>("language-server") {
            groupId = "com.strumenta.kolasu"
            artifactId = "language-server"
            version = "1.0.0"

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
//        create<MavenPublication>("language-server-test") {
//            groupId = "com.strumenta.kolasu"
//            artifactId = "language-server-test"
//            version = "0.0.0"
//            artifact(tasks.getByName("jarTest"))
//        }
    }
}

signing {
    sign(publishing.publications.getByName("language-server"))
}
