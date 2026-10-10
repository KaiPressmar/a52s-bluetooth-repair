// Pure Java domain logic: call-audio diagnosis, repair state machine and call reports.
// No Android dependencies, so every safety rule is covered by fast JVM tests.
plugins {
    `java-library`
    jacoco
}

jacoco { toolVersion = "0.8.14" }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(libs.junit)
}
