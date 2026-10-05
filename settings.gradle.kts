rootProject.name = "typesafe-sdk-java"

include("typesafe-sdk", "typesafe-sdk-spring-boot-starter")

include(
    ":examples:support",
    ":examples:ticket-triage",
    ":examples:typed-routing",
    ":examples:async-batch",
    ":examples:spring-boot",
)
