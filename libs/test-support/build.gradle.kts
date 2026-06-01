plugins {
    `java-library`
    kotlin("jvm")
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":libs:common"))
    api("org.springframework.boot:spring-boot-testcontainers")
    api("org.testcontainers:junit-jupiter")
    api("org.testcontainers:postgresql")
}
