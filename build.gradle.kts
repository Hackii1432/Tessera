import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import io.papermc.paperweight.tasks.RebuildGitPatches
import io.papermc.paperweight.core.tasks.CheckoutRepo
import dev.tessera.buildsupport.LocalPaperTask

plugins {
    java // TODO java launcher tasks
    id("io.papermc.paperweight.patcher") version "2.0.0-beta.23"
}

paperweight {
    filterPatches = false
    upstreams.paper {
        // This is a generated adapter, not another maintained repository or a network upstream.
        repo = layout.buildDirectory.dir("sinopia-snapshot").map { it.asFile.absolutePath }
        ref = "tessera-local"

        patchFile {
            path = "paper-server/build.gradle.kts"
            outputFile = file("folia-server/build.gradle.kts")
            patchFile = file("folia-server/build.gradle.kts.patch")
        }
        patchFile {
            path = "paper-api/build.gradle.kts"
            outputFile = file("folia-api/build.gradle.kts")
            patchFile = file("folia-api/build.gradle.kts.patch")
        }
        patchDir("paperApi") {
            upstreamPath = "paper-api"
            excludes = setOf("build.gradle.kts")
            patchesDir = file("folia-api/paper-patches")
            outputDir = file("paper-api")
        }
        patchDir("paperCheckstyle") {
            upstreamPath = "paper-checkstyle"
            patchesDir = file("folia-checkstyle/project-patches")
            outputDir = file("paper-checkstyle")
        }
        patchDir("paperCheckstyleConfig") {
            upstreamPath = ".checkstyle"
            patchesDir = file("folia-checkstyle/config-patches")
            outputDir = file(".checkstyle")
        }
    }
}

val localPaperSnapshot = layout.buildDirectory.dir("sinopia-snapshot")
val localPaperWorkspace = layout.buildDirectory.dir("sinopia-workspace")

tasks.withType<LocalPaperTask>().configureEach {
    group = "sinopia"
    sourceDirectory = layout.projectDirectory.dir("sinopia")
    snapshotDirectory = localPaperSnapshot
    workspaceDirectory = localPaperWorkspace
    expectedMinecraftVersion = providers.gradleProperty("mcVersion")
}

val prepareLocalPaper = tasks.register<LocalPaperTask>("prepareSinopia") {
    description = "Snapshot the versioned Sinopia base; no Paper GitHub checkout is required."
    operation = "prepare"
}

tasks.withType<CheckoutRepo>().configureEach {
    if (name == "checkoutPaperRepo") {
        dependsOn(prepareLocalPaper)
        initializeSubmodules = false
    }
}

val preparePaperWorkspace = tasks.register<LocalPaperTask>("prepareSinopiaWorkspace") {
    description = "Create an editable Sinopia workspace without resetting existing edits."
    dependsOn(prepareLocalPaper)
    operation = "workspace"
}

tasks.register<GradleBuild>("applySinopiaPatches") {
    group = "sinopia"
    description = "Apply Sinopia's Minecraft patches in its separate generated workspace."
    dependsOn(preparePaperWorkspace)
    dir = localPaperWorkspace.get().asFile
    buildName = "sinopia-apply"
    tasks = listOf("applyPatches")
}

tasks.register<GradleBuild>("fixupSinopiaSourcePatches") {
    group = "sinopia"
    description = "Fold Minecraft edits into Sinopia's existing source patches in its editable workspace."
    mustRunAfter("applySinopiaPatches")
    dir = localPaperWorkspace.get().asFile
    buildName = "sinopia-fixup"
    tasks = listOf("fixupSourcePatches")
}

tasks.register<GradleBuild>("rebuildSinopiaPatches") {
    group = "sinopia"
    description = "Rebuild Minecraft patches inside the existing Sinopia workspace; does not overwrite the versioned base."
    mustRunAfter("applySinopiaPatches", "fixupSinopiaSourcePatches")
    dir = localPaperWorkspace.get().asFile
    buildName = "sinopia-rebuild"
    tasks = listOf("rebuildPatches")
}

tasks.register<LocalPaperTask>("captureSinopiaPatches") {
    description = "Copy rebuilt Sinopia patches/build-data back, refusing concurrently changed sources."
    mustRunAfter("rebuildSinopiaPatches", "applySinopiaPatches", "fixupSinopiaSourcePatches")
    operation = "capture"
}

tasks.register<GradleBuild>("buildTessera") {
    group = "build"
    description = "Prepare Sinopia, apply every patch, test and build the Tessera server."
    dependsOn("applyAllPatches")
    dir = layout.projectDirectory.asFile
    // Paperweight already ran a nested build to apply patches. Use a distinct identity.
    buildName = "tessera-compiled"
    tasks = listOf("test", "build")
}

// The runnable Tessera paperclip is the root build artifact; do not emit an empty root Java jar.
tasks.jar {
    enabled = false
}

val paperMavenPublicUrl = "https://repo.papermc.io/repository/maven-public/"

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    repositories {
        mavenCentral()
        maven(paperMavenPublicUrl)
    }

    dependencies {
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
    tasks.withType<JavaCompile>().configureEach  {
        options.encoding = Charsets.UTF_8.name()
        options.release = 25
        options.isFork = true
    }
    tasks.withType<Javadoc>().configureEach  {
        options.encoding = Charsets.UTF_8.name()
    }
    tasks.withType<ProcessResources>().configureEach  {
        filteringCharset = Charsets.UTF_8.name()
    }
    tasks.withType<Test>().configureEach  {
        testLogging {
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
            events(TestLogEvent.STANDARD_OUT)
        }
    }

    extensions.configure<PublishingExtension> {
        repositories {
            maven("https://artifactory.papermc.io/artifactory/releases/") {
                name = "paperReleases"
                credentials(PasswordCredentials::class)
            }
        }
    }
}

tasks.register("printMinecraftVersion") {
    doLast {
        println(providers.gradleProperty("mcVersion").get().trim())
    }
}

tasks.register("printPaperVersion") {
    doLast {
        println(project.version)
    }
}
