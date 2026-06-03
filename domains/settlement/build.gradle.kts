plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.jpa")
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":libs:common"))
    implementation(project(":domains:merchant"))
    implementation(project(":domains:payment"))
    implementation(project(":libs:persistence"))
}
