# API-Referenz: Migration und Prüfung

Stand: 2026-09-28. Die öffentliche Entwicklerreferenz ist nach
[docs/api/index.md](api/index.md) migriert. Diese Datei ist ein Repository-
Prüfbericht außerhalb des Website-Importordners, keine zweite API-Referenz.

## Geprüfter Quellstand

- Branch `ver/26.3.x`, Ausgangs-HEAD
  `137750de5182131d8cf0ea3ca6fc7f8e7fa99e77`; der Arbeitsbaum war vor dieser
  Dokumentationsaufgabe sauber. Kein Commit oder Push für diese Aufgabe.
- `gradle.properties` und `settings.gradle.kts`: Minecraft/API 26.3,
  `tesseraBuildVersion=010`, Kanal `alpha`, Maven-Version `26.3.build.010-alpha`.
- Java-25-API, Gradle 9.8.0, Paperweight 2.0.0-beta.24.
- Sinopia ist eine integrierte Paper-abgeleitete Basis mit selektiven lokalen
  Übernahmen. `paperRef=38b0bfeb67855206ede9cb1df4f3354c4611c4c2` ist ihre
  Importherkunft, **nicht** die vollständige Beschreibung des heutigen Codes.

Abgeglichen wurden die dauerhaften API-Patches unter
`folia-api/paper-patches/features`, die integrierte `sinopia/paper-api`, die
gepatchte API unter `paper-api/src/main/java` sowie die tatsächlichen
CraftBukkit-/Minecraft-Implementierungen und vorhandenen Tests. Besonders relevant:

- `Bukkit`, `Server`, `TesseraCapabilities`, Scheduler-Interfaces und die
  Folia-Schedulerimplementierungen, einschließlich Retirement/Disable und TPS.
- `RuntimeWorldManager`, alle Options-/Resultatklassen,
  `CraftRuntimeWorldManager`, Runtime-Lifecycle-/Snapshot-Zulassung und Gates.
- `PlayerRestoreService`, Resultate/Status, `CraftPlayerRestoreService`,
  Koordinator, natives Backend, Writer-Fence und Teleport-Scope.
- Scoreboard-Modelle und `CraftPlayer`-Zuweisung, Entity-Tags, Portal-/Respawn-
  Events und native Transferpfade.
- Sinopias Gamerule-/Post-Effects-Oberfläche, `CraftWorld`-Global-Prüfungen,
  Post-Effects-Owner-Prüfungen und die tatsächlichen Maven-Abhängigkeiten.

Eine unveränderte Paketbezeichnung wurde nicht als Beleg für allgemeine
Thread-Sicherheit oder zusätzliche öffentliche APIs gewertet.

## Neue Struktur

Alle 18 Artikel liegen direkt in `docs/api`, mit YAML-Frontmatter und
Bearbeitungsdatum 2026-09-28. `README.md` enthält die vom Import ausgenommenen
[Pflegehinweise](api/README.md).

| Order | Artikel | Inhalt |
| ---: | --- | --- |
| 0 | [index.md](api/index.md) | Einstieg und Herkunft der APIs |
| 10 | [project-setup.md](api/project-setup.md) | Java, API-Artefakt, Gradle und Plugin-Metadaten |
| 15 | [capabilities.md](api/capabilities.md) | Fähigkeiten, Vertragsversion und Adapter |
| 20 | [ownership.md](api/ownership.md) | Threadkontexte, Ownership und Teleports |
| 30 | [scheduler.md](api/scheduler.md) | Vier Scheduler, Aufgaben und Abbruch |
| 40 | [runtime-worlds.md](api/runtime-worlds.md) | Create/Load, Identität und Ergebnisse |
| 50 | [world-cloning.md](api/world-cloning.md) | Statische Templates und parallele Arenen |
| 60 | [world-snapshots.md](api/world-snapshots.md) | Frische Saves, Level-/Player-Stores und Publikation |
| 70 | [world-unloading.md](api/world-unloading.md) | Drain/Close, Optionen und Fehler |
| 80 | [player-restore.md](api/player-restore.md) | Nativer Vertrag 1 und Recovery |
| 90 | [scoreboards.md](api/scoreboards.md) | Modelle, Publikation und Entity-Tags |
| 100 | [events.md](api/events.md) | Initialisierung, Welt-Lifecycle, Portal, Respawn, Restore |
| 110 | [region-tps.md](api/region-tps.md) | Öffentliche TPS-Abfragen und Grenzen |
| 120 | [gamerules.md](api/gamerules.md) | Enderaugen-Regel und globale Änderungen |
| 130 | [player-effects.md](api/player-effects.md) | Geerbte Post-Effects-API und Besitzer |
| 140 | [commands.md](api/commands.md) | Konsole/RCON und Befehlsgrenzen |
| 150 | [compatibility.md](api/compatibility.md) | Paper/Folia/Sinopia/Tessera-Adapter |
| 160 | [error-handling.md](api/error-handling.md) | Futures, Disable und Folgearbeit |

## Abdeckung der bisherigen Referenz

| Bisheriger Inhalt | Kanonischer Zielbereich |
| --- | --- |
| `tessera-api.md`: Grundprinzipien, Setup, Erkennung und Pakete | Einstieg, Projekt-Setup, Capabilities, Kompatibilität |
| Threadmodell, Scheduler, regionale TPS | Ownership, Scheduler, regionale TPS |
| Runtime-Weltmethoden, Optionen, Status, Identität, parallele Arenen | Runtime-Welten, Weltvorlagen, Welt-Unload |
| Scoreboards, Sidebar-Beispiele, Entity-Tags | Scoreboards |
| Gamerule, Konsole/RCON und Best Practices | Gamerules, Befehle, Fehlerbehandlung |
| `runtime-world-lifecycle.md`: Fresh Snapshot einschließlich Level-Root | Snapshots |
| Lifecycle-Zustandsmaschine, Executor-/Lock-Reihenfolge und Storage-Details | Separater [Architekturhinweis](runtime-world-architecture.md), ausdrücklich kein Plugin-Vertrag |
| MAB-Beispiel mit bereits erfüllten `join()`-Zugriffen und Platzhalter-Löschfunktion | Nichtblockierende `WorldExamples`-/Teleport-Muster, Clone-Paar und sichere Unload-/Pfadregeln |
| `region-safe-scoreboards.md`: Publikation, Lifecycle, Disable und Tests | Scoreboards einschließlich vollständig kompilierbarem Muster und Prüfgrenzen |
| Bisheriger nativer Restore-Statusbericht | Spieler-Restore als öffentliche Referenz; datierter Status-/Buildbericht bleibt Nachweis |

Die drei aufgeteilten alten Referenzdateien sind kurze Verweisseiten; ihre
bisherigen H2-Abschnittsanker bleiben erhalten. Keine zweite vollständige
Referenz wird parallel gepflegt. README und Workflowhinweise verlinken direkt
die neuen Artikel. Datierte technische Berichte behalten ihren historischen
Inhalt und verweisen, soweit angepasst, auf die gepflegte Referenz.

## Fachliche Korrekturen

1. Zielstand von 26.2 auf den nachgewiesenen Build 010 / Minecraft 26.3 korrigiert.
   Das API-Artefakt ist `dev.folia:folia-api:26.3.build.010-alpha`; keine
   unbelegte Veröffentlichung dieses Fork-Artefakts im Paper-Maven vorausgesetzt.
   Lokale Publikation samt transitiven Abhängigkeiten dokumentiert, einschließlich
   Adventure 5.2.0 und JSpecify 1.0.0.
2. `RegionizedServerInitEvent` nicht mehr als Paper/Folia-Detektor verwendet:
   Die Klasse existiert bereits in Sinopia/Paper. Reflektive Capability-Auswahl
   lädt Tessera-Adapter erst nach erfolgreicher Prüfung und verschluckt keine
   Fehler eines vorhandenen Capability-Getters.
3. Gamerule-Änderungen auf den **Global-Thread** verlegt; das alte
   RegionScheduler-Beispiel widersprach `CraftWorld`. `setGameRule`-Erfolg ist
   keine Aussage, dass sich der effektive Wert geändert hat. Tessera-Regel und
   geerbte Nether-/Locator-Regeln werden unterschieden.
4. Scheduler-`false`/`null`, ausbleibende Retirement-Callbacks bei bereits
   stillgelegten Entities, Plugin-Disable, laufende Cancellation und eigene
   Future-Abschlüsse präzisiert. Async-Callbacks besitzen keine implizite Region.
5. Scoreboard-Zuweisung als explizite interne Owner-Weiterleitung beschrieben,
   nicht als allgemeine Bukkit-Threadfreigabe. Ein `void`-Aufruf ist off-owner
   keine Read-after-Write-/Publikationsbestätigung. Entity-Tags bleiben ownergebunden.
6. Frische Snapshots vom statischen Clone-Cache getrennt. Alle geladenen Welten
   nehmen an der Schutzphase teil; nur angefragte werden kopiert. Drei publizierte
   Verzeichnisse sind keine einzelne absturzatomare Dateisystemtransaktion.
7. Tatsächlichen nativen Restore-Vertrag **1** dokumentiert, einschließlich
   Fehlerstatus, operationsgebundenem Event-Scope und dauerhafter Caller-
   Commit-/Recovery-Verantwortung. `PlayerRestoreResult` besitzt kein `cause()`.
   Die Dokumentationsänderung implementiert keinen neuen Restore-Code.
8. Geerbte Post-Effects-API und aktuelle Respawn-Zugriffe aufgenommen; erhaltene
   Handles beziehungsweise Post-Respawn-Events nicht mit sofortiger fremder
   Entity-Zuständigkeit verwechselt. Rich-`/tps`-Interna nicht als öffentliche
   Region-ID-/MSPT-/Spielerzuordnungs-API ausgegeben.

## Ausgeführte Prüfungen

- `node --test scripts/validate-api-docs.test.mjs`: **35 bestanden**.
  Darunter ungültige Metadaten, Längen/Datum, fehlende Felder, Dateinamen,
  Überschriften, Codefences, Datei-/Ankerlinks, Unicode-Anker und Importgrenzen.
- `node scripts/validate-api-docs.mjs`: **18 Artikel bestanden**, deutlich unter
  100 Artikeln, 512 KiB pro Datei und 8 MiB insgesamt. Alle Artikel haben
  eindeutige Abschnittsanker und gekennzeichnete Codeblöcke. Keine Bilder,
  Zugangsdaten oder internen Dienstadressen in den veröffentlichten Artikeln.
- Relative Links/Anker der Artikel, Pflegehinweise, Verweisseiten, geänderten
  Repository-Dokumentation und dieses Berichts geprüft. Öffentliche GitHub-
  Quell-/Berichtlinks sind gegen die lokalen Repository-Ziele abgeglichen;
  keine Live-HTTP-Verfügbarkeitsprüfung und kein Website-Frontend gestartet.
- Der Dokumentations-Gradle-Task kompiliert **8 vollständige Java-Beispiele**
  unverändert aus den Artikeln gegen die aktuelle API-JAR und deren echte
  Compile-Classpath mit JDK 25: `TesseraDetection`, `RuleExample`,
  `RestoreListener`, `EffectsExample`, `OwnerTasks`, `TeleportExample`,
  `WorldExamples`, `SidebarExample`. **BUILD SUCCESSFUL**. Kürzere Ausschnitte
  und Signaturblöcke wurden anhand des Quellcodes geprüft, nicht als eigenständige
  Pluginprogramme ausgegeben.
- `git diff --check`: keine Whitespace-Fehler. Die 28 vorhandenen Dateien unter
  `docs/builds` beziehungsweise `docs/CHANGELOG*` stimmen per SHA-256 mit dem
  Zustand vor Beginn überein.

Reproduzierbarer Aufruf auf einem vorbereiteten Checkout:

```powershell
.\gradlew.bat -I scripts/check-api-examples.init.gradle.kts :folia-api:checkApiDocumentationExamples --no-configuration-cache --console=plain --max-workers=2 --no-parallel
```

Lokal wurde dafür der bereits vorhandene Gradle-Cache mit `--offline` genutzt.
Das Init-Script ignoriert `buildSrc`/fremde Included Builds. Es registriert nur
für den aktuellen Tessera-API-Build einen zusätzlichen Dokumentationsprüftask.
Weder Serverimplementierung noch API-/Minecraft-Patches wurden verändert; kein
neuer Server-Gesamtbuild oder Live-Servertest war Teil dieser Dokumentationsaufgabe.

## Bestehender separater CI-Befund

Die unveränderten Changelog-Validator-Tests bestehen (**22 Tests**), aber dessen
Prüfung der vorhandenen Release-Dateien scheitert bereits an
[docs/builds/26.2/0.0.1.md](builds/26.2/0.0.1.md): `Use a double-quoted YAML string`.
Die Datei enthält unzitierte Textfelder und das ältere Abschnittsschema. Der
bestehende CI-Release-Schritt bleibt damit rot, unabhängig von der neuen API-
Referenz. Entsprechend dem Auftrag wurden weder Changelogs noch deren Validator
oder Anforderungen verändert. Dieser Altbestand benötigt einen getrennten Auftrag.

Die API-Dokumentationsprüfung und Beispielkompilierung sind in den bestehenden
Workflow aufgenommen. Das bedeutet nicht, dass die komplette CI trotz des oben
genannten bestehenden Release-Fehlers grün ist.

## Pflege und Grenzen

Die vorhandenen `sinopia/AGENTS.md`- und `sinopia/CONTRIBUTING.md`-Hinweise sowie
der Tessera-Workflow verlangen nun die gemeinsame Pflege von API-Code, Patches
und Referenz. Es wurden keine Website-Dateien verändert oder Inhalte publiziert.

Kompilierbare Beispiele ersetzen keine Multithreading-/Clientintegrationstests.
Die nativen Build-010-Tests werden als vorhandene datierte Nachweise zitiert,
nicht als hier neu ausgeführt. Die konkrete MCC-/MVE-/TAB-/LuckPerms-Abnahme und
visuelle Vanilla-Client-Prüfung bleiben entsprechend dem bestehenden Buildbericht
offen. Eine öffentliche Maven-Distribution für das Tessera-Fork-Artefakt ist
nicht nachgewiesen; dafür beschreibt die Referenz den lokalen Publish-Weg.
