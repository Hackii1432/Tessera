plugins { java }

dependencies { implementation(gradleApi()) }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.register<JavaExec>("selfTest") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.tessera.buildsupport.LocalPaperTest")
}

tasks.check { dependsOn("selfTest") }

// These tests are an executable real-Git harness, not JUnit test methods.
tasks.test {
    dependsOn("selfTest")
    failOnNoDiscoveredTests = false
}
