plugins {
    id("net.researchgate.release") version "3.0.2"
}

allprojects {
    group = "com.strumenta.kolasu.languageserver"
}

//
//release {
//    buildTasks.set(listOf(":publish"))
//    git {
//        requireBranch.set("")
//        pushToRemote.set("origin")
//    }
//}
