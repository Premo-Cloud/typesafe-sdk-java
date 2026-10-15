
plugins {
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
}

configure(listOf(project(":typesafe-sdk"), project(":typesafe-sdk-spring-boot-starter"))) {
    apply(plugin = "java-library")
    apply(plugin = "com.vanniktech.maven.publish")

    group = rootProject.group
    version = rootProject.version

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

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.withType<Jar>().configureEach {
        manifest {
            attributes("Implementation-Title" to project.name, "Implementation-Version" to project.version)
        }
    }

    extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
        publishToMavenCentral()
        signAllPublications()

        pom {
            url.set("https://github.com/Premo-Cloud/typesafe-sdk-java")
            inceptionYear.set("2026")

            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }

            developers {
                developer {
                    id.set("garretpremo")
                    name.set("Garret Premo")
                    url.set("https://github.com/garretpremo")
                }
            }

            scm {
                url.set("https://github.com/Premo-Cloud/typesafe-sdk-java")
                connection.set("scm:git:git://github.com/Premo-Cloud/typesafe-sdk-java.git")
                developerConnection.set("scm:git:ssh://git@github.com/Premo-Cloud/typesafe-sdk-java.git")
            }
        }
    }
}
