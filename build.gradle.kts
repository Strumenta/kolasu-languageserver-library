plugins {
    alias(libs.plugins.release)
}

allprojects {
    group = "com.strumenta.kolasu.languageserver"
}

subprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

release {
    buildTasks.set(listOf(":kolasu-languageserver-library:publish", ":kolasu-languageserver-testing:publish", ":kolasu-languageserver-plugin:publishPlugins"))
    git {
        requireBranch.set("")
        pushToRemote.set("origin")
    }
}
