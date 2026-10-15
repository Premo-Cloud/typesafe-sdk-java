plugins {
    application
}

dependencies {
    // in your project: implementation("io.github.premo-cloud:typesafe-sdk:0.6.0")
    implementation(project(":typesafe-sdk"))
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

application {
    mainClass.set("io.github.premocloud.typesafe.examples.batch.AsyncBatch")
}
