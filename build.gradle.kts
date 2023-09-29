plugins {
	id("org.jetbrains.kotlin.jvm") version "1.8.22"
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("com.strumenta.kolasu:kolasu-core:1.5.31")
	implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.1")
}

group = "com.strumenta"
version = "0.0.0"
