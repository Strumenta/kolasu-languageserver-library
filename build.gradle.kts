plugins {
    id("net.researchgate.release") version "3.0.2"
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
