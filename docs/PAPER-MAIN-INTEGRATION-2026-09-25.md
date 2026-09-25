# Paper-main-Integration vom 25. September 2026

## Grundlage

Verglichen wurde der zuletzt geprüfte Stand `c13e3c9f0a79d0a117af4273d872c767f05b0751`
mit [Paper main a15fed9](https://github.com/PaperMC/Paper/commit/a15fed9c16a5cc93e4ff38d6e2135623e2dc9daa).
Die 17 Commits werden inhaltlich in die integrierte Sinopia-Basis übernommen,
einschließlich der größeren Moonrise- und Werkzeugänderungen. Kein vollständiger
Austausch von Sinopia: lokale Datenmigrationen, Gameplay-Korrekturen und
Folia-/Tessera-Regionsfunktionen bleiben erhalten.

`paperRef` bleibt die ursprüngliche Import-Provenienz; Minecraft/Mache bleiben
26.3 / 26.3+build.1. Buildnummer 007 bleibt unverändert.

## Umfang

| Paper-Commit | Änderung | Integration |
| --- | --- | --- |
| [a15fed9](https://github.com/PaperMC/Paper/commit/a15fed9c16a5cc93e4ff38d6e2135623e2dc9daa) | Gradle 9.8.0, Paperweight beta24, aktualisierte Kotlin-DSL | Übernommen; beide Build-Schichten gemeinsam aktualisiert. |
| [beac08f](https://github.com/PaperMC/Paper/commit/beac08f83f965f86453bb0ef2803329d1ae8f443) | Leere Suggestions nicht vorab abbrechen | Übernommen; Plugins dürfen leere Ergebnisse ergänzen. |
| [f8a210f](https://github.com/PaperMC/Paper/commit/f8a210f1d783dbdc02b7d82e2bc3042efa8c2197) | Cushion in Flüssigkeiten | Übernommen; Blockposition vor Effektauswertung gesetzt. |
| [d5cc7d4](https://github.com/PaperMC/Paper/commit/d5cc7d4af82999472037d1eda52ca3617b801b8f) | Yaw-Winkelvergleich bei Bewegung | Übernommen; beide PlayerMoveEvent-Pfade normalisieren den Winkel. |
| [217fb92](https://github.com/PaperMC/Paper/commit/217fb92a10465228eedfc1285626b14b757c8200) | Entfernte Transport-Container | Übernommen; zusätzlich Regions- und Ladeprüfung beider Kistenhälften. |
| [327052e](https://github.com/PaperMC/Paper/commit/327052edf467c0b355c8d7f3820f3f46a811836e) | Pathfinder-Vergleich | Bereits gleichwertig vorhanden; keine doppelte Änderung. |
| [e1f150a](https://github.com/PaperMC/Paper/commit/e1f150a5eb96c3b1bd6777b230e0d4828451e3dd) | Serverseitiges Suggestions-Event und Namespaces | Übernommen; Future-Abschlüsse werden zum Spieler-Owner weitergeleitet. |
| [dd9d103](https://github.com/PaperMC/Paper/commit/dd9d1031f07dd052e2f495df5ef630c8e44b9f3f) | PlayerPostEffects-API | Übernommen; aktueller Spieler, Owner-Thread und aktiver Zustand werden geprüft. |
| [dfbf6f4](https://github.com/PaperMC/Paper/commit/dfbf6f4183106966ac63889e49c1ecd4c51b46db) | Zusammenfassung der Moonrise-Patches | Inhalt über die folgenden Änderungen übernommen; kein zweites Anwenden des Rebases. |
| [e863872](https://github.com/PaperMC/Paper/commit/e863872fabde080d871453ca5e8acfa67f2b8915) | Chunk-Unload-Kopien, Leafpile 1.2.2, Status-Ticketlevel | Übernommen; expliziter Unload-Pfad statt ambientem ThreadLocal; Lichtdaten bleiben kopiert. |
| [2987325](https://github.com/PaperMC/Paper/commit/298732599a851c6de4cf0f58ea474f723c1dc567) | Broadcast-Liste beim Chunk-Unload | Übernommen; Zugriff auf Folias regionslokale Liste. |
| [be3fe45](https://github.com/PaperMC/Paper/commit/be3fe45d6e75e5dc6b571e220ae058d2ee778a53) | NBT-I/O-Kopien | Übernommen; exklusive Übergabe nur an einzelnen Leser, sonst isolierte Kopien. |
| [f759838](https://github.com/PaperMC/Paper/commit/f759838b851fe4c65462fe9c6eb88f7258c25d80) | Paletten-Packcache und FlatBitsetUtil | Übernommen; threadlokaler Cache, Referenzen im finally freigegeben. |
| [18f05bb](https://github.com/PaperMC/Paper/commit/18f05bb815458ca3dc785b8b26d87732341a9b74) | Klares Wetter nach Respawn | Übernommen; explizite Clear-/Regen-/Gewitter-Synchronisierung. |
| [0c803ba](https://github.com/PaperMC/Paper/commit/0c803ba1fad919e4982010b6d969e6c7df36cc15) | Veraltete is_tempted-Brain-Memory | Gleichwertige Migration im vorhandenen Vanilla-DFU-26.3-Pfad; DataConverter bleibt erhalten. |
| [6db70e0](https://github.com/PaperMC/Paper/commit/6db70e070a815b81b03c89d92e5bb09dfa0ecbdd) | Gamerule-Paket und Locale-Caches begrenzen | Übernommen; maximal 128 Einträge beziehungsweise 256 Cache-Einträge. |
| [a22040d](https://github.com/PaperMC/Paper/commit/a22040d53cf84997ca8f4053d8d878bb375e72ab) | Mending auf Items mit benutzerdefinierter Haltbarkeit | Übernommen; zusätzlich Owner-Prüfung vor Zugriff und nach Plugin-Event. |

## Regionssicherheit und Kompatibilität

- Chunk-Broadcasts werden auf dem Chunk-Owner aus `RegionizedWorldData`
  entfernt. Regions-Merge und -Split verwenden weiterhin Folias Listenmigration.
- Unload-Saves verwenden einen expliziten Serializer-Einstieg. Geladene LevelChunks
  werden dort abgewiesen; ein Owner-Thread ist erforderlich. Nur bereits im
  Chunk-System abgetrennte Sections werden ohne zusätzliche Kopie weitergereicht.
  Normales Autosave, Shutdown-Save und Runtime-Snapshots behalten den kopierenden
  Einstieg. Licht-Nibblearrays werden weiterhin kopiert.
- NBT-Leser erhalten bei mehreren Abnehmern unabhängige, veränderbare Ergebnisse.
  Ausstehende Schreibdaten werden nicht an verändernde Leser ausgeliehen.
  Interne ausdrücklich schreibgeschützte Callbacks können weiterhin auf Kopien
  verzichten. Auch der erste registrierte Leseauftrag ist jetzt abbrechbar.
- Paletten-Caches sind threadlokal; Ausgabedaten gehören dem jeweiligen Aufruf.
  Cache-Referenzen werden auch beim fehlgeschlagenen Packen freigegeben.
- Server-Suggestions lösen ihr neues Event auf dem aktuellen Spieler-Owner aus,
  auch wenn ein Plugin-Future auf einem anderen Thread fertig wird.
  Nach dem Event werden Entfernung, Verbindung und Zuständigkeit erneut geprüft.
  Der explizit asynchrone, vom Plugin behandelte Tab-Complete-Pfad bleibt asynchron.
  Tesseras bestehende Syntax-/Tiefenprüfung und Request-Budget bleiben erhalten.
- Die Post-Effects-API wird nicht automatisch auf andere Threads verschoben:
  synchrone Rückgabewerte bleiben synchron. Plugins müssen auf dem EntityScheduler
  des Spielers arbeiten. Aufbewahrte API-Objekte folgen dem aktuellen CraftPlayer-
  Handle nach Respawn; Listen sind unveränderbare Snapshots.
- Container-Transport validiert Ladezustand und Eigentümerschaft vor BlockEntity-
  Zugriffen, einschließlich beider Hälften zusammengesetzter Container.
- Mending berücksichtigt Haltbarkeit auf dem konkreten ItemStack. Nach einem
  Plugin-Event wird kein inzwischen fremdes oder ersetztes Spielerinventar geändert.
- Keine Abschaltung von Folia-Threadchecks, kein synchroner Global-Thread-
  Ersatz und kein pauschaler Welt-Lock wurden eingebaut.

## Patch-Aufteilung

- Sinopia Minecraft 0045: allgemeine Laufzeitkorrekturen und Datenmigration.
- Sinopia Minecraft 0046: Moonrise-Palette, NBT-I/O und Unload-Kopieroptimierungen.
- Tessera Minecraft 0044: Owner-Thread-Anpassungen für die neue Basis.
- Tessera Implementierung 0033: PlayerPostEffects/Mending und Regionsregressionstests.
- Tessera API 0013: Dokumentation der Post-Effects-Threadzuständigkeit.
- API-/CraftBukkit-Basisänderungen und allgemeine Tests liegen direkt in Sinopia.
- Folias Basispatch 0001 kombiniert regionslokales Autosave mit dem neuen
  Moonrise-Unload-Broadcast-Hook.
- Der Rebuild normalisiert außerdem Metadaten, Kontext und einige Dateinamen
  bestehender Patches auf das Ausgabeformat der neuen Paperweight-Version.

## Bau und Prüfung

Java 25 und den Root-Wrapper verwenden:

```powershell
.\gradlew.bat buildTessera --console=plain --max-workers=2 --no-parallel
```

Die Werkzeugversionen werden gemeinsam auf Gradle 9.8.0 und Paperweight
2.0.0-beta.24 angehoben. Ein isoliertes Gradle-Upgrade mit dem alten Paperweight
ist nicht die unterstützte Kombination.

Zusätzlich ist die Abhängigkeit der Paperweight-Core-Quell-/Ressourcen- und
Server-Filteraufgaben vom Sinopia-Checkout ausdrücklich verdrahtet. Dadurch
funktioniert auch der kombinierte Rebuild unter Gradles Abhängigkeitsprüfung.

Am 25. September 2026 erfolgreich geprüft:

- Erneutes Anwenden sämtlicher Sinopia- und Tessera-Patches aus ihren gespeicherten Dateien.
- Vollständiger `buildTessera` mit aktivierter Configuration Cache-Konfiguration:
  Tests, Checkstyle, Bad-Call-Prüfungen, Testplugin-Kompilierung und Paperclip.
- Server: 10.080 erfasste Testfälle, keine Fehler, 87 übersprungen.
- API: 529 erfasste Testfälle, keine Fehler, zwei übersprungen.
- 48 neue Regressionstestfälle: 14 Paletten-, sechs NBT-I/O-, 13 allgemeine
  Runtime-/Migrations-, zwölf Regions-/Suggestions-/Cushion- und drei Post-Effects-Fälle.
- Build-/Git-Schutzmechanismen: 22 erfolgreiche `buildSrc selfTest`-Checks.
- `:folia-server:generateDevelopmentBundle` erfolgreich:
  `folia-server/build/libs/paperweight-development-bundle-26.3.build.007-alpha.zip`.
  Das Bundle wurde erzeugt; ein separater MVE-Build damit wurde nicht ausgeführt.
- Ein zusätzlicher direkter Gradle-Dry-Run im versionierten `sinopia/` wurde
  abgewiesen, erzeugte aber zuvor `sinopia/.gradle/`. Dieser Cache blockierte
  anschließend die Sinopia-Vorbereitung. Er wurde nach
  `build/sinopia-gradle-cache-backup-2026-09-25/` verschoben; die Schutzprüfung
  bleibt unverändert aktiv. Auch Diagnose-Aufrufe müssen den Tessera-Root
  beziehungsweise den dafür vorgesehenen generierten Arbeitsbereich verwenden.

Ausführbare JAR: `build/libs/tessera-server-26.3.build.007-alpha.jar`.
SHA-256: `584b9edd4f6cc399e2f824c963d15ce0b72065e1b98fd2524412f67cc6b9485a`.
Die vollständigen lokalen Logs liegen unter `build/paper-main-*-2026-09-25.log`.

Nach der oben beschriebenen Cache-Bereinigung wurde `buildTessera` mit der
inzwischen vom Nutzer gesetzten Buildnummer `008` erneut erfolgreich ausgeführt
(8 Minuten 39 Sekunden). Der Server-Testlauf erfasste wieder 10.080 Fälle ohne
Fehler, davon 87 übersprungen; die unveränderten API-Tests waren `UP-TO-DATE`.
Artefakt: `build/libs/tessera-server-26.3.build.008-alpha.jar`.
Prüflog: `build/sinopia-cache-recovery-build-2026-09-25.log`.
Der Root-Build hat keinen neuen Cache unter `sinopia/` angelegt.

Automatisierte Owner-Grenztests ersetzen keinen Live-Test mit Spielern,
Regionswechseln, Respawn, parallelem Entladen und Runtime-Snapshots.
Es wird kein gemessener TPS-/MSPT-Gewinn behauptet.

Die alten generierten Sinopia-Arbeitsbereiche wurden unter
`build/sinopia-workspace-before-2026-09-25` und
`build/sinopia-workspace-main-integration-2026-09-25` erhalten, nicht gelöscht.
`applySinopiaPatches` kann damit einen neuen Arbeitsbereich auf der aktuellen
Basis erstellen, statt den alten Patch-Arbeitsstand zu überschreiben.
