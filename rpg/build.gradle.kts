import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.dokka")
    id("org.jlleitschuh.gradle.ktlint") version "11.3.2"
    id("maven-publish")
    id("signing")
    id("com.github.johnrengelman.shadow") version "7.1.2"

    id("java-library")
    id("net.researchgate.release") version "3.0.2"
    id("application")

    id("com.github.gmazzo.buildconfig") version "4.0.4"
    id("language-server-plugin") version "0.0.0"
}

group = "com.strumenta.rpg"

val githubUser = (
    project.findProperty("starlasu.github.user")
        ?: System.getenv("GITHUB_USER")
        ?: System.getenv("STRUMENTA_PACKAGES_USER")
    ) as? String
    ?: throw RuntimeException("GitHub user not specified")
val githubToken = (
    project.findProperty("starlasu.github.token")
        ?: System.getenv("GITHUB_TOKEN")
        ?: System.getenv("STRUMENTA_PACKAGES_TOKEN")
    ) as? String
    ?: throw RuntimeException("GitHub token not specified")

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        name = project.name
        url = uri("https://maven.pkg.github.com/Strumenta/rpg-parser")
        credentials {
            username = githubUser
            password = githubToken
        }
    }
    maven {
        name = project.name
        url = uri("https://maven.pkg.github.com/Strumenta/kolasu-python-langmodule")
        credentials {
            username = githubUser
            password = githubToken
        }
    }
}

// renamed from mainClassName to avoid conflict in the application configuration
val appMainClassName = "com.strumenta.rpgle.MainKt"

val generatedMain = "src/main/java"
val generatedMainFile = file(generatedMain)

val rpgParserVersion = extra["rpgParserVersion"]
val kotlinVersion = extra["kotlinVersion"]
val kolasuVersion = extra["kolasuVersion"]
val antlrVersion = extra["antlrVersion"]
val jvmVersion = extra["jvmVersion"]

dependencies {
    implementation("com.strumenta:rpg-parser:$rpgParserVersion")
    implementation(files("../jars/symbol-resolution-2.1.23-SNAPSHOT-all.jar"))
    implementation("com.strumenta:language-server:0.0.0")

    implementation("com.strumenta.kolasu:kolasu-core:1.5.31")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.1")

    implementation("org.antlr:antlr4-runtime:$antlrVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")

    implementation("com.strumenta.kolasu:kolasu-core:$kolasuVersion")
    implementation("commons-io:commons-io:2.7")
    implementation("com.github.ajalt.clikt:clikt:3.2.0")
    implementation("org.slf4j:slf4j-api:1.7.28")
    implementation("org.slf4j:slf4j-simple:1.7.30")

    implementation("com.beust:klaxon:5.5")

    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.7.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
}

sourceSets.getByName("main") {
    java.srcDir(generatedMainFile)
}

tasks.withType(KotlinCompile::class).all {
    kotlinOptions {
        jvmTarget = "$jvmVersion"
        freeCompilerArgs = freeCompilerArgs + listOf("-Xuse-experimental=kotlin.ExperimentalUnsignedTypes")
    }
}

tasks.clean {
    delete(file(generatedMain))
    mkdir(generatedMain)
}

tasks.withType(Test::class).all {
    testLogging {
        events("failed")
    }
}

application {
    mainClass.set(appMainClassName)
}

tasks {
    named<ShadowJar>("shadowJar") {
        manifest {
            attributes("Main-Class" to appMainClassName)
            archiveFileName.set("${project.name}.jar")
        }
    }
}

ktlint {
    verbose.set(true)
    outputToConsole.set(true)
    enableExperimentalRules.set(true)
    filter {
        exclude { element ->
            element.file.absolutePath.startsWith(project.file("build").absolutePath + File.separator)
        }
    }
    // disabling no-unused-imports because it does not recognize correctly that some imports are needed
    disabledRules.set(listOf("no-wildcard-imports", "experimental:argument-list-wrapping", "no-unused-imports"))
}

tasks.wrapper {
    gradleVersion = "${project.extra["gradleVersion"]}"
}

buildConfig {
    packageName("com.strumenta.rpgle")
    buildConfigField("String", "TRANSPILER_VERSION", "\"${project.version}\"")
}

languageServer {
    editor = "codium"
    language = "RPG"
    extension = "rpgle"
}
