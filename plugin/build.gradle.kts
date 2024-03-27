plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    `maven-publish`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "1.2.1"
    id("com.github.gmazzo.buildconfig") version "5.3.5"
}

dependencies {
    implementation(libs.shadow)
    implementation(libs.kotlin.jvm)
}

gradlePlugin {
    website = "https://strumenta.com"
    vcsUrl = "https://github.com/Strumenta/kolasu-languageserver-library"
    plugins {
        create("com.strumenta.kolasu.language-server-plugin") {
            id = "com.strumenta.kolasu.language-server-plugin"
            version = project.version
            displayName = "Kolasu language server plugin"
            description = "Create language servers from Kolasu parsers"
            tags = listOf("kolasu", "language-server", "parser")
            implementationClass = "com.strumenta.kolasu.languageserver.plugin.LanguageServerPlugin"
        }
    }
}

buildConfig {
    packageName = "com.strumenta.kolasu.languageserver.plugin"
    buildConfigField("KOLASU_LSP_VERSION", "${project.version}")
    buildConfigField("KOLASU_VERSION", libs.versions.kolasu)
    buildConfigField("LSP4J_VERSION", libs.versions.lsp4j)
    buildConfigField("JUNIT_VERSION", libs.versions.junit5)
    buildConfigField("ANTLR4C3_VERSION", libs.versions.antlr4.c3)
}

ktlint {
    filter {
        exclude { it.file.path.contains(layout.buildDirectory.dir("generated").get().toString()) }
    }
}
