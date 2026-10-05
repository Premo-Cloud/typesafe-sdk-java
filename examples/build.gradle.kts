// Shared setup for the examples: the same toolchain and Java 17 target as the SDK. Examples are never published.
subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
        options.encoding = "UTF-8"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    if (name != "support") {
        dependencies {
            "testImplementation"(project(":examples:support"))
            "testImplementation"(platform("org.junit:junit-bom:5.14.4"))
            "testImplementation"("org.junit.jupiter:junit-jupiter")
            "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        }
    }
}
