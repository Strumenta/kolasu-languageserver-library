package com.strumenta.greeting;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class GreetingPlugin implements Plugin<Project>
{
	@Override
	public void apply(Project project)
	{
		project.getTasks().create("greet").doLast(x -> System.out.println("Hello, World!"));
	}
}
