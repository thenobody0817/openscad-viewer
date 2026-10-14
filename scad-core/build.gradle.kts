plugins {
    kotlin("jvm") version "2.2.20"
}

group = "com.openscad"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // Robust 2D polygon overlay, buffering (offset), convex hull and
    // triangulation for the 2D geometry kernel.
    implementation("org.locationtech.jts:jts-core:1.20.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
