plugins {
    java
}

dependencies {
    compileOnly(project(":folia-api"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

tasks.jar {
    archiveFileName = "tessera-runtime-world-smoke.jar"
}
