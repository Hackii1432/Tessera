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

tasks.register<Jar>("sparkPreferenceProbeJar") {
    dependsOn(tasks.classes)
    archiveFileName = "spark-preference-probe.jar"
    from(sourceSets.main.get().output) { include("dev/tessera/smoke/SparkPreferenceProbe.class") }
    from("src/sparkProbe")
}
