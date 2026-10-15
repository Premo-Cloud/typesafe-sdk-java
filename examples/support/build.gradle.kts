// Test support shared by the examples: an in-process stand-in for api.typesafe.ai.
apply(plugin = "java-library")

dependencies {
    "api"(project(":typesafe-sdk"))
}
