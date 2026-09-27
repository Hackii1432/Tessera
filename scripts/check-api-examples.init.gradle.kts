import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

// Documentation-only init script; uses the actual API compile classpath and JDK.
// Run from the prepared Tessera root with --no-configuration-cache.
gradle.projectsEvaluated {
    // Init scripts also run for buildSrc/included builds; only target Tessera.
    val apiProject = rootProject.findProject(":folia-api") ?: return@projectsEvaluated
    val apiJar = apiProject.tasks.named("jar", Jar::class.java)
    val toolchains = apiProject.extensions.getByType(JavaToolchainService::class.java)
    val compiler = toolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    apiProject.tasks.register("checkApiDocumentationExamples", Exec::class.java) {
        group = "verification"
        description = "Compile complete examples extracted from docs/api against the current API."
        dependsOn(apiJar)
        workingDir(rootProject.projectDir)
        doFirst {
            commandLine(
                "node",
                rootProject.file("scripts/check-api-examples.mjs").absolutePath,
                compiler.get().executablePath.asFile.absolutePath,
                apiProject.files(apiJar.get().archiveFile, apiProject.configurations.getByName("compileClasspath")).asPath
            )
        }
    }
}
