plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.jpa")
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":libs:common"))
    api(project(":libs:pg-client"))
    implementation(project(":domains:merchant"))
    implementation(project(":libs:persistence"))
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.4.0")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-context")
}
