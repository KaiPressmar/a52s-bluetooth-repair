import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.android.application) apply false
    jacoco
}

jacoco { toolVersion = "0.8.14" }

tasks.register<JacocoReport>("coverageReport") {
    group = "verification"
    description = "Combined production-code coverage from core JVM and Android Robolectric tests."
    dependsOn(":core:test", ":app:testDebugUnitTest")
    executionData.from(
        project(":core").layout.buildDirectory.file("jacoco/test.exec"),
        project(":app").layout.buildDirectory.file("jacoco/testDebugUnitTest.exec")
    )
    sourceDirectories.from(files("core/src/main/java", "app/src/main/java"))
    classDirectories.from(
        fileTree("core/build/classes/java/main"),
        fileTree("app/build/intermediates/javac/debug") {
            exclude("**/R.class", "**/R\$*.class", "**/BuildConfig.class", "**/databinding/**")
        }
    )
    reports {
        html.required = true
        xml.required = true
        csv.required = true
    }
}
