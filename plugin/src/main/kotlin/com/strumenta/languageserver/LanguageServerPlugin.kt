package com.strumenta.languageserver

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformJvmPlugin
import org.jetbrains.kotlin.konan.file.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*

open class LanguageServerExtension {
    var editor: String = ""
    var language: String = ""
    var fileExtensions: List<String> = listOf()
    var serverJarPath: Path = Paths.get("")
    var examplesPath: Path = Paths.get("")
    var textmateGrammarPath: Path = Paths.get("")
    var textmateGrammarScope: String = ""
    var logoPath: Path = Paths.get("")
    var fileIconPath: Path = Paths.get("")
    var languageClientPath: Path = Paths.get("")
    var packageDefinitionPath: Path = Paths.get("")
    var licensePath: Path = Paths.get("")
    var symbolResolverPath: Path = Paths.get("")
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
        project.dependencies.add("implementation", "org.apache.lucene:lucene-core:9.8.0")
        project.dependencies.add("implementation", "org.apache.lucene:lucene-core:9.8.0")
        project.dependencies.add("implementation", "org.apache.lucene:lucene-queryparser:9.8.0")
        project.dependencies.add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit:1.8.22")

        val language = project.rootProject.name

        val shadowJar = project.tasks.getByName("shadowJar") as ShadowJar
        shadowJar.manifest.attributes["Main-Class"] = "com.strumenta.$language.languageserver.MainKt"
        shadowJar.manifest.attributes["Multi-Release"] = "true"
        shadowJar.manifest.attributes["Class-Path"] = "lucene-core-9.8.0.jar lucene-codecs-9.8.0.jar"
        shadowJar.excludes.add("org/apache/lucene/**")
        shadowJar.archiveFileName.set("$language.jar")

        project.extensions.add("languageServer", LanguageServerExtension::class.java)
        extension = project.extensions.getByType(LanguageServerExtension::class.java)
        extension.language = language
        extension.fileExtensions = mutableListOf(language)
        extension.editor = "code"
        extension.serverJarPath = Paths.get(project.projectDir.toString(), "build", "libs", "$language.jar")
        extension.examplesPath = Paths.get(project.rootDir.toString(), "examples")

        extension.textmateGrammarPath = Paths.get(project.projectDir.toString(), "src", "main", "resources", "grammar.tmLanguage")
        extension.textmateGrammarScope = "main"
        extension.logoPath = Paths.get(project.projectDir.toString(), "src", "main", "resources", "logo.png")
        extension.fileIconPath = Paths.get(project.projectDir.toString(), "src", "main", "resources", "fileIcon.png")
        extension.languageClientPath = Paths.get(project.projectDir.toString(), "src", "main", "resources", "client.js")
        extension.packageDefinitionPath = Paths.get(project.projectDir.toString(), "src", "main", "resources", "package.json")
        extension.licensePath = Paths.get(project.projectDir.toString(), "src", "main", "resources", "LICENSE.md")

        addCreateVscodeExtensionTask(project)
        addLaunchVscodeEditorTask(project)
    }

    private fun addLaunchVscodeEditorTask(project: Project) {
        project.tasks.create("launchVscodeEditor").apply {
            group = "language server"
            description = "Launch the configured vscode editor with the language server installed (defaults to code)"
            actions = listOf(Action { _ -> try { launchVscodeEditor(project) } catch (exception: Exception) { System.err.println(exception.message) } })
            dependsOn(project.tasks.getByName("createVscodeExtension"))
        }
    }

    private fun launchVscodeEditor(project: Project) {
        ProcessBuilder(extension.editor, "--extensionDevelopmentPath", "${project.projectDir}${File.separator}build${File.separator}vscode", extension.examplesPath.toString()).directory(project.projectDir).start().waitFor()
    }

    private fun addCreateVscodeExtensionTask(project: Project) {
        project.tasks.create("createVscodeExtension").apply {
            group = "language server"
            description = "Create language server extension folder for vscode under build/vscode"
            actions = listOf(Action { _ -> try { createVscodeExtension(project) } catch (exception: Exception) { System.err.println(exception.message) } })
            dependsOn.add(project.tasks.getByName("shadowJar"))
        }
    }

    private fun createVscodeExtension(project: Project) {
        val root = project.projectDir.toString()
        val language = extension.language

        val shadowJar = project.tasks.getByName("shadowJar") as ShadowJar
        val entryPoint = shadowJar.manifest.attributes["Main-Class"] as String
        if (entryPoint == "com.strumenta.$language.languageserver.MainKt") {
            if (!Files.exists(Paths.get(root, "src", "main", "kotlin", "com", "strumenta", language, "languageserver", "Main.kt"))) {
                Files.createDirectories(Paths.get(root, "src", "main", "kotlin", "com", "strumenta", language, "languageserver"))
                Files.writeString(
                    Paths.get(root, "src", "main", "kotlin", "com", "strumenta", language, "languageserver", "Main.kt"),
                    """
                    package com.strumenta.$language.languageserver
        
                    import com.strumenta.$language.parser.${language.capitalized()}KolasuParser
                    import com.strumenta.kolasu.languageserver.library.KolasuServer
                    import com.strumenta.kolasu.languageserver.library.ScopelessSymbolResolver
        
                    fun main(arguments: Array<String>) {
                        val language = arguments[0]
                        val fileExtensions = arguments[1].split(",")
                        val symbolResolver = ScopelessSymbolResolver()
                        
                        val parser = ${language.capitalized()}KolasuParser()
                        val server = KolasuServer(parser, language, fileExtensions, symbolResolver)
                        server.startCommunication()
                    }
                    """.trimIndent()
                )
            }
        }

        Files.createDirectories(Paths.get(root, "build", "vscode"))

        if (Files.exists(extension.packageDefinitionPath)) {
            Files.copy(extension.packageDefinitionPath, Paths.get(root, "build", "vscode", "package.json"), StandardCopyOption.REPLACE_EXISTING)
        } else {
            var grammars = ""
            if (Files.exists(extension.textmateGrammarPath)) {
                grammars = """
                ,
                "grammars":
                [
                    {"language": "$language", "scopeName": "${extension.textmateGrammarScope}", "path": "./grammar.tmLanguage"}
                ]
                """.trimIndent()
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
                    "name": "${language.lowercase(Locale.getDefault())}",
                    "version": "0.0.0",$logo
                    "publisher": "strumenta",
                    "contributes":
                    {
                        "languages":
                        [
                            {"id": "$language", "extensions": ["${extension.fileExtensions.joinToString("\", \""){ ".$it" }}"]$fileIcon}
                        ],
                        "configuration": {
                            "title": "${language.capitalized()}",
                            "properties": {
                                "$language.showParsingErrors": {
                                  "type": "boolean",
                                  "default": true,
                                  "description": "Show parsing errors produced by ANTLR."
                                },
                                "$language.showASTWarnings": {
                                  "type": "boolean",
                                  "default": false,
                                  "description": "Show warnings for ast inconsistencies."
                                },
                                "$language.showLeafPositions": {
                                  "type": "boolean",
                                  "default": false,
                                  "description": "Show all leaves' positions."
                                }
                            }
                        }$grammars
                    },
                    "engines": {"vscode": "^1.52.0"},
                    "activationEvents": ["onLanguage:$language"],
                    "main": "client.js"
                }
                """.trimIndent()
            )
        }

        if (Files.exists(extension.languageClientPath)) {
            Files.copy(extension.languageClientPath, Paths.get(root, "build", "vscode", "client.js"), StandardCopyOption.REPLACE_EXISTING)
        } else {
            Files.writeString(
                Paths.get(root, "build", "vscode", "client.js"),
                """
                let {LanguageClient} = require("../node_modules/vscode-languageclient/node");
        
                async function activate (context)
                {
                    let productionServer = {run: {command: "java", args: ["-jar", context.asAbsolutePath("server.jar"), "$language", "${extension.fileExtensions.joinToString(",")}"]}};
        
                    let languageClient = new LanguageClient("$language", "$language language server", productionServer, {documentSelector: ["$language"]});
                    await languageClient.start();
        
                    context.subscriptions.push(languageClient);
                }
        
                module.exports = {activate};
                """.trimIndent()
            )
        }

        Files.copy(extension.serverJarPath, Paths.get(root, "build", "vscode", "server.jar"), StandardCopyOption.REPLACE_EXISTING)

        ProcessBuilder("npm", "install", "--prefix", "build", "vscode-languageclient").directory(project.projectDir).start().waitFor()
        ProcessBuilder("npx", "esbuild", "build/vscode/client.js", "--bundle", "--external:vscode", "--format=cjs", "--platform=node", "--outfile=build/vscode/client.js", "--allow-overwrite").directory(project.projectDir).start().waitFor()

        if (Files.exists(extension.textmateGrammarPath)) {
            Files.copy(extension.textmateGrammarPath, Paths.get(root, "build", "vscode", "grammar.tmLanguage"), StandardCopyOption.REPLACE_EXISTING)
        }

        if (Files.exists(extension.logoPath)) {
            Files.copy(extension.logoPath, Paths.get(root, "build", "vscode", "logo.png"), StandardCopyOption.REPLACE_EXISTING)
        }

        if (Files.exists(extension.fileIconPath)) {
            Files.copy(extension.fileIconPath, Paths.get(root, "build", "vscode", "fileIcon.png"), StandardCopyOption.REPLACE_EXISTING)
        }

        if (Files.exists(extension.licensePath)) {
            Files.copy(extension.licensePath, Paths.get(root, "build", "vscode", "LICENSE.md"))
        } else {
            Files.writeString(Paths.get(root, "build", "vscode", "LICENSE.md"), "Copyright Strumenta SRL")
        }
        ProcessBuilder("npx", "vsce", "package", "--allow-missing-repository").directory(Paths.get(project.projectDir.toString(), "build", "vscode").toFile()).redirectErrorStream(true).start().waitFor()
    }
}
