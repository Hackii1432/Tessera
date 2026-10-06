plugins {
    java
}

dependencies {
    compileOnly(project(":folia-api"))
    compileOnly(project(":folia-server")) // Isolated native regression fixtures; never bundled in the plugin.
    compileOnly(files(rootProject.project(":folia-server").configurations.named("compileClasspath")))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

tasks.jar {
    archiveFileName = "tessera-runtime-world-smoke.jar"
}
