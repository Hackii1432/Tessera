---
title: "Plugin-Projekt für Tessera einrichten"
description: "Java 25, die tatsächlich gebaute API-Version und Folia-Metadaten verwenden."
navTitle: "Projekt-Setup"
order: 10
updated: 2026-10-06
minecraftVersion: "26.3"
badge: "Anleitung"
---

Für Tessera-Erweiterungen ist die **passende Tessera-API** nötig. Eine normale
Paper-/Folia-API aus einem öffentlichen Maven-Repository enthält nicht automatisch
Tesseras zusätzliche Methoden.

## API beziehen

Der aktuelle Root-Build verwendet `group=dev.folia`, Modul `folia-api` und
Version `26.3.build.017-beta`. Diese Werte stammen aus `gradle.properties`,
`settings.gradle.kts` und dem API-Publishing. Eine Veröffentlichung dieses
Tessera-Artefakts im PaperMC-Maven-Repository ist damit **nicht** belegt.

Reproduzierbarer lokaler Weg, mit JDK 25 im Tessera-Repository:

```powershell
.\gradlew.bat buildTessera
.\gradlew.bat :folia-api:publishToMavenLocal
```

Unter Linux/macOS entsprechend `./gradlew`. Der erste Befehl bereitet alle
Patchschichten vor; ungesicherte Änderungen in generierten Quellen vorher als
Patches exportieren. Der zweite veröffentlicht API, POM und Metadaten im lokalen
Maven-Repository. Eine bloße `files("...jar")`-Abhängigkeit löst transitive
Abhängigkeiten nicht automatisch auf.

## Gradle-Konfiguration des Plugins

```kotlin
plugins {
    java
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenLocal {
        content { includeModule("dev.folia", "folia-api") }
    }
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("dev.folia:folia-api:26.3.build.017-beta")
}
```

`mavenLocal` ist hier der bewusst gewählte lokale Entwicklungsweg. CI benötigt
dieselbe lokale Publikation oder ein von euch tatsächlich gepflegtes Repository;
keine unbekannte Downloadadresse erfinden. Für andere Builds die Version aus
deren Checkout übernehmen. Die API und ihre serverseitigen Bibliotheken nicht
in das Plugin-JAR shaden. Die aktuelle API exponiert unter anderem Adventure
5.2.0 und JSpecify 1.0.0 transitiv; für einen anderen Serverstand nicht blind
die Versionen dieses Artikels erzwingen.

## Plugin-Metadaten

Klassisches `plugin.yml` unter `src/main/resources`:

```yaml
name: ExamplePlugin
version: 1.0.0
main: dev.example.ExamplePlugin
api-version: '26.3'
folia-supported: true
```

`main` muss zur eigenen `JavaPlugin`-Klasse passen. Die API-Version ist `26.3`,
nicht die Buildnummer oder Maven-Artefaktversion. `folia-supported: true` ist
eine notwendige Erklärung, kein automatischer Threading-Fix. Der deklarierte
Wert ist über `plugin.getPluginMeta().isFoliaSupported()` abfragbar.

## Plattformadapter

Für eine gemeinsame Paper-/Folia-/Tessera-JAR Tessera-Klassen erst nach
[Capability-Erkennung](capabilities.md) laden. NMS-/Paket-Zugriffe sind nicht
durch öffentliche API-Kompatibilität abgesichert. Ein früheres RC2-Dev-Bundle
oder ein zufällig ähnlicher Methodenname ist kein Ersatz für den passenden
Serverstand. Dieses Setup braucht kein NMS-Dev-Bundle.
