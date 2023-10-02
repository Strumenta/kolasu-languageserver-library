plugins {
	id("java-gradle-plugin")
	id("maven-publish")
}

gradlePlugin {
	plugins {
		create("language-server-plugin") {id = "language-server-plugin"; implementationClass = "com.strumenta.kolasu.languageserver.KolasuServerPlugin"}
	}
}

group = "com.strumenta"
version = "0.0.0"

publishing {
	repositories {mavenLocal()}
	publications { create<MavenPublication>("mavenJava") { from(components["java"]) } }
}
