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
    lateinit var editor: String
    lateinit var language: String
    lateinit var version: String
    lateinit var publisher: String
    lateinit var fileExtensions: List<String>
    lateinit var textmateGrammarScope: String
    lateinit var entryPointPath: Path
    lateinit var serverJarPath: Path
    lateinit var examplesPath: Path
    lateinit var textmateGrammarPath: Path
    lateinit var logoPath: Path
    lateinit var fileIconPath: Path
    lateinit var languageClientPath: Path
    lateinit var packageDefinitionPath: Path
    lateinit var licensePath: Path
    lateinit var outputPath: Path
}

class LanguageServerPlugin : Plugin<Project?> {

    private lateinit var extension: LanguageServerExtension

    override fun apply(project: Project?) {
        if (project == null) return

        project.pluginManager.apply(ShadowPlugin::class.java)
        project.pluginManager.apply(KotlinPlatformJvmPlugin::class.java)

        project.repositories.add(project.repositories.mavenCentral())
        project.repositories.add(project.repositories.mavenLocal())

        if (project.rootProject.subprojects.any { it.name == "ast" }) {
            project.dependencies.add("implementation", project.dependencies.project(mapOf("path" to ":ast")))
        }
        project.dependencies.add("implementation", "com.strumenta:language-server:0.0.0")
        project.dependencies.add("implementation", "com.strumenta.kolasu:kolasu-core:1.5.31")
        project.dependencies.add("implementation", "org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.1")
        project.dependencies.add("implementation", "org.apache.lucene:lucene-core:9.8.0")
        project.dependencies.add("implementation", "org.apache.lucene:lucene-codecs:9.8.0")
        project.dependencies.add("implementation", "org.apache.lucene:lucene-queryparser:9.8.0")
        project.dependencies.add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit:1.8.22")

        val projectPath = project.projectDir.toString()
        val language = project.rootProject.name
        val resources = listOf(projectPath, "src", "main", "resources")

        project.extensions.add("languageServer", LanguageServerExtension::class.java)
        extension = project.extensions.getByType(LanguageServerExtension::class.java)
        extension.language = language
        extension.version = "0.0.0"
        extension.publisher = "strumenta"
        extension.fileExtensions = mutableListOf(language)
        extension.editor = "code"
        extension.textmateGrammarScope = "main"
        extension.serverJarPath = Paths.get(projectPath, "build", "libs", "$language.jar")
        extension.examplesPath = Paths.get(project.rootDir.toString(), "examples")
        extension.entryPointPath = Paths.get(projectPath, "src", "main", "kotlin", "com", "strumenta", language, "languageserver", "Main.kt")
        extension.textmateGrammarPath = Paths.get(projectPath, "src", "main", "resources", "grammar.tmLanguage")
        extension.logoPath = Paths.get(projectPath, "src", "main", "resources", "logo.png")
        extension.fileIconPath = Paths.get(projectPath, "src", "main", "resources", "fileIcon.png")
        extension.languageClientPath = Paths.get(projectPath, "src", "main", "resources", "client.js")
        extension.packageDefinitionPath = Paths.get(projectPath, "src", "main", "resources", "package.json")
        extension.licensePath = Paths.get(projectPath, "src", "main", "resources", "LICENSE.md")
        extension.outputPath = Paths.get(projectPath, "build", "vscode")

        val shadowJar = project.tasks.getByName("shadowJar") as ShadowJar
        shadowJar.manifest.attributes["Main-Class"] = "com.strumenta.$language.languageserver.MainKt"
        shadowJar.manifest.attributes["Multi-Release"] = "true"
        shadowJar.manifest.attributes["Class-Path"] = "lucene-core-9.8.0.jar"
        shadowJar.archiveFileName.set("$language.jar")

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
            dependsOn(project.tasks.getByName("shadowJar"))
            inputs.files(extension.entryPointPath, extension.textmateGrammarPath, extension.logoPath, extension.fileIconPath, extension.languageClientPath, extension.packageDefinitionPath, extension.licensePath).optional()
            outputs.dirs(extension.outputPath)
        }
    }

    private fun createVscodeExtension(project: Project) {
        val shadowJar = project.tasks.getByName("shadowJar") as ShadowJar
        val entryPoint = shadowJar.manifest.attributes["Main-Class"] as String
        if (entryPoint == "com.strumenta.${extension.language}.languageserver.MainKt") {
            if (!Files.exists(extension.entryPointPath)) {
                Files.createDirectories(extension.entryPointPath.parent)
                Files.writeString(
                    extension.entryPointPath,
                    """
                    package com.strumenta.${extension.language}.languageserver
        
                    import com.strumenta.${extension.language}.parser.${extension.language.capitalized()}KolasuParser
                    import com.strumenta.kolasu.languageserver.library.KolasuServer
                    import com.strumenta.kolasu.languageserver.library.ScopelessSymbolResolver
        
                    fun main(arguments: Array<String>) {
                        val language = arguments[0]
                        val fileExtensions = arguments[1].split(",")
                        val symbolResolver = ScopelessSymbolResolver()
                        
                        val parser = ${extension.language.capitalized()}KolasuParser()
                        val server = KolasuServer(parser, language, fileExtensions, symbolResolver)
                        server.startCommunication()
                    }
                    """.trimIndent()
                )
            }
        }

        Files.createDirectories(extension.outputPath)

        if (Files.exists(extension.packageDefinitionPath)) {
            Files.copy(extension.packageDefinitionPath, Paths.get(extension.outputPath.toString(), "package.json"), StandardCopyOption.REPLACE_EXISTING)
        } else {
            var grammars = ""
            if (Files.exists(extension.textmateGrammarPath)) {
                grammars = """
                ,
                "grammars":
                [
                    {"language": "${extension.language}", "scopeName": "${extension.textmateGrammarScope}", "path": "./grammar.tmLanguage"}
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
                Paths.get(extension.outputPath.toString(), "package.json"),
                """
                {
                    "name": "${extension.language.lowercase(Locale.getDefault())}",
                    "version": "${extension.version}",$logo
                    "publisher": "${extension.publisher}",
                    "contributes":
                    {
                        "languages":
                        [
                            {"id": "${extension.language}", "extensions": ["${extension.fileExtensions.joinToString("\", \""){ ".$it" }}"]$fileIcon}
                        ],
                        "configuration": {
                            "title": "${extension.language.capitalized()}",
                            "properties": {
                                "${extension.language}.showParsingErrors": {
                                  "type": "boolean",
                                  "default": true,
                                  "description": "Show parsing errors produced by ANTLR."
                                },
                                "${extension.language}.showASTWarnings": {
                                  "type": "boolean",
                                  "default": false,
                                  "description": "Show warnings for ast inconsistencies."
                                },
                                "${extension.language}.showLeafPositions": {
                                  "type": "boolean",
                                  "default": false,
                                  "description": "Show all leaves' positions."
                                }
                            }
                        }$grammars
                    },
                    "engines": {"vscode": "^1.52.0"},
                    "activationEvents": ["onLanguage:${extension.language}"],
                    "main": "client.js"
                }
                """.trimIndent()
            )
        }

        if (Files.exists(extension.languageClientPath)) {
            Files.copy(extension.languageClientPath, Paths.get(extension.outputPath.toString(), "client.js"), StandardCopyOption.REPLACE_EXISTING)
        } else {
            Files.writeString(
                Paths.get(extension.outputPath.toString(), "client.js"),
                """
                let {LanguageClient} = require("../node_modules/vscode-languageclient/node");
        
                async function activate (context)
                {
                    let productionServer = {run: {command: "java", args: ["-jar", context.asAbsolutePath("server.jar"), "${extension.language}", "${extension.fileExtensions.joinToString(",")}"]}};
        
                    let languageClient = new LanguageClient("${extension.language}", "${extension.language} language server", productionServer, {documentSelector: ["${extension.language}"]});
                    await languageClient.start();
        
                    context.subscriptions.push(languageClient);
                }
        
                module.exports = {activate};
                """.trimIndent()
            )
        }

        Files.copy(extension.serverJarPath, Paths.get(extension.outputPath.toString(), "server.jar"), StandardCopyOption.REPLACE_EXISTING)

        ProcessBuilder("npm", "install", "--prefix", "build", "vscode-languageclient").directory(project.projectDir).start().waitFor()
        ProcessBuilder("npx", "esbuild", "build/vscode/client.js", "--bundle", "--external:vscode", "--format=cjs", "--platform=node", "--outfile=build/vscode/client.js", "--allow-overwrite").directory(project.projectDir).start().waitFor()

        if (Files.exists(extension.textmateGrammarPath)) {
            Files.copy(extension.textmateGrammarPath, Paths.get(extension.outputPath.toString(), "grammar.tmLanguage"), StandardCopyOption.REPLACE_EXISTING)
        }

        if (Files.exists(extension.logoPath)) {
            Files.copy(extension.logoPath, Paths.get(extension.outputPath.toString(), "logo.png"), StandardCopyOption.REPLACE_EXISTING)
        }

        if (Files.exists(extension.fileIconPath)) {
            Files.copy(extension.fileIconPath, Paths.get(extension.outputPath.toString(), "fileIcon.png"), StandardCopyOption.REPLACE_EXISTING)
        }

        if (Files.exists(extension.licensePath)) {
            Files.copy(extension.licensePath, Paths.get(extension.outputPath.toString(), "LICENSE.md"))
        } else {
            Files.writeString(Paths.get(extension.outputPath.toString(), "LICENSE.md"), "Copyright Strumenta SRL")
        }
        ProcessBuilder("curl", "https://repo1.maven.org/maven2/org/apache/lucene/lucene-core/9.8.0/lucene-core-9.8.0.jar", "-o", Paths.get(extension.outputPath.toString(), "lucene-core-9.8.0.jar").toString()).start().waitFor()

        ProcessBuilder("npx", "vsce", "package", "--allow-missing-repository").directory(extension.outputPath.toFile()).start().waitFor()
    }
}
