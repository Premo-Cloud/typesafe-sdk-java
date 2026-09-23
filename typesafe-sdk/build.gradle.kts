description = "Community Java SDK for Jev and the TypeSafe System One API"

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.15.4")
    api("org.jspecify:jspecify:1.0.1")
    api("org.slf4j:slf4j-api:2.0.16")

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // A binding with a programmable appender, so the tests can assert on what was logged.
    testImplementation("ch.qos.logback:logback-classic:1.5.18")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

mavenPublishing {
    coordinates(group.toString(), "typesafe-sdk", version.toString())

    pom {
        name.set("TypeSafe SDK for Java (community)")
        description.set(project.description)
    }
}
