package com.strumenta.kolasu.languageserver.plugin

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformJvmPlugin
import org.jetbrains.kotlin.konan.file.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*

class LanguageServerPlugin : Plugin<Project?> {

    private lateinit var configuration: Configuration

    override fun apply(project: Project?) {
        if (project == null) return

        project.pluginManager.apply(ShadowPlugin::class.java)
        project.pluginManager.apply(KotlinPlatformJvmPlugin::class.java)

        project.repositories.add(project.repositories.mavenCentral())

        if (project.rootProject.subprojects.any { it.name == "ast" }) {
            project.dependencies.add("implementation", project.dependencies.project(mapOf("path" to ":ast")))
        }
        project.dependencies.add("implementation", "com.strumenta.kolasu:language-server:1.0.0")
        project.dependencies.add("implementation", "com.strumenta.kolasu:kolasu-core:1.5.31")
        project.dependencies.add("implementation", "org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.1")
        project.dependencies.add("implementation", "org.apache.lucene:lucene-core:9.8.0")
        project.dependencies.add("implementation", "org.apache.lucene:lucene-codecs:9.8.0")
        project.dependencies.add("implementation", "org.apache.lucene:lucene-queryparser:9.8.0")

        project.dependencies.add("testImplementation", "com.strumenta.kolasu:language-server-testing:1.0.0")
        project.dependencies.add("testImplementation", "org.junit.jupiter:junit-jupiter:5.+")

        val projectPath = project.projectDir.toString()
        val language = project.rootProject.name

        project.extensions.add("languageServer", Configuration::class.java)
        configuration = project.extensions.getByType(Configuration::class.java)
        configuration.language = language
        configuration.version = "0.0.0"
        configuration.publisher = "strumenta"
        configuration.repository = "https://github.com/Strumenta/kolasu-$language-parser"
        configuration.fileExtensions = mutableListOf(language)
        configuration.editor = "code"
        configuration.textmateGrammarScope = "main"
        configuration.serverJarPath = Paths.get(projectPath, "build", "libs", "$language.jar")
        configuration.examplesPath = Paths.get(project.rootDir.toString(), "examples")
        configuration.entryPointPath = Paths.get(projectPath, "src", "main", "kotlin", "com", "strumenta", language, "languageserver", "Main.kt")
        configuration.textmateGrammarPath = Paths.get(projectPath, "src", "main", "resources", "grammar.tmLanguage")
        configuration.logoPath = Paths.get(projectPath, "src", "main", "resources", "logo.png")
        configuration.fileIconPath = Paths.get(projectPath, "src", "main", "resources", "fileIcon.png")
        configuration.languageClientPath = Paths.get(projectPath, "src", "main", "resources", "client.js")
        configuration.packageDefinitionPath = Paths.get(projectPath, "src", "main", "resources", "package.json")
        configuration.licensePath = Paths.get(projectPath, "src", "main", "resources", "LICENSE.md")
        configuration.outputPath = Paths.get(projectPath, "build", "vscode")

        val shadowJar = project.tasks.getByName("shadowJar") as ShadowJar
        shadowJar.manifest.attributes["Main-Class"] = "com.strumenta.$language.languageserver.MainKt"
        shadowJar.manifest.attributes["Multi-Release"] = "true"
        shadowJar.manifest.attributes["Class-Path"] = "lucene-core-9.8.0.jar lucene-codecs-9.8.0.jar"
        shadowJar.archiveFileName.set("$language.jar")
        shadowJar.excludes.add("org/apache/lucene/**/*")

        val testTask = project.tasks.getByName("test") as org.gradle.api.tasks.testing.Test
        testTask.useJUnitPlatform()

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
        ProcessBuilder(configuration.editor, "--extensionDevelopmentPath", "${project.projectDir}${File.separator}build${File.separator}vscode", configuration.examplesPath.toString()).directory(project.projectDir).start().waitFor()
    }

    private fun addCreateVscodeExtensionTask(project: Project) {
        project.tasks.create("createVscodeExtension").apply {
            group = "language server"
            description = "Create language server extension folder for vscode under build/vscode"
            actions = listOf(Action { _ -> try { createVscodeExtension(project) } catch (exception: Exception) { System.err.println(exception.message) } })
            dependsOn(project.tasks.getByName("shadowJar"))
            inputs.files(configuration.entryPointPath, configuration.textmateGrammarPath, configuration.logoPath, configuration.fileIconPath, configuration.languageClientPath, configuration.packageDefinitionPath, configuration.licensePath).optional()
            outputs.dirs(configuration.outputPath)
        }
    }

    fun isWindows(): Boolean {
        return System.getProperty("os.name").toLowerCase().contains("win")
    }
    private fun createVscodeExtension(project: Project) {
        val shadowJar = project.tasks.getByName("shadowJar") as ShadowJar
        val entryPoint = shadowJar.manifest.attributes["Main-Class"] as String
        if (entryPoint == "com.strumenta.${configuration.language}.languageserver.MainKt") {
            if (!Files.exists(configuration.entryPointPath)) {
                Files.createDirectories(configuration.entryPointPath.parent)
                Files.writeString(
                    configuration.entryPointPath,
                    """
                    package com.strumenta.${configuration.language}.languageserver
        
                    import com.strumenta.${configuration.language}.parser.${configuration.language.capitalized()}KolasuParser
                    import com.strumenta.kolasu.languageserver.KolasuServer
        
                    fun main() {
                        val parser = ${configuration.language.capitalized()}KolasuParser()
                        
                        val server = KolasuServer(parser, "${configuration.language}", listOf(${configuration.fileExtensions.joinToString(",") { "\"$it\"" }}))
                        server.startCommunication()
                    }
                    """.trimIndent()
                )
            }
        }

        Files.createDirectories(configuration.outputPath)

        if (Files.exists(configuration.packageDefinitionPath)) {
            Files.copy(configuration.packageDefinitionPath, Paths.get(configuration.outputPath.toString(), "package.json"), StandardCopyOption.REPLACE_EXISTING)
        } else {
            var grammars = ""
            if (Files.exists(configuration.textmateGrammarPath)) {
                grammars = """
                ,
                "grammars":
                [
                    {"language": "${configuration.language}", "scopeName": "${configuration.textmateGrammarScope}", "path": "./grammar.tmLanguage"}
                ]
                """.trimIndent()
            }

            var logo = ""
            if (Files.exists(configuration.logoPath)) {
                logo = """"icon": "logo.png","""
            }

            var fileIcon = ""
            if (Files.exists(configuration.fileIconPath)) {
                fileIcon = """, "icon": {"dark": "fileIcon.png", "light": "fileIcon.png"}"""
            }

            Files.writeString(
                Paths.get(configuration.outputPath.toString(), "package.json"),
                """
                {
                    "name": "${configuration.language.lowercase(Locale.getDefault())}",
                    "version": "${configuration.version}",$logo
                    "publisher": "${configuration.publisher}",
                    "repository": "${configuration.repository}",
                    "contributes":
                    {
                        "languages":
                        [
                            {"id": "${configuration.language}", "extensions": ["${configuration.fileExtensions.joinToString("\", \""){ ".$it" }}"]$fileIcon}
                        ],
                        "configuration": {
                            "title": "${configuration.language.capitalized()}",
                            "properties": {
                                "${configuration.language}.showParsingErrors": {
                                  "type": "boolean",
                                  "default": true,
                                  "description": "Show parsing errors produced by ANTLR."
                                },
                                "${configuration.language}.showASTWarnings": {
                                  "type": "boolean",
                                  "default": false,
                                  "description": "Show warnings for ast inconsistencies."
                                },
                                "${configuration.language}.showLeafPositions": {
                                  "type": "boolean",
                                  "default": false,
                                  "description": "Show all leaves' positions."
                                }
                            }
                        }$grammars
                    },
                    "engines": {"vscode": "^1.52.0"},
                    "activationEvents": ["onLanguage:${configuration.language}"],
                    "main": "client.js"
                }
                """.trimIndent()
            )
        }

        if (Files.exists(configuration.languageClientPath)) {
            Files.copy(configuration.languageClientPath, Paths.get(configuration.outputPath.toString(), "client.js"), StandardCopyOption.REPLACE_EXISTING)
        } else {
            Files.writeString(
                Paths.get(configuration.outputPath.toString(), "client.js"),
                """
                let {LanguageClient} = require("./node_modules/vscode-languageclient/node");
        
                async function activate (context)
                {
                    let productionServer = {run: {command: "java", args: ["-jar", context.asAbsolutePath("server.jar")]}};
        
                    let languageClient = new LanguageClient("${configuration.language}", "${configuration.language} language server", productionServer, {documentSelector: ["${configuration.language}"]});
                    await languageClient.start();
        
                    context.subscriptions.push(languageClient);
                }
        
                module.exports = {activate};
                """.trimIndent()
            )
        }

        Files.copy(configuration.serverJarPath, Paths.get(configuration.outputPath.toString(), "server.jar"), StandardCopyOption.REPLACE_EXISTING)

        val npm = if (isWindows()) "npm.cmd" else "npm"
        val npx = if (isWindows()) "npx.cmd" else "npx"

        ProcessBuilder(npm, "install", "--prefix", "build/vscode/", "vscode-languageclient").directory(project.projectDir).start().waitFor()
        ProcessBuilder(npx, "esbuild", "build/vscode/client.js", "--bundle", "--external:vscode", "--format=cjs", "--platform=node", "--outfile=build/vscode/client.js", "--allow-overwrite").directory(project.projectDir).start().waitFor()

        if (Files.exists(configuration.textmateGrammarPath)) {
            Files.copy(configuration.textmateGrammarPath, Paths.get(configuration.outputPath.toString(), "grammar.tmLanguage"), StandardCopyOption.REPLACE_EXISTING)
        }

        if (Files.exists(configuration.logoPath)) {
            Files.copy(configuration.logoPath, Paths.get(configuration.outputPath.toString(), "logo.png"), StandardCopyOption.REPLACE_EXISTING)
        }

        if (Files.exists(configuration.fileIconPath)) {
            Files.copy(configuration.fileIconPath, Paths.get(configuration.outputPath.toString(), "fileIcon.png"), StandardCopyOption.REPLACE_EXISTING)
        }

        if (Files.exists(configuration.licensePath)) {
            Files.copy(configuration.licensePath, Paths.get(configuration.outputPath.toString(), "LICENSE.md"))
        } else {
            Files.writeString(Paths.get(configuration.outputPath.toString(), "LICENSE.md"), "Copyright Strumenta SRL")
        }
        ProcessBuilder("curl", "https://repo1.maven.org/maven2/org/apache/lucene/lucene-core/9.8.0/lucene-core-9.8.0.jar", "-o", Paths.get(configuration.outputPath.toString(), "lucene-core-9.8.0.jar").toString()).start().waitFor()
        ProcessBuilder("curl", "https://repo1.maven.org/maven2/org/apache/lucene/lucene-codecs/9.8.0/lucene-codecs-9.8.0.jar", "-o", Paths.get(configuration.outputPath.toString(), "lucene-codecs-9.8.0.jar").toString()).start().waitFor()

        ProcessBuilder(npx, "vsce@2.15", "package").directory(configuration.outputPath.toFile()).start().waitFor()
    }
}
