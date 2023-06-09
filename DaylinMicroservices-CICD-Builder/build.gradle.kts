plugins {
    kotlin("jvm") version "1.8.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
    fileTree(files("D:\\javadev\\DaylinMicroservices\\DaylinMicroservices-Core\\build\\libs")) // todo switch to jitpack, this is just for testing
}

dependencies {
    implementation(project(":DaylinMicroservices-CICD-API"))
    implementation(files("D:\\javadev\\DaylinMicroservices\\DaylinMicroservices-Core\\build\\libs\\DaylinMicroservices-Core-0.2.jar")) // todo switch to jitpack, this is just for testing
}

tasks.withType<Jar>() {
    manifest {
        attributes["Main-Class"] = "daylightnebula.daylinmicroservices.cicd.BuilderKt"
    }
}