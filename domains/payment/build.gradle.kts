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
}
