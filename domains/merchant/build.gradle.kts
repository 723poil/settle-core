plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.jpa")
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":libs:common"))
    implementation(project(":libs:persistence"))
}
