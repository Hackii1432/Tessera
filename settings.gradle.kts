pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenLocal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// tessera 26.2 start - change root name
rootProject.name = "tessera"
// tessera 26.2 end - change root name

include("folia-api")
include("folia-server")
include("test-plugin")

gradle.lifecycle.beforeProject {
    val mcVersion = providers.gradleProperty("mcVersion").get().trim()
    // tessera 26.2 start - change build loader
    val tesseraBuildNumber = providers.gradleProperty("tesseraBuildVersion").get()
    val tesseraVersionChannel = providers.gradleProperty("tesseraBuildChannel").get()

    version = "$mcVersion.build.$tesseraBuildNumber-$tesseraVersionChannel"
    // tessera 26.2 end - change build loader
}
