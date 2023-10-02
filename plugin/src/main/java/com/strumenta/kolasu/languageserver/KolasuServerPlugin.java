package com.strumenta.kolasu.languageserver;

import java.util.ArrayList;

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
	}

	private void addInstallDependenciesTask(Project project)
	{
		var installDependenciesTask = project.getTasks().create("installLanguageClientDependencies");
		installDependenciesTask.setGroup("kolasuServer");
		installDependenciesTask.setDescription("Install node dependencies under build/node_modules");

		var actions = new ArrayList<Action<? super Task>>();
		actions.add(task ->
		{
			var npm = new ProcessBuilder("npm", "install", "--prefix", "build", "vscode-languageclient");
			try
			{
				npm.start();
			}
			catch (Exception exception)
			{
				System.err.println(exception.getMessage());
			}
		});
		installDependenciesTask.setActions(actions);
	}
}
