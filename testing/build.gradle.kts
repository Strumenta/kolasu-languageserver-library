import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.mavenPublish)
    signing
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":kolasu-languageserver-library"))
    implementation(libs.kolasu.core)
    implementation(libs.lsp4j)
    implementation(libs.junit5)
}

java {
    withSourcesJar()
    withJavadocJar()
}

val isReleaseVersion = !(project.version as String).endsWith("SNAPSHOT")

mavenPublishing {
    coordinates(
        groupId = "com.strumenta.kolasu",
        artifactId = "language-server-testing",
        version = project.version as String,
    )

    pom {
        name.set("kolasu-language-server-testing")
        description.set("Tools for testing a Language Server Protocol adapter for Kolasu")
        version = project.version as String
        packaging = "jar"
        inceptionYear = "2023"
        url.set("https://github.com/Strumenta/kolasu-languageserver-library")

        scm {
            url.set("https://github.com/Strumenta/kolasu-languageserver-library.git")
            connection.set("scm:git:git:github.com/Strumenta/kolasu-languageserver-library.git")
            developerConnection.set("scm:git:ssh:github.com/Strumenta/kolasu-languageserver-library.git")
        }

        licenses {
            license {
                name.set("Apache Licenve V2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                distribution.set("repo")
            }
        }

        // The developers entry is strictly required by Maven Central
        developers {
            developer {
                id.set("martin-azpillaga")
                name.set("Martin Azpillaga")
                email.set("martin.azpillaga@strumenta.com")
            }
        }
    }
    publishToMavenCentral("CENTRAL_PORTAL", true)
    signAllPublications()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}


tasks {
    withType(KotlinCompile::class).all {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }

    withType(Sign::class) {
        enabled = isReleaseVersion
    }
}

afterEvaluate {
    tasks.named("generateMetadataFileForMavenPublication") {
        dependsOn("kotlinSourcesJar", "dokkaJavadocJar")
    }
}
