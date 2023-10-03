plugins {
	id("org.jetbrains.kotlin.jvm") version "1.8.22"
	id("com.github.johnrengelman.shadow") version "7.1.2"
	id("language-server-plugin") version "0.0.0"
}

dependencies {
	implementation("com.strumenta.langmodules.kolasu-entities-languageserver:ast:0.0.1-SNAPSHOT")
}

languageServer {
	language = "Entity"
	extension = "entity"
	shadowJarName = project.name + "-all"
	editor = "codium"
}

tasks.shadowJar {
	manifest {
		attributes("Main-Class" to "com.strumenta.entity.languageserver.EntityServerKt")
	}
}