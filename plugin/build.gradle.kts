plugins {
	id("java-gradle-plugin")
	id("maven-publish")
	id("org.jetbrains.kotlin.jvm") version "1.8.22"
}

repositories {
	mavenCentral()
}

gradlePlugin {
	plugins {
		create("language-server-plugin") {id = "language-server-plugin"; implementationClass = "com.strumenta.languageserver.LanguageServerPlugin"}
	}
}

group = "com.strumenta"
version = "0.0.0"

publishing {
	repositories {mavenLocal()}
	publications { create<MavenPublication>("mavenJava") { from(components["java"]) } }
}
