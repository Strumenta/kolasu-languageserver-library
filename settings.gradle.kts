include("library")
include("testing")
include("plugin")

rootProject.name="kolasu-languageserver"
project(":library").name = "kolasu-languageserver-library"
project(":testing").name = "kolasu-languageserver-testing"
project(":plugin").name = "kolasu-languageserver-plugin"
