package com.strumenta.languageserver

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class LanguageServerPlugin : Plugin<Project?> {

    override fun apply(project: Project?) {
        addCreateVscodeExtensionTask(project!!)
        addLaunchCodiumTask(project)
        addLaunchCodeTask(project)
    }

    private fun addLaunchCodeTask(project: Project) {
        project.tasks.create("launchCode").apply {
            this.group = "language server";
            this.description = "Launch Visual Studio Code with the language server installed";
            this.actions = listOf(Action { _ ->
                try {
                    ProcessBuilder("code", "--extensionDevelopmentPath", "${project.projectDir}/build/extension", "${project.projectDir}/examples").start().waitFor()
                } catch (exception: Exception) {
                    System.err.println(exception.message)
                }
            })
            this.dependsOn(project.tasks.getByName("createVscodeExtension"))
        };
    }

    private fun addLaunchCodiumTask(project: Project) {
        project.tasks.create("launchCodium").apply {
            this.group = "language server";
            this.description = "Launch codium with the language server installed";
            this.actions = listOf(Action { _ ->
                try {
                    ProcessBuilder("codium", "--extensionDevelopmentPath", "${project.projectDir}/build/extension", "${project.projectDir}/examples").start().waitFor()
                } catch (exception: Exception) {
                    System.err.println(exception.message)
                }
            })
            this.dependsOn.add(project.tasks.getByName("createVscodeExtension"))
        };
    }

    private fun addCreateVscodeExtensionTask(project: Project) {
        project.tasks.create("createVscodeExtension").apply {
            this.group = "language server";
            this.description = "Create language server extension folder for vscode under build/extension";
            this.actions = listOf(Action { _ ->
                try {
                    val name = "rpgle"
                    Files.createDirectories(Paths.get("build", "extension"))
                    Files.writeString(Paths.get("build", "extension", "package.json"), """
                        {
                            "name": "$name",
                            "version": "0.0.0",
                            "contributes":
                            {
                                "languages":
                                [
                                    {"id": "$name", "extensions": [".$name"]}
                                ]
                            },
                            "engines": {"vscode": "^1.52.0"},
                            "activationEvents": ["onLanguage:$name"],
                            "main": "client.js"
                        }
                        """.trimIndent())
                    Files.writeString(Paths.get("build", "extension", "client.js"), """
                        let {LanguageClient} = require("../node_modules/vscode-languageclient/node");
    
                        async function activate (context)
                        {
                            let productionServer = {run: {command: "java", args: ["-jar", context.asAbsolutePath("server.jar")]}};
    
                            let languageClient = new LanguageClient("$name", "$name language server", productionServer, {documentSelector: ["$name"]});
                            await languageClient.start();
    
                            context.subscriptions.push(languageClient);
                        }
    
                        module.exports = {activate};
                    """.trimIndent())

                    Files.copy(Paths.get("build", "libs", project.name+".jar"), Paths.get("build", "extension", "server.jar"), StandardCopyOption.REPLACE_EXISTING)

                    ProcessBuilder("npm", "install", "--prefix", "build", "vscode-languageclient").start().waitFor()
                    ProcessBuilder("npx", "esbuild", "build/extension/client.js", "--bundle", "--external:vscode", "--format=cjs", "--platform=node", "--outfile=build/extension/client.js", "--allow-overwrite").start().waitFor()
                } catch (exception: Exception) {
                    System.err.println(exception.message)
                }
            })
            this.dependsOn.add(project.tasks.getByName("shadowJar"))
        }
    }
}
