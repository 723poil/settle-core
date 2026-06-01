plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    api(project(":libs:common"))
    api(project(":libs:pg-client"))
}
