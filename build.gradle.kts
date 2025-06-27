plugins {
    alias(libs.plugins.release)
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.mavenPublish) apply false
}

allprojects {
    group = "com.strumenta.kolasu.languageserver"
}

release {
    buildTasks.set(listOf(":kolasu-languageserver-library:publish", ":kolasu-languageserver-testing:publish", ":kolasu-languageserver-plugin:publishPlugins"))
    git {
        requireBranch.set("")
        pushToRemote.set("origin")
    }
}
