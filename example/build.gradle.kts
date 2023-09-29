plugins {
	id("org.jetbrains.kotlin.jvm") version "1.8.22"
	id("greeting-plugin") version "0.0.0"
}

repositories {
	mavenCentral()
	mavenLocal()
}

dependencies {
	implementation("com.strumenta.kolasu:kolasu-core:1.5.31")
	implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.1")

	implementation("com.strumenta:kolasu-languageserver-library:0.0.0")
	implementation("com.strumenta.langmodules.kolasu-entities-languageserver:ast:0.0.1-SNAPSHOT")
}
