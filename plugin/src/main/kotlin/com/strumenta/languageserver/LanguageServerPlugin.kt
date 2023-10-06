package com.strumenta.languageserver

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformJvmPlugin
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*

open class LanguageServerExtension {
    var editor: String = "code"
    var language: String = "language"
    var fileExtensions: List<String> = listOf("extension")
    var shadowJarName: String = ""
    var examples: String = "examples"
    var textmateGrammar: String = "grammar.tmLanguage"
    var textmateGrammarScope: String = "scope.main"
    var logoPath: Path = Paths.get("logo.png")
    var fileIconPath: Path = Paths.get("fileIcon.png")
}

class LanguageServerPlugin : Plugin<Project?> {

    private var extension: LanguageServerExtension = LanguageServerExtension()

    override fun apply(project: Project?) {
        if (project == null) return

        project.pluginManager.apply(ShadowPlugin::class.java)
        project.pluginManager.apply(KotlinPlatformJvmPlugin::class.java)

        project.repositories.add(project.repositories.mavenCentral())
        project.repositories.add(project.repositories.mavenLocal())

        if (project.rootProject.subprojects.find { it.name == "ast" } != null) {
            project.dependencies.add("implementation", project.dependencies.project(mapOf("path" to ":ast")))
        }
        project.dependencies.add("implementation", "com.strumenta:language-server:0.0.0")
        project.dependencies.add("implementation", "com.strumenta.kolasu:kolasu-core:1.5.31")
        project.dependencies.add("implementation", "org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.1")
        project.dependencies.add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit:1.8.22")

        val language = project.rootProject.name

        val shadowJar = project.tasks.getByName("shadowJar") as ShadowJar
        shadowJar.manifest.attributes["Main-Class"] = "com.strumenta.$language.languageserver.MainKt"
        shadowJar.archiveFileName.set("$language.jar")

        project.extensions.add("languageServer", LanguageServerExtension::class.java)
        extension = project.extensions.getByType(LanguageServerExtension::class.java)
        extension.language = language
        extension.fileExtensions = mutableListOf(".$language")
        extension.editor = "code"
        extension.shadowJarName = language
        extension.examples = project.rootDir.absolutePath + "/examples"
        extension.logoPath = Paths.get(project.projectDir.toString(), "src", "main", "resources", "logo.png")
        extension.fileIconPath = Paths.get(project.projectDir.toString(), "src", "main", "resources", "fileIcon.png")

        addCreateEntryPointTask(project)
        addCreateVscodeExtensionTask(project)
        addLaunchVscodeEditorTask(project)
    }

    private fun addCreateEntryPointTask(project: Project) {
        project.tasks.create("createEntryPoint").apply {
            this.group = "language server"
            this.description = "Create an entrypoint under src/main/kotlin that starts communication with a kolasu server"
            this.actions = listOf(Action { _ -> try { createEntryPoint(project) } catch (exception: Exception) { System.err.println(exception.message) } })
        }
    }

    private fun createEntryPoint(project: Project) {
        val root = project.projectDir.toString()
        val language = extension.language

        if (Files.exists(Paths.get(root, "src", "main", "kotlin", "com", "strumenta", language, "languageserver", "Main.kt"))) {
            return
        }
        Files.createDirectories(Paths.get(root, "src", "main", "kotlin", "com", "strumenta", language, "languageserver"))
        Files.writeString(
            Paths.get(root, "src", "main", "kotlin", "com", "strumenta", language, "languageserver", "Main.kt"),
            """
            package com.strumenta.$language.languageserver

            import com.strumenta.$language.parser.${language.capitalized()}KolasuParser
            import com.strumenta.kolasu.languageserver.library.KolasuServer

            fun main() {
                val parser = ${language.capitalized()}KolasuParser()
                val server = KolasuServer(parser)
                server.startCommunication()
            }
            """.trimIndent()
        )
    }

    private fun addLaunchVscodeEditorTask(project: Project) {
        project.tasks.create("launchVscodeEditor").apply {
            this.group = "language server"
            this.description = "Launch the configured vscode editor with the language server installed (defaults to code)"
            this.actions = listOf(Action { _ -> try { launchVscodeEditor(project) } catch (exception: Exception) { System.err.println(exception.message) } })
            this.dependsOn(project.tasks.getByName("createVscodeExtension"))
        }
    }

    private fun launchVscodeEditor(project: Project) {
        ProcessBuilder(extension.editor, "--extensionDevelopmentPath", "${project.projectDir}/build/vscode", extension.examples).directory(project.projectDir).start().waitFor()
    }

    private fun addCreateVscodeExtensionTask(project: Project) {
        project.tasks.create("createVscodeExtension").apply {
            this.group = "language server"
            this.description = "Create language server extension folder for vscode under build/vscode"
            this.actions = listOf(Action { _ -> try { createVscodeExtension(project) } catch (exception: Exception) { System.err.println(exception.message) } })
            this.dependsOn.add(project.tasks.getByName("shadowJar"))
        }
    }

    private fun createVscodeExtension(project: Project) {
        val root = project.projectDir.toString()
        val name = extension.language
        Files.createDirectories(Paths.get(root, "build", "vscode"))

        var grammars = ""
        if (Files.exists(Paths.get(root, "src", "main", "resources", extension.textmateGrammar))) {
            grammars = """
            ,
            "grammars":
            [
                {"language": "$name", "scopeName": "${extension.textmateGrammarScope}", "path": "./grammar.tmLanguage"}
            ]
            """.trimIndent()
            Files.copy(Paths.get(root, "src", "main", "resources", extension.textmateGrammar), Paths.get(root, "build", "vscode", "grammar.tmLanguage"), StandardCopyOption.REPLACE_EXISTING)
        }

        var logo = ""
        if (Files.exists(extension.logoPath)) {
            logo = """"icon": "logo.png","""
        }

        var fileIcon = ""
        if (Files.exists(extension.fileIconPath)) {
            fileIcon = """, "icon": {"dark": "fileIcon.png", "light": "fileIcon.png"}"""
        }

        Files.writeString(
            Paths.get(root, "build", "vscode", "package.json"),
            """
        {
            "name": "${name.lowercase(Locale.getDefault())}",
            "version": "0.0.0",$logo
            "publisher": "strumenta",
            "contributes":
            {
                "languages":
                [
                    {"id": "$name", "extensions": ["${extension.fileExtensions.joinToString("\", \"")}"]$fileIcon}
                ]$grammars
            },
            "engines": {"vscode": "^1.52.0"},
            "activationEvents": ["onLanguage:$name"],
            "main": "client.js"
        }
            """.trimIndent()
        )

        Files.writeString(
            Paths.get(root, "build", "vscode", "client.js"),
            """
        let {LanguageClient} = require("../node_modules/vscode-languageclient/node");

        async function activate (context)
        {
            let productionServer = {run: {command: "java", args: ["-jar", context.asAbsolutePath("server.jar")]}};

            let languageClient = new LanguageClient("$name", "$name language server", productionServer, {documentSelector: ["$name"]});
            await languageClient.start();

            context.subscriptions.push(languageClient);
        }

        module.exports = {activate};
            """.trimIndent()
        )

        Files.copy(Paths.get(root, "build", "libs", extension.shadowJarName + ".jar"), Paths.get(root, "build", "vscode", "server.jar"), StandardCopyOption.REPLACE_EXISTING)

        ProcessBuilder("npm", "install", "--prefix", "build", "vscode-languageclient").directory(project.projectDir).start().waitFor()
        ProcessBuilder("npx", "esbuild", "build/vscode/client.js", "--bundle", "--external:vscode", "--format=cjs", "--platform=node", "--outfile=build/vscode/client.js", "--allow-overwrite").directory(project.projectDir).start().waitFor()

        if (Files.exists(extension.logoPath)) {
            Files.copy(extension.logoPath, Paths.get(root, "build", "vscode", "logo.png"), StandardCopyOption.REPLACE_EXISTING)
        }

        if (Files.exists(extension.fileIconPath)) {
            Files.copy(extension.fileIconPath, Paths.get(root, "build", "vscode", "fileIcon.png"), StandardCopyOption.REPLACE_EXISTING)
        }

        if (!Files.exists(Paths.get(project.projectDir.toString(), "build", "vscode", "LICENSE.md"))) {
            Files.writeString(Paths.get(project.projectDir.toString(), "build", "vscode", "LICENSE.md"), "Copyright Strumenta SRL")
        }
        ProcessBuilder("npx", "vsce", "package", "--allow-missing-repository").directory(Paths.get(project.projectDir.toString(), "build", "vscode").toFile()).redirectErrorStream(true).start().waitFor()
    }
}
