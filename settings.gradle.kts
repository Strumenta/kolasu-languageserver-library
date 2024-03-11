include("library")
include("testing")
include("plugin")

rootProject.name="kolasu-languageserver"
project(":library").name = "kolasu-languageserver-library"
project(":testing").name = "kolasu-languageserver-testing"
project(":plugin").name = "kolasu-languageserver-plugin"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            // versions
            val releasePluginVersion = version("release", "3.0.2")
            val shadowPluginVersion = version("shadow", "7.1.2")
            val ktlintPluginVersion = version("ktlint", "11.6.0")
            val ktlintLibraryVersion = version("ktlint-library", "0.47.1")
            val kotlinVersion = version("kotlin", "1.8.22")
            val kolasuVersion = version("kolasu", "1.5.45")
            val lsp4jVersion = version("lsp4j", "0.21.1")
            val luceneVersion = version("lucene", "9.8.0")
            val junit5Version = version("junit5", "5.7.1")
            // plugins
            plugin("kotlin-jvm", "org.jetbrains.kotlin.jvm").versionRef(kotlinVersion)
            plugin("ktlint", "org.jlleitschuh.gradle.ktlint").versionRef(ktlintPluginVersion)
            plugin("release", "net.researchgate.release").versionRef(releasePluginVersion)
            plugin("shadow", "com.github.johnrengelman.shadow").versionRef(shadowPluginVersion)
            // libraries
            library("kotlin-reflect", "org.jetbrains.kotlin", "kotlin-reflect").versionRef(kotlinVersion)
//            library("kotlin-stdlib-jdk8", "org.jetbrains.kotlin", "kotlin-stdlib-jdk8").versionRef(kotlinVersion)
            library("kolasu-core", "com.strumenta.kolasu", "kolasu-core").versionRef(kolasuVersion)
            library("lsp4j", "org.eclipse.lsp4j", "org.eclipse.lsp4j").versionRef(lsp4jVersion)
            library("junit5", "org.junit.jupiter", "junit-jupiter-api").versionRef(junit5Version)
            library("lucene", "org.apache.lucene", "lucene-core").versionRef(luceneVersion)
            library("ktlint", "com.pinterest", "ktlint").versionRef(ktlintLibraryVersion)
            // plugin - libraries
            library("shadow", "com.github.johnrengelman.shadow", "com.github.johnrengelman.shadow.gradle.plugin").versionRef(shadowPluginVersion)
            library("kotlin-jvm", "org.jetbrains.kotlin.jvm", "org.jetbrains.kotlin.jvm.gradle.plugin").versionRef(kotlinVersion)
        }
    }
}