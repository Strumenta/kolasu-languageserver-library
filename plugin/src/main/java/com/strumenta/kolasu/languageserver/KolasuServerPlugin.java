package com.strumenta.kolasu.languageserver;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;

public class KolasuServerPlugin implements Plugin<Project>
{
	@Override
	public void apply(Project project)
	{
		addInstallDependenciesTask(project);
		addCreateExtensionFolderTask(project);
	}

	private void addInstallDependenciesTask(Project project)
	{
		var installDependenciesTask = project.getTasks().create("installLanguageClientDependencies");
		installDependenciesTask.setGroup("kolasuServer");
		installDependenciesTask.setDescription("Install node dependencies under build/node_modules");

		var actions = new ArrayList<Action<? super Task>>();
		actions.add(task -> {
			var npm = new ProcessBuilder("npm", "install", "--prefix", "build", "vscode-languageclient");
			try {
				npm.start();
			} catch (Exception exception) {
				System.err.println(exception.getMessage());
			}
		});
		installDependenciesTask.setActions(actions);
	}

	private void addCreateExtensionFolderTask(Project project)
	{
		var createExtensionFolderTask = project.getTasks().create("createExtensionFolder");
		createExtensionFolderTask.setGroup("kolasuServer");
		createExtensionFolderTask.setDescription("Create language server extension folder for vscode under build/extension");

		var actions = new ArrayList<Action<? super Task>>();
		actions.add(task ->
		{
			try
			{
				var languageName = project.getName().replace("kolasu-", "").replace("-languageserver", "");
				Files.createDirectories(Paths.get("build", "extension"));
				var lines = new ArrayList<String>();
				Files.write(Paths.get("build", "extension", "package.json"), List.of(languageName), StandardOpenOption.CREATE);
			}
			catch (Exception exception)
			{
				System.err.println(exception.getMessage());
			}
		});
		createExtensionFolderTask.setActions(actions);
	}
}
