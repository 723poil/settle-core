plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    api(project(":libs:common"))
    implementation(project(":domains:merchant"))
    implementation(project(":domains:payment"))
}
