plugins {
    application
}

val springBootVersion = "3.1.2"

dependencies {
    // in your project: implementation("io.github.premo-cloud:typesafe-sdk-spring-boot-starter:0.6.0")
    implementation(project(":typesafe-sdk-spring-boot-starter"))
    implementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
}

application {
    mainClass.set("io.github.premocloud.typesafe.examples.spring.TicketRoutingApplication")
}
