# Build 019 – Moonrise-Update und Paper-APIs

Stand: 11.10.2026. Tessera `26.3-019-beta`, Minecraft/API `26.3`, Java 25.
Die empfohlene Paper-Integration ist implementiert, dauerhaft gesichert,
erneut angewendet und auf der finalen ausführbaren JAR nativ geprüft.
Keine MCC-/MVE-/Horizons-Produktänderung und kein automatischer Root-Commit/Push.

## Implementierter Umfang

Alle 19 Paper-Commits nach `4728a90` bis `99867f8` sind fachlich berücksichtigt.
Der reine Patch-Rebuild und die bereits gleichwertig vorhandene DragonBattle-
Korrektur verursachen keine zweite Verhaltensänderung. Die
[Commit-Zuordnung](PAPER-INTEGRATION-2026-10-11.md) benennt jeden Original-Commit,
seine Umsetzung und Folia-Anpassung; die
[Changelog 0.0.19](builds/26.3/0.0.19.md) beschreibt die Benutzeränderungen.

- Beide Moonrise-Updates: Paletten, WorldGen-Zugriffe, Noise/Biome, Materialregeln,
  threadlokale Puffer/Arenen und Chunk-Ticket-Batching. Bestehende Folia-
  Ownership-, Ticket-, Anti-Xray- und Tessera-Palettenkorrekturen bleiben erhalten.
- Paper-APIs: typisierte PDC-Schlüssel, Batch-Rezepte, allgemeiner Waffen-Getter,
  Standard-/effektive Schwerkraft, Luftwiderstand, ausdrücklicher Post-Effects-
  Refresh und der Unrestricted-Command-Marker ohne Permission-Bypass.
- Unveränderliche native Rezeptkataloge; validierte globale Batch-Publikation,
  erneuerter Learnable-Cache, zusammengehörige Displays/RecipeBook-Resets und
  nicht recycelte Display-IDs innerhalb des laufenden RecipeManager-Katalogs.
- Client-/Advancement-Arbeit auf den aktuellen Spielerbesitzern einschließlich
  Respawn/Transfer und Tick-Freeze. Kein Warten des Global-Threads auf fremde
  Regionen, kein neuer öffentlicher Freeze-Bypass für Plugin-Tasks.
- Villager-/Zombie-Displayitems, Spawner-Blocklicht, SulfurCube-Cancellation und
  zusätzliche Callback-Ownership-Prüfungen; kein weiterer Tick am alten Owner
  nach Entfernung oder Transfer durch Ignite-/Explosion-Callbacks.
- Typisierte Datenmigrationen für verborgene Item-Komponenten, eingebettete
  Structure-Blockstates und den V2701-Pool-Walker im bestehenden DFU-Pfad.
  Keine beliebige Plugin-NBT-Umschreibung und kein DataConverter-Austausch.
- Spark `1.10.191`, JOML ohne Unsafe als respektierbarer Default und Native-
  Access-Manifest. Java, Minecraft/Mache, Gradle, Paperweight und Leafpile bleiben.

## Quellstand und dauerhafte Patches

Branch `ver/26.3.x`; Root-Basiscommit
`c159a4e973747dbe787648d569826124a7b3c308` **plus die lokalen versionierten
Änderungen dieses Builds**. Der Basiscommit allein reproduziert Build 019 nicht.
Vorhandene Arbeit und die Disconnect-Patches 0061/0050 wurden erhalten.

Sinopia-Inhaltsrevision: `7eecec186248f7aa177afc2997c1893c8c0f9208`.
Nach der finalen Patch-Anwendung sind die generierten Arbeitsrepositories sauber:

| Arbeitsrepository | HEAD |
| --- | --- |
| Minecraft | `1eb539af86cb9ac8eec4813f100c95f464a5aae1` |
| Server | `ca548472169e1d8e1420eff33342b5dd43512b25` |
| API | `c2ce14c10dbeb294ec10992d2b8966997d6fd0ec` |

Insgesamt werden 65 Minecraft-, 52 Server- und 15 API-Feature-Patches angewendet.
Kleine Paper-Basisänderungen einschließlich APIs stehen direkt versioniert in
Sinopia. Ihre zusätzliche [Sinopia-Patchsicherung](../upstream-patches/sinopia/README.md)
enthält alle 43 Basisdateien. Vorwärts gegen den unveränderten Sinopia-Index und
rückwärts gegen den fertigen Arbeitsbaum geprüft; diese Sicherung wird nicht
doppelt im normalen Build angewendet.

| Neue Sicherung | SHA-256 |
| --- | --- |
| [Minecraft 0062: Moonrise](../folia-server/minecraft-patches/features/0062-Backport-October-Moonrise-optimisations-preserving-r.patch) | `b1cedf3abcb387182d36aef0d097c2056b7097d6b87590c2f49a889d10227965` |
| [Minecraft 0063: Migrationen](../folia-server/minecraft-patches/features/0063-Migrate-hidden-item-components-and-embedded-structur.patch) | `5884e530ef8380940e05e430e52ff217616b62121fbbaf0f5adcff114f5be5bf` |
| [Minecraft 0064: Rezeptkataloge](../folia-server/minecraft-patches/features/0064-Publish-runtime-recipe-catalogs-atomically-and-refre.patch) | `039abd96767fba959f7133bdd49040a7f0f36ec953b3b0bcec187e7321b39761` |
| [Minecraft 0065: Cube-Ownership](../folia-server/minecraft-patches/features/0065-Stop-sulfur-cube-ignition-and-explosion-work-after-o.patch) | `36adec45697555b68f7e6b5486d2071859d597e7c7648a95e2f295d4149a50a8` |
| [Server 0051: Adapter](../folia-server/paper-patches/features/0051-Adapt-batched-recipe-APIs-iterators-and-potion-reset.patch) | `c4f526e8021327852ef15b1c40b437e9488fe24100809169a837ac31e9862b2b` |
| [Server 0052: Regressionen](../folia-server/paper-patches/features/0052-Test-October-Moonrise-typed-APIs-data-migrations-and.patch) | `569a9d0d36ef0f7409adad6dde2c96539798c6753239ad8ce285ac23dc134249` |
| [API 0015: Folia-Vertrag](../folia-api/paper-patches/features/0015-Document-Folia-recipe-writer-ownership-and-queued-cl.patch) | `9195e524198ad5a44cd12ec714e8d0f634e6d950cb6df8a171c75554e38fbded` |
| [Sinopia-Basisdiff](../upstream-patches/sinopia/2026-10-11-paper-backports.patch) | `d945a265e2305b8a1cf3c1d39e22bad99ca25c9fa83c02f0ef649d3c59b2f5c0` |

## Build und Qualitätsprüfungen

Ausgeführt auf Windows 11 mit Java 25.0.3 und Gradle 9.8.0:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-25.0.3'
.\gradlew.bat rebuildPaperApiFeaturePatches :folia-server:rebuildMinecraftFeaturePatches :folia-server:rebuildPaperServerFeaturePatches --console=plain --no-configuration-cache --max-workers=2 --no-parallel
.\gradlew.bat buildTessera --console=plain --no-configuration-cache --max-workers=2 --no-parallel
```

Finaler normaler `buildTessera`-Durchlauf: **BUILD SUCCESSFUL in 11m 32s**.
Die Server-Tests wurden frisch ausgeführt; unveränderte API-Tests werden aus
dem regulären Gradle-Up-to-date-Stand übernommen. Checkstyle, Bad-Call-Scanner
und bestehende Qualitätsprüfungen bleiben aktiv. Das Testplugin wurde danach
separat für seinen finalen nativen Testablauf kompiliert; die Server-JAR wurde
dabei nicht neu gepackt oder verändert.

| Prüfung | Ergebnis |
| --- | --- |
| Server-Suite | 183 Suiten, 10.369 Fälle, 87 bestehende Skips, 0 Fehler/Failures |
| API-Suite | 42 Suiten, 529 Fälle, 2 bestehende Skips, 0 Fehler/Failures |
| Checkstyle-Helfer | 3 Suiten/Fälle, 0 Fehler/Failures |
| Neue Portierungsregressionen | 22 Fälle: Moonrise 9, typisierte APIs 3, Migrationen 5, Rezeptkatalog/Bootstrap 5 |
| Vollständige API-Beispiele | 14 Java-Klassen gegen die tatsächliche 019-API und Abhängigkeiten kompiliert |
| API-/Changelog-Validator-Tests | 57 bestanden, keine Skips/Fehler |
| Öffentliche API-Referenz | 21 flache Artikel; Metadaten, Größenlimits und 59 Artikel-/Ankerlinks gültig |
| Neue Release-/Build-/Nachweisverweise | 102 lokale Datei-/Artikel-/Ankerlinks gültig; acht archivierte native Ergebnisdateien gegen den finalen JAR-Hash geprüft |

Damit 10.901 Java-Testfälle, davon **10.812 ausgeführt und bestanden** und
89 reguläre Skips. Keine Assertion wurde zur Umgehung eines Produktfehlers
abgeschwächt. Negative Tests erzeugen gezielt Fehlerdiagnosen.

Der erste native Start zeigte einen echten Bootstrap-Guard-Fehler. Er wurde vor
dem finalen Build behoben und regressionsgeprüft: nur der tatsächliche
Konstruktor-Thread darf vor Start des Server-Threads den initialen Rezeptkatalog
aufbauen. Fremde Threads erhalten keine vorzeitige Schreibberechtigung.
Eine anfänglich kombinierte Gradle-Ausführung mit dem zusätzlichen Beispielcheck
löste eine äußere Task-Abhängigkeitsdiagnose aus; normale Builds und der separate
Beispielcheck sind erfolgreich, ohne Qualitätsprüfungen zu umgehen.

Die globale Changelog-Prüfung trifft weiterhin auf den unveränderten historischen
Formatfehler in `docs/builds/26.2/0.0.1.md`. Die neue Release-Datei und ihre
lokalen Links werden separat geprüft; historische Releases bleiben unverändert.

## Finale ausführbare JAR

[tessera-server-26.3.build.019-beta.jar](../build/libs/tessera-server-26.3.build.019-beta.jar)

Absoluter Pfad:
`C:\Users\hunte\IdeaProjects\Tessera\build\libs\tessera-server-26.3.build.019-beta.jar`.
Größe **56.595.256 Bytes**. SHA-256:

```text
38a469ba075dd4fc9d36293b71aaa5d5ae077d27062d33da24994ad505d77cea
```

Alle folgenden nativen Nachweise beziehen sich exakt auf diese JAR. Die
Protokollclients zählen Verbindungen bis zum Test-PASS, nicht die erwartete
Trennung beim anschließenden regulären Serverstop.

## Native Abnahme

Isolierte, markierte Testwelten, freie Loopback-Ports und das separate Testplugin.
Keine produktiven Welten oder externen MCC-Testartefakte als Fixtures verwendet.
Die [archivierten Ergebnisse](test-evidence/26.3-019/) enthalten JAR-Hashes,
Assertions und Clientbeobachtungen.

| Fixture | Port | Ergebnis |
| --- | --- | --- |
| `paper-october-update-1791733331228` | 64727 | 55 native Assertions; zwei Logins, keine Kicks/Disconnects |
| `paper-october-1791732907521` | 50461 | 12 Assertions; zwei Logins, keine Kicks/Disconnects |
| `native-restore-smoke-1791732932636` | 58963 | Komponenten und öffentlicher Vertrag 1 bestanden; je Client ein Login, keine Kicks/Disconnects |
| `disconnect-lifecycle-1791733089185` | 59196 | Vier Verbindungsfälle; exakt beabsichtigte Kicks/Quits, keine Timeout-Wiederholungen |
| `native-pregeneration-1791733541386` | 55790 | Erstlauf und tatsächlicher Neustart-/Recovery-Lauf bestanden; je zwei Logins, keine Kicks/Quits vor PASS |
| `end-portal-dupe-false-1791733893655` | 52464 | Alle 46 Fälle mit deaktivierter Dupe-Option bestanden |
| `end-portal-dupe-true-1791733992544` | 58981 | Alle 46 Fälle mit aktivierter Dupe-Option bestanden |
| `end-portal-flight-true-1791733906918` | 52472 | Alle 20 Flugrichtungs-/Button-/Hopper-Fälle bestanden |

Alle sieben Runner (neun Serverstarts): regulärer Stop, Exitcode 0, kein Timeout. Keine unerwartete
Cross-Region-Ausnahme, Region-Tick-Ausfälle oder ConcurrentModificationException.
Windows-OSHI meldet auf diesem Testrechner fehlende englische Perflib-Zähler;
Offline-Testidentitäten und anfängliche Clientbewegung erzeugen weitere
Umgebungswarnungen. Das ist kein behaupteter vollständig warnungsfreier Loglauf.

### Moonrise, APIs und globale Ressourcen

Zwei gleich gesetzte NORMAL-Welten verwenden native Noise-/Surface-/Cave-
Generierung unter kontrolliertem initialem Tick-Freeze. Alle Blockpositionen
und Biome der beiden ausgewählten Chunkpaare müssen exakt übereinstimmen.
Dekoration/Mobs/Strukturen werden nur im weltlokalen **Testgenerator** deaktiviert;
ein weiteres Nether-Ziel nutzt den normalen nativen Generator. Zuvor scheiterten
unkontrollierte dekorierte In-Memory-Vergleiche sowohl auf 018 als auch auf 019;
ein gespeicherter 019-Vergleich unterschied sich an einem Grenz-Erzblock. Das ist
kein sauberer Noise-/Palettenvergleich und kein nachgewiesener gleicher Fehler
beider Builds. Produktionsgeneratoren wurden deshalb nicht geändert.

Zwei tatsächlich unterschiedliche Spielerregionen prüfen typed PDC, Waffen-
Kopie, Physikgetter und Post-Effects. Native Cube-Cancellation, Entfernung im
Ignite-/Prime-Callback und Dimensionswechsel im Prime-Callback bestehen. Die
alte native Instanz tickt nach Abgabe ihres Owners nicht weiter. Equipment-
Fähigkeiten werden auf gewöhnlichen Vanilla-Ticks abgewartet; die Fixture
behauptet keine garantierte Übernahme transienter Fuse-/Cache-Werte beim Transfer.

Globale Batch-Rezepte, ownergebundene Discovery, abgewiesene Regionsschreiber
und Duplikat-Batches ohne Teilmutation sind nativ geprüft. Entfernen, Resources-
und Advancement-Resets laufen während Freeze; der neue Katalog folgt einem
Spieler ins Nether. Die echten Clients empfangen jeweils **5 Rezeptpakete und
5 RecipeBook-Resets**, außerdem 3 beziehungsweise 4 Post-Effects-Pakete.

### Bestehende Spielerdaten- und Gameplay-Pfade

Die October-Regressionsfixture prüft Shelf-/Bundle-Obfuscation, Disk-/Unload-
Reload-Erhalt verschachtelter Items, explizite Blocksnapshots, Todesattribution
und Passagier-Spawns parallel auf zwei nativen Besitzern.

Der Restore-Runner übt erneut den echten öffentlichen Prepare/Apply/Complete-
Vertrag 1 aus: wiederholtes Load/Resave, frische Stores, Rollback, Transfer-Veto,
Admission-/Writer-Drain, Windows-Publikationsfehler, fehlende Save-Einträge,
verbundene tote/schlafende Spieler und erhaltene Zielwelt-Generatorbindungen.
Pro Client 8 Stats- und 12 Advancement-Resets, je ein Login und kein Reconnect.

Die Disconnect-Regressionsfixture prüft bei Freeze einen abgebrochenen Kick,
einen angenommenen Kick mit verzögertem Netty-Write, echten unbeantworteten
Keepalive in Play/Configuration und einen normalen Client-Quit. Kein wiederholt
abgespielter Timeout-Kick; Cleanup genau einmal und Global-Maintenance läuft weiter.

Die native Pregen-Fixture prüft Dimension-Auswahl/Berechtigung, generationfreie
FULL-Skips, Pause/Resume/Cancel, Status ohne alte Historie, parallele native
Generierung und Ticket-Retirement bei Freeze, Runtime-Unload und Snapshot-
Koordination. Gezielt injizierte Save-/Checkpoint-Fehler werden genau zugeordnet
und anschließend erfolgreich wiederaufgenommen. Stop mit zugelassener Arbeit,
tatsächlicher Neustart, Paused-Recovery und Revalidierung bestehen. Die echte
TPS-Kapazitätsanzeige zeigt 400% für vier konfigurierte Tick-Threads bei Freeze.

Die unveränderten Endportal-Fixtures bestehen erneut mit **112 nativen Fällen**:
Dupe-Option an/aus, parallele Quellregionen, Landen/Transfer/Drop, beide
Transferrichtungen, Veto/Umleitung/Entfernung und zwölf Drachenei-Fälle je
Einstellung. Dracheneier bleiben vom zusätzlichen Duplizierungspfad ausgeschlossen;
andere gültige Falling Blocks behalten ihre bisherige optionale Quellkopie.
Fünf Materialien fliegen in vier Ankunftsrichtungen weiter, Buttons erzeugen
native Drops und Hopper nehmen genau ein Zielitem auf. UUID/Wrapper und
Ankunftsrotation bleiben erhalten. Keine zusätzlichen Portal-Testfehler.

Nach dem Build ausgeführte Runner, jeweils gegen die oben gehashte JAR:

```powershell
$java = 'C:/Program Files/Java/jdk-25.0.3/bin/java.exe'
$jar = 'build/libs/tessera-server-26.3.build.019-beta.jar'
node smoke-tests/paper-october-update/run.mjs $java $jar
node smoke-tests/paper-october/run.mjs $java $jar
node smoke-tests/native-player-restore/run.mjs $java $jar
node smoke-tests/disconnect-lifecycle/run.mjs $java $jar
node smoke-tests/native-pregeneration/run.mjs $java $jar
$env:JAVA_TOOL_OPTIONS = '-XX:ActiveProcessorCount=4'
node smoke-tests/end-portal-duplication/run.mjs $java $jar
$env:TESSERA_SMOKE_MODE = 'end-portal-flight'
node smoke-tests/end-portal-duplication/run.mjs $java $jar
Remove-Item Env:TESSERA_SMOKE_MODE
Remove-Item Env:JAVA_TOOL_OPTIONS
```

Die letzten Umgebungsvariablen galten ausschließlich in den Testprozessen;
keine dauerhafte JVM-Einstellung des Nutzers wurde geändert. Der Restore-Lauf
wurde ebenfalls mit isoliert begrenzter CPU-Erkennung ausgeführt. Die neue API-
und Pregen-Fixture konfigurieren vier Scheduler-Threads; die älteren Portal-
und Restore-Fixtures behalten ihren vorhandenen Default-Threadumfang, prüfen
aber tatsächlich getrennte Regionsbesitzer.

Nach Archivierung wurden ausschließlich die in diesem Auftrag erzeugten,
markierten Fixture-Welten, redundanten Laufzeitbibliotheken und temporären
Test-Checkpoints entfernt: **2.372.394.395 Bytes (2,21 GiB)**. Ergebnisdateien,
Assertions, Logs, Konfigurationen, native Restore-Nachweise und ein verifizierter
Mojang-Launcher-Cache bleiben erhalten. Die entfernten Wegwerfwelten sind nicht
wiederherstellbar, können aber mit den Runnern neu erzeugt werden. Keine
Produktionswelt, Nutzer-Notiz, ältere fremde Fixture, Quell-/Patchdatei oder
`chunks-2026-10-10_16.47.27.txt` wurde entfernt; die finale JAR bleibt unverändert.

## Update- und Integrationsgrenzen

**Rezeptschreiber müssen global laufen.** Das gilt auch für bisherige Einzel-
APIs, Iterator-Remove und Potion-Mix-Mutationen. Region-/Async-Schreiber werden
abgewiesen. `breaking: true` in der Changelog bezeichnet diese verschärfte
Threadregel, keine neue Minecraft-/Protokollversion. Siehe
[Rezepte](api/recipes.md), [PDC](api/persistent-data.md) und
[Entity-APIs](api/entity-and-tree-compatibility.md).

Display-ID-Stabilität gilt für den laufenden Katalog, nicht als neue Zusage
über einen vollständigen Datapack-/RecipeManager-Austausch. `resetRecipes`
bleibt der bestehende Reload-Pfad. Client-Refresh-APIs liefern kein Future und
bestätigen nicht die Zustellung an alle Clients.

Kein quantitativer Performance-Benchmark, keine visuell gerenderte Vanilla-
Client-Abnahme und keine Vollprüfung beliebiger produktiver Plugins.
MCC/MVE/TAB/LuckPerms beziehungsweise der tatsächliche SMP-Pluginstack wurden
hier nicht zusammen geprüft. Native Restore-Tests ersetzen diese Folgeabnahme
nicht; neue APIs machen nicht Folia-fähige Plugins nicht automatisch sicher.
