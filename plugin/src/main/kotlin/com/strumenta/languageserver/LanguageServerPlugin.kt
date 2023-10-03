package com.strumenta.languageserver

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*

open class LanguageServerExtension {
    var editor: String = "code"
    var language: String = "language"
    var extension: String = "extension"
    var shadowJarName: String = ""
}

class LanguageServerPlugin : Plugin<Project?> {

    private var extension: LanguageServerExtension = LanguageServerExtension()

    override fun apply(project: Project?) {
        if (project == null) return

        project.repositories.add(project.repositories.mavenCentral())
        project.repositories.add(project.repositories.mavenLocal())

        project.dependencies.add("implementation", "com.strumenta:language-server:0.0.0")
        project.dependencies.add("implementation", "com.strumenta.kolasu:kolasu-core:1.5.31")
        project.dependencies.add("implementation", "org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.1")

        addCreateVscodeExtensionTask(project)
        addLaunchVscodeEditorTask(project)

        project.extensions.add("languageServer", LanguageServerExtension::class.java)
        extension = project.extensions.getByType(LanguageServerExtension::class.java)
    }

    private fun addLaunchVscodeEditorTask(project: Project) {
        project.tasks.create("launchVscodeEditor").apply {
            this.group = "language server"
            this.description = "Launch the configured vscode editor with the language server installed (defaults to code)"
            this.actions = listOf(
                Action { _ ->
                    try {
                        ProcessBuilder(extension.editor, "--extensionDevelopmentPath", "${project.projectDir}/build/extension", "${project.projectDir}/examples").start().waitFor()
                    } catch (exception: Exception) {
                        System.err.println(exception.message)
                    }
                }
            )
            this.dependsOn(project.tasks.getByName("createVscodeExtension"))
        }
    }

    private fun addCreateVscodeExtensionTask(project: Project) {
        project.tasks.create("createVscodeExtension").apply {
            this.group = "language server"
            this.description = "Create language server extension folder for vscode under build/extension"
            this.actions = listOf(
                Action { _ ->
                    try {
                        val name = extension.language
                        Files.createDirectories(Paths.get("build", "extension"))
                        Files.writeString(
                            Paths.get("build", "extension", "package.json"),
                            """
                        {
                            "name": "${name.lowercase(Locale.getDefault())}",
                            "version": "0.0.0",
                            "contributes":
                            {
                                "languages":
                                [
                                    {"id": "$name", "extensions": [".${extension.extension}"]}
                                ]
                            },
                            "engines": {"vscode": "^1.52.0"},
                            "activationEvents": ["onLanguage:$name"],
                            "main": "client.js"
                        }
                            """.trimIndent()
                        )
                        Files.writeString(
                            Paths.get("build", "extension", "client.js"),
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

                        if (extension.shadowJarName == "") {
                            extension.shadowJarName = project.name
                        }
                        Files.copy(Paths.get("build", "libs", extension.shadowJarName + ".jar"), Paths.get("build", "extension", "server.jar"), StandardCopyOption.REPLACE_EXISTING)

                        ProcessBuilder("npm", "install", "--prefix", "build", "vscode-languageclient").start().waitFor()
                        ProcessBuilder("npx", "esbuild", "build/extension/client.js", "--bundle", "--external:vscode", "--format=cjs", "--platform=node", "--outfile=build/extension/client.js", "--allow-overwrite").start().waitFor()
                    } catch (exception: Exception) {
                        System.err.println(exception.message)
                    }
                }
            )
            this.dependsOn.add(project.tasks.getByName("shadowJar"))
        }
    }
}
