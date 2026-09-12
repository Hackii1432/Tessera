# Tessera – Migrationsstand 26.3-rc-2

> Historischer Zwischenstand vor der Sinopia-Portierung. Die hier beschriebenen
> 62 Konfliktdateien und fehlenden Features sind inzwischen bearbeitet.
> Maßgeblich ist [PORTIERUNG-26.3-rc-2.md](PORTIERUNG-26.3-rc-2.md).

Prüfung am 12. September 2026 auf `ver/26.3.x`, Ausgangscommit `0b66675`.

## Status

**Vorbereitete Teilmigration, noch kein freigegebener oder getesteter 26.3-Server.**
Die Build-Patches und der API-Teil wurden angepasst bzw. geprüft. Die
Server-Patchserie lässt sich gegen eine isolierte Kopie der neuen Paper-Quellen
anwenden. Die vollständige Minecraft-/Folia-Portierung ist damit noch nicht
abgeschlossen; insbesondere fehlt in der gewählten Paper-Basis die angewendete
Moonrise-Grundlage für Folias Regions-Code.

Deine Werte `mcVersion=26.3-rc-2`, `apiVersion=26.3` und die Paper-Referenz wurden
beibehalten. Buildnummer `018` und der bisherige Channel-Wert wurden nicht
geändert; die Zeichenfolge `stable` in einem API-Artefaktnamen bedeutet hier
ausdrücklich keine Freigabe dieser Minecraft-Vorabversion. Die bereits
vorhandene Changelog-Datei zu 26.2 wurde nicht verändert.

## Verifizierte Basis und rc-3

| Bestandteil | Geprüfter Stand |
| --- | --- |
| Paper-Branch | `dev/26.3` |
| Paper-Commit | `38b0bfeb67855206ede9cb1df4f3354c4611c4c2` (`Work`) |
| Minecraft | `26.3-rc-2` |
| Paper-Mache | `26.3-rc-2+build.2` |
| Paperweight | `2.0.0-beta.23` |
| Java / Gradle | Java 25 / Gradle 9.4.1 |
| Paper-Migrationskennzeichnung | `updatingMinecraft=true` im Upstream |

Beim erneuten direkten Abgleich am 12.09.2026 um 19:10 Uhr (Europe/Berlin)
verwies Paper weiterhin auf denselben Commit. Mojangs offizielles
Versionsmanifest nennt `26.3-rc-2` als neuesten Snapshot und enthält keinen
Eintrag `26.3-rc-3`. `26.3-pre-3` existiert, ist aber eine ältere Vorabversion
und kein Release Candidate 3. Ein rc-3-Umstieg kann deshalb zu diesem
Prüfzeitpunkt nicht auf eine veröffentlichte offizielle Grundlage gestützt
werden. Die Folia-Branchliste enthält ebenfalls noch keinen 26.3-Branch.

Quellen: [Paper-Versionseinstellungen](https://github.com/PaperMC/Paper/blob/38b0bfeb67855206ede9cb1df4f3354c4611c4c2/gradle.properties),
[Paper-Buildkonfiguration](https://github.com/PaperMC/Paper/blob/38b0bfeb67855206ede9cb1df4f3354c4611c4c2/paper-server/build.gradle.kts),
[Mojang-Versionsmanifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json),
[Minecraft 26.3 Release Candidate 2](https://www.minecraft.net/en-us/article/minecraft-26-3-release-candidate-2),
[Folia-Branches](https://api.github.com/repos/PaperMC/Folia/branches?per_page=100).

## Umgesetzte Anpassungen

- `build.gradle.kts`: Paperweight-Patcher von `2.0.0-beta.21` auf die von Paper
  verwendete `2.0.0-beta.23` aktualisiert.
- `folia-server/build.gradle.kts.patch`: Kontext auf Mache
  `26.3-rc-2+build.2` angepasst; Tesseras Artefaktpfad, Branding und Fork-Setup
  bleiben erhalten.
- Derselbe Build-Patch übernimmt Papers `oldPaperCommit=50018799…` nicht in
  den Downstream. Dieser Commit gehört zu Paper, nicht zum Tessera-Repository.
  Die ungeprüfte Übernahme verursachte bei `setupMacheSources` einen JGit-
  Fehler: `Missing unknown 50018799…`, nach außen als `Short read of block`
  gemeldet. Die Basiseinrichtung läuft mit der Korrektur weiter.
- `folia-api/build.gradle.kts.patch`: Kontext an die neuen JetBrains-
  Annotationsabhängigkeiten (`26.1.0`) angepasst. Die bestehenden Source- und
  Checkstyle-Pfade bleiben erhalten.
- Server-Patch `0001-Region-Threading-Base.patch`: `CraftEnderman` an den
  umbenannten Minecraft-Typ `Enderman` angepasst. Die Owner-Thread-Prüfung
  bleibt unverändert enthalten.
- Server-Patch `0009-Tessera-runtime-world-lifecycle.patch`: Kontext im
  `CraftServer`-Konstruktor auf `getStructureTemplateManager()` angepasst.
- `gradle.properties`: Deine neue Version/Referenz beibehalten und die
  Kommentare um den überprüften, noch unfertigen Upstream-Status ergänzt.

Es wurden keine Tessera-Funktionen entfernt, keine Regionsprüfungen
ausgeschaltet und keine Feature-Patches übersprungen. Die Änderungen sind
lokal; im Hauptrepository wurde kein Commit oder Push erstellt.

## Erfolgreiche Prüfungen

### API und Testplugin

Alle zwölf API-Feature-Patches ließen sich ohne Änderungen an ihrem Inhalt auf
die neue Paper-API anwenden. Anschließend erfolgreich ausgeführt:

```powershell
.\gradlew.bat :folia-api:build :test-plugin:jar --no-daemon --console=plain
```

Ergebnis: **BUILD SUCCESSFUL**, 529 API-Testfälle, keine Fehler, zwei
übersprungen. Auch API-Checkstyle, Javadoc, API-JAR und Testplugin-Build sind
erfolgreich. Protokoll: `build/migration-26.3-api-build.log`.

### Isolierte Server-Patchprüfung

Die 22 Server-Patches wurden vollständig und in Reihenfolge auf eine frische
Kopie von Papers Serverquellen am festgelegten Commit angewendet. Dazu waren
die beiden oben beschriebenen Kontext-/Typanpassungen nötig. Die Prüfumgebung
liegt unter `build/migration-26.3-server-patch-check/paper-server`; die
Commitreihenfolge ist in `build/migration-26.3-server-patch-order.log` erfasst.

Das bestätigt die Anwendbarkeit der Server-Patchserie, **nicht** deren
Kompilierbarkeit oder Regionssicherheit auf einer fertigen 26.3-Laufzeit.
Die bestehenden generierten Serverquellen wurden für diese isolierte Prüfung
nicht ersetzt. Stasis-, Enderperlen-, Snapshot- und Restore-Erweiterungen
bleiben in der Serie erhalten.

## Noch fehlende Upstream-Grundlage

Paper hält an diesem Commit 34 Feature-Patches unter
`paper-server/patches/features_unapplied` vor. Darunter befinden sich
Moonrise/Chunk-System, der DataConverter-Umbau, Entity Activation Range und
weitere Optimierungen. Es reicht nicht, diesen Ordner umzubenennen:
Die Patches müssen zuerst tatsächlich auf die neue Minecraft-Version portiert
und geprüft werden.

Tesseras Minecraft-Patch `0001-Region-Threading-Base.patch` verändert bereits
zu Beginn `ca/spottedleaf/moonrise/common/misc/NearbyPlayers.java`. Diese
Grundlage stammt aus dem noch nicht angewendeten Moonrise-Patch. Das betrifft
die Architektur des Regions-/Chunk-Systems, nicht nur verschobene Zeilennummern.

Auch [Papers eigener CI-Lauf für genau diesen Commit](https://github.com/PaperMC/Paper/actions/runs/34691133436)
scheitert im Schritt `Apply Patches`; `Build` und Paperclip-Erstellung wurden
dort nicht ausgeführt. Das ist unabhängig von den hier korrigierten Tessera-
Patchkontexten. Die genaue Ursache dieses externen CI-Fehlers wurde damit
nicht bestimmt; der lokal nachgewiesene Folia-Fehler ist separat beschrieben.

### Ergebnis des vollständigen lokalen Patchlaufs

Der abschließende Aufruf von `applyAllPatches` konnte nach der Korrektur des
Downstream-Update-Baselines die Minecraft-Grundlage einrichten. Dabei wurden
105 Mache-Patches und 929 Paper-Minecraft-Quellpatches erfolgreich angewendet;
auch die drei Paper-Ressourcenpatches waren angewendet.

Der Lauf scheiterte anschließend bei
`:Tessera:folia-server:applyMinecraftFeaturePatches` am ersten
Minecraft-/Folia-Patch `0001-Region-Threading-Base.patch`: **62 Dateien mit
Konflikten**. Neben Inhaltskonflikten fehlen von Folia vorausgesetzte Klassen,
unter anderem `NearbyPlayers`, `ChunkHolderManager`, `ChunkTaskScheduler`,
`NewChunkHolder`, `RegionizedPlayerChunkLoader`, `EntityLookup` und
`ActivationRange`. Alte 26.2-Klassen ungeprüft zu übernehmen oder betroffene
Patches auszulassen wäre keine belastbare Portierung.

Das vollständige Protokoll liegt in `build/migration-26.3-apply-final.log`,
der vor der Bereinigung gesicherte Git-Status in
`build/migration-26.3-minecraft-conflicts.txt`.

Nach der Diagnose wurde ausschließlich die durch diesen Lauf gestartete,
fehlgeschlagene `git am`-Anwendung abgebrochen. Das generierte Repository
`folia-server/src/minecraft/java` ist wieder konfliktfrei auf der
26.3-Grundlage **vor** den Folia-Feature-Patches. Die zuvor sauberen
Arbeitskopien enthielten keine ungesicherten Benutzeränderungen. Die
versionierten Tessera-Patches und Diagnoseprotokolle bleiben erhalten.
Die tatsächlichen generierten `paper-server`-Quellen stehen weiterhin auf
der bisherigen Basis; die erfolgreiche Prüfung der 22 Server-Patches erfolgte
nur in der oben genannten isolierten Kopie. Dieser Zwischenstand ist deshalb
kein vollständiger oder startbarer Tessera-26.3-Server.

## Fortsetzung

1. Eine Paper-26.3-Basis verwenden, deren Minecraft-Patches anwendbar und deren
   benötigte Feature-Patches einschließlich Moonrise wieder aktiv sind.
   Alternativ wäre deren eigenständige Portierung ein zusätzlicher großer
   Arbeitsumfang vor der eigentlichen Folia-Portierung.
2. Danach die 31 Minecraft-/Folia-Feature-Patches in Reihenfolge portieren;
   Thread-Besitz, Scheduler, Chunk-I/O und Regionsübergänge erneut prüfen.
3. Den vollständigen Server mit `test build createPaperclipJar` bauen. Ein
   erfolgreiches API-Build ersetzt diese Prüfung nicht.
4. Smoke-Test-Artefaktpfade und Protokollclients auf die tatsächliche
   26.3-Version anpassen und sämtliche Runtime-World-/Snapshot-/Restore- und
   Gameplay-Regressionen auf dem neuen JAR prüfen.
5. Nur mit getrennten Testwelten beginnen. Es wurde kein vorhandener
   Produktionsserver gestartet und keine Spielwelt migriert.

Ein neues `mcVersion` allein oder das Überspringen von Moonrise-/Regions-
Patches liefert keinen funktionierenden 26.3-Folia-Server. Bis zum erfolgreichen
Gesamtbuild gibt es aus diesem Migrationsversuch kein neues startbares
Server-JAR und keinen neuen Server-Smoke-Test.
