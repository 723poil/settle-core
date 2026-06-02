plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "settle-core"

include(
    ":apps:api",
    ":apps:scheduler",
    ":domains:merchant",
    ":domains:payment",
    ":domains:settlement",
    ":libs:common",
    ":libs:infra",
    ":libs:pg-client",
    ":libs:persistence",
    ":libs:test-support",
)
