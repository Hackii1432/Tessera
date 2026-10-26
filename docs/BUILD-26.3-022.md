# Build 022 – Paper-/Moonrise-Integration mit Folia-Anpassungen

Stand: 26.10.2026. Tessera `26.3-022-beta`, Minecraft/API `26.3`, Java 25.

## Quellbasis und Umfang

Ausgangscommit auf `ver/26.3.x`:
`c1f9128c388cea89c3ef479dff6091fe780c685b`.
Der Build benötigt zusätzlich die versionierten Arbeitsbaumänderungen dieses
Auftrags. Der Basiscommit allein enthält das Update nicht. Es wurde kein
automatischer Commit oder Push im Tessera-Hauptrepository erstellt.

Der fachliche Vergleich umfasst 54 Paper-Commits nach
`99867f8f9207d3164696e469435a067330473743` bis
`e415f5e030435d3108d7b623b41b9e47901e9d12`.
Die [vollständige Zuordnung](PAPER-INTEGRATION-2026-10-26.md) unterscheidet
übernommene Korrekturen/APIs, beide Moonrise-Updates, bereits gleichwertige
Funktionen und Paper-eigene Metadaten. Papers Stable-Promotion wird nicht als
Tessera-Stable-Freigabe übernommen. MCC und MVE bleiben unverändert.

## Dauerhafte Patchsicherung

- [Minecraft 0069](../folia-server/minecraft-patches/features/0069-Integrate-October-Paper-and-Moonrise-updates-with-re.patch)
  integriert die nativen Paper-/Moonrise-Änderungen mit Folia-Anpassungen.
- [Minecraft 0070](../folia-server/minecraft-patches/features/0070-Revalidate-bundle-air-and-sorting-callbacks-on-curre.patch)
  prüft neue Bundle-/Luft-/Sortier-Callbacks erneut und finalisiert deaktivierte
  Regionsaufgaben.
- [Server 0062](../folia-server/paper-patches/features/0062-Guard-October-entity-APIs-and-test-native-chunk-meta.patch)
  sichert explizite Entity-Besitzerprüfungen und native Daten-/Dateitests.
- [Server 0063](../folia-server/paper-patches/features/0063-Test-October-API-ownership-recipe-priority-and-vanil.patch)
  ergänzt Rezeptpriorität, Ownership-/Generierungsregressionen, Explorer-Map-
  Grenzen und die Anpassung bestehender Tests an die echten neuen Signaturen.

Der bestehende Rebuild-Workflow erzeugt insgesamt 70 Minecraft- und 63 Server-
Feature-Patches. Zwei Minecraft-Quellpatches begleiten die aktualisierten ATs.
Der Server-Basispfad und der Logging-Patch wurden auf die neue Sinopia-Basis
abgeglichen; Folia-Respawn und der idempotente Shutdown bleiben enthalten.

Die zusätzliche [Sinopia-Sicherung](../upstream-patches/sinopia/2026-10-26-paper-backports.patch)
umfasst 68 geänderte/neue Basisdateien. Sie ist vorwärts gegen einen separaten
Index des Ausgangscommits und rückwärts gegen die aktualisierten Quellen geprüft.
Der normale Git-Index wurde dafür nicht ersetzt. Nicht auf die bereits
aktualisierte Basis erneut anwenden.

SHA-256 der Basissicherung:

```text
7950643359c357d5917c23114f1304313c572df41c08bf2b4679f888a13f6c5f
```

## Gezielte Java-Regressionen

Der [gezielte Lauf](test-evidence/26.3-022/targeted-tests.log) besteht mit 38
Tests, ohne Failures oder Errors. Die sechs Ergebnisdateien liegen unter
[targeted](test-evidence/26.3-022/targeted/).

| Testklasse | Fälle | Nachweis |
| --- | ---: | --- |
| `LoggerShutdownTest` | 7 | Drain vor manuellen JLine-Hooks vor Terminal-Close; einmalige, serialisierte Bereinigung; Hook-Removal ohne null-JVM-Hook |
| `PaperMainRuntimeTest` | 13 | Bestehende Laufzeitregressionen, Codec-Aufrufkontexte und weiterhin begrenzter Adventure-Locale-Cache |
| `PaperOctober22ChunkStateTest` | 4 | Fehlend/partiell/vollständig, alte/verschobene/neue Datenversion, kein synchrones I/O auf Tick-/Netty-Threads, außergewöhnlicher Future-Abschluss bei Lifecycle-Ablehnung |
| `PaperOctober22NbtAndRegionTest` | 5 | Alle Tagarten/Modified-UTF, Vanilla-DataInput-Vergleich, alle abgeschnittenen Präfixe, Heap-/Tiefe-Limit, echte normale/externe Region-Dateien und Header-Reparatur mit erhaltenem Backup |
| `PaperOctober22OwnershipTest` | 3 | Spieler-Handle umgeht neue Owner-Guards nicht, lebende unveränderbare Regelansicht, Namespace-Cleanup, kein World-Read für veralteten/fremden Container |
| `RegionSafeRecipeCatalogTest` | 6 | Unveränderliche Publikation, atomare Duplikatablehnung, nicht recycelte Display-IDs, parallele Leser und Vanilla-/Runtime-Priorität |

Die vorherigen fehlgeschlagenen Versuche wurden nicht als erfolgreiche Tests
gezählt: zunächst alte Adapter-/Testsignaturen, dann leere gefilterte Hilfssuiten
und eine unpassende Registry-Fixture. Ein weiterer Lauf wurde abgebrochen,
nachdem sein Thread-Dump einen Hang in Windows-OSHI/JNA-Hardwareerfassung beim
Erstellen eines Crashreports für absichtlich fehlerhaftes NBT zeigte. Nur die
Hardwarebericht-Konstruktion innerhalb dieser Unit-Fixture ist isoliert;
Parser, Daten, Quoten und Assertions bleiben echt und unverändert streng.
Produktive Windows-Hardwarediagnostik wurde nicht umgeschrieben.

## Dokumentationsprüfung

Die 57 Validator-Tests bestehen. Die API-Referenz umfasst 23 Artikel; Metadaten,
Größengrenzen und 73 lokale Links bestehen. Die neue Referenz beschreibt unter
anderem den [Chunk-Statusvertrag](api/chunk-generation-state.md), neue
[Paper-APIs](api/paper-api-022.md), Scheduler-Abbruchzustände und Rezeptpriorität.

Alle **15 vollständigen Java-Beispiele** kompilieren gegen die gebaute 022-API.
Der neue Changelog besteht den bestehenden Parser und seine lokalen Links.
Der globale historische Changelog-Validator trifft weiterhin auf die unveränderte
Datei `docs/builds/26.2/0.0.1.md` mit nicht doppelt quotierten YAML-Strings;
historische Releases wurden nicht außerhalb dieses Auftrags umgeschrieben.

## Vollständiger Build und ausführbare Datei

Der [vollständige `buildTessera`-Lauf](test-evidence/26.3-022/buildTessera.log)
besteht nach erneuter Anwendung aller Patchschichten in **12 min 4 s**,
einschließlich API-/Server-Kompilierung, Javadoc, Checkstyle und Bad-Call-Prüfungen.
Das separate Testplugin ist nicht Bestandteil der produktiven Server-JAR.

| Modul | Fälle | Bestanden | Regulär übersprungen | Failures / Errors |
| --- | ---: | ---: | ---: | ---: |
| Server | 10.434 | 10.347 | 87 | 0 / 0 |
| API | 529 | 527 | 2 | 0 / 0 |
| Checkstyle-Modul | 3 | 3, aus dem unveränderten Gradle-Cache | 0 | 0 / 0 |
| Gesamt | 10.966 | 10.877 | 89 | 0 / 0 |

Die vier neuen Testklassen laufen auch in diesem unbeschränkten Durchlauf:
Chunk-Status 4, NBT/Region-Dateien 5, Ownership 3, Generierung 2 Fälle.
Zusätzlich bestehen die vier übernommenen Cookie-Tests und alle 18 bestehenden
Navigationsfälle. Ausgewählte vollständige Ergebnisdateien liegen unter
[full-build](test-evidence/26.3-022/full-build/).

Datei: `build/libs/tessera-server-26.3.build.022-beta.jar`.
Größe: **56.726.475 Bytes**.

SHA-256:

```text
d65513f45a1f8a94ac642f3a11ffbe3f6c01abff0561147729e9124344944532
```

Die generierten Quellen sind nach der erneuten Patch-Anwendung sauber:

- Minecraft: `0c18292f103443f83a659c9bb1da499ea6e50c82`.
- Server: `5f5edb1bd31363d5ad22434a699a3b2346c20ab3`.
- API: `6cae6c3a4664fcb97825a5ac82203efc3beb231c`.

Dies sind Köpfe der generierten Quellrepositories, keine neuen Root-Commits.
Die dauerhafte Grundlage bleibt die versionierte Basis plus Patchdateien.

## Native Prüfungen auf derselben finalen JAR

Alle folgenden Läufe verwenden den oben genannten unveränderten Server-Hash,
eigene Loopback-Ports und Java 25. Die Testclients sprechen das echte Protokoll,
rendern aber keine Welt. Wiederholte Einzelprüfungen sind nicht mit ebenso vielen
verschiedenen Spielszenarien gleichzusetzen.

| Prüfung | Tatsächliches Ergebnis |
| --- | --- |
| [Paper-/Moonrise-Lauf](test-evidence/26.3-022/native-october.json) | 86 Einzelprüfungen, zwei nachgewiesene Regionsbesitzer, parallele reale Terrain-/Biomvergleiche, neue Entity-APIs, echte Hopper-Auswahl und verschachtelte fluidlose Bucket-Ergebnisse. Alle vier Scheduler erreichen nach Callback-Abbruch `CANCELLED`. Geladene und ungeladene Chunk-Metadaten sowie Freeze-I/O funktionieren ohne Generierung. Rezept- und Post-Effect-Pakete werden tatsächlich auf beiden Clients beobachtet, auch nach Dimensionswechsel. Zwei Logins, kein Kick/Disconnect bis PASS, regulärer Stop, Exit 0. |
| [Wiederholung mit Welt-Unload](test-evidence/26.3-022/native-october-repeat.json) | 218 Einzelprüfungen. Zusätzlich 128 angenommene Metadata-Reads vollständig beendet und echter Runtime-Welt-Unload erfolgreich; nach CLOSED sind synchrone und asynchrone Abfragen ausdrücklich abgelehnt, kein Future bleibt offen. In diesem Lauf beendeten sich alle 128 Reads vor der Quiescing-Grenze; eine künstlich erzwungene In-flight-Ablehnung wird nicht behauptet. Wieder zwei Logins ohne Kick/Disconnect, Exit 0. |
| [Navigation](test-evidence/26.3-022/native-navigation.json) | 107 Einzelprüfungen erhalten die beiden gerittenen Pferde einschließlich UUID, Besitzer, Sattel, Rüstung und Reiterzuordnung. Fremde/fehlende alte Wegpunkte, Regions- und Dimensionswechsel werden weiter ohne Chunk-Nachladen bzw. Entity-Verlust behandelt. Zwei Logins, kein Kick/Disconnect bis PASS, Exit 0. |
| [Online-Restore](test-evidence/26.3-022/native-restore.json) | Öffentlicher Vertrag 1 und echte öffentliche Transaktion angenommen; wiederholtes Save/Load, Rollback, Client-Reset und vorbereitete Wüsten-/Rotsand-/Nether-Bindungen bestehen. Neu erzeugte Zielchunks behalten Biome und Generator-Layer statt der globalen Plains-Konfiguration. Beide Clients jeweils ein Login, null Kicks/Disconnects; 38 bzw. 35 bestätigte Positions-/Teleportpakete, je acht Null-Stat-Resets und zwölf Advancement-Resets. Exit 0. Kein MCC-/MVE-Produktplugin beteiligt. |
| [Pregenerierung und Neustart](test-evidence/26.3-022/native-pregeneration.json) | Beide bestehenden strengen Phasen bestehen mit je zwei Logins, null Kicks und Exit 0: Generation/Speicherung bei Freeze sowie persistierte Wiederaufnahme nach regulärem Neustart. Enthaltene absichtliche Fehler-/Abbruchfälle sind keine produktiven Weltfehler. |
| [Disconnect-Lifecycle](test-evidence/26.3-022/native-disconnect.json) | Vier Clients erreichen die erwarteten gezielten Kick-, Timeout-, normalen Quit- und Konfigurations-Abbruchpfade. Timeout-Logs sind auf die beiden erwarteten Spieler begrenzt; der normale Quit erzeugt keinen Kick. Dies ist ausdrücklich kein Null-Disconnect-Test. Exit 0. |
| [Bundled Spark](test-evidence/26.3-022/native-spark.json) | 28 Einzelprüfungen mit zwei Logins und null Kick/Disconnect. Zwei echte Profildateien, 42 Spielerantworten bei Freeze und je 62 Keepalives; Netty-/Alle-Threads-Profilpfad und native Zehn-Sekunden-Regionsmessungen bestehen. Der Gameplay-Sentinel bleibt während Freeze angehalten. Exit 0. |
| [Windows-ConPTY-Shutdown](test-evidence/26.3-022/console-shutdown.json) | Echter `NativeWinSysTerminal`, `windows=true`, `reader=true`; regulärer `stop`, alle 512 eindeutigen Disable-Logzeilen, vollständiges Welt-/Spieler-/I/O-Speichern, keine Terminal-/Appenderfehler, Exit 0. Der vorhandene strenge Verifier bleibt unverändert; kein DumbTerminal-Ersatznachweis. |
| [Worldgen-Startpfade](test-evidence/26.3-022/native-worldgen.json) | Auf einer weiteren Kopie ausschließlich der eigenen Testwelt: fehlende primäre Einstellungen werden rekonstruiert, der Server startet und stoppt vollständig. Eine anschließend absichtlich korrupte vorhandene Datei verhindert den Start mit der erwarteten Fehlermeldung und bleibt bytegleich erhalten. Der Fehlerpfad endet in rund neun Sekunden statt zu hängen. Exit 0 ist hier das Verhalten des abgefangenen Main-Fehlers, kein Nachweis eines erfolgreichen Starts. |

Die [Konsolen-Logdatei](test-evidence/26.3-022/console-shutdown-latest.log),
[Statusaufzeichnung](test-evidence/26.3-022/console-shutdown-status.txt) und
[Terminalprüfung](test-evidence/26.3-022/console-shutdown-checks.txt) erhalten
den Nachweis über Plugin-Disable hinaus. Die Worldgen-Logs unterscheiden den
[Missing-Fall](test-evidence/26.3-022/worldgen-missing.log) und den absichtlichen
[Corrupt-Fall](test-evidence/26.3-022/worldgen-corrupt.log).

Die [maschinenlesbare Zusammenfassung](test-evidence/26.3-022/build-summary.json)
verknüpft Quellbasis, Patch-Hashes, Testergebnisse und den identischen JAR-Hash
aller nativen Läufe. Die vollständigen MCC-/MVE-Integrationstests werden damit
nicht als ausgeführt ausgewiesen.

## Grenzen und Update

Der vollständige MCC-/MVE-/TAB-/LuckPerms-Stack, lange Produktionslast und ein
gerenderter Vanilla-/Konsolenclient wurden nicht erneut integriert getestet.
Linux-Unix-Sockets sind unter Windows nicht nativ abgenommen. Die vorhandenen
Verträge bleiben erhalten; dies ist kein Auftrag zur Änderung fremder Plugins.
Neue APIs und ein bestandener Test ersetzen keine allgemeine Folia-Freigabe
beliebiger Plugin-Hauptthreadannahmen.

Die vorhandenen defekten Windows-Perflib-Zähler melden sich beim Systemreport
weiterhin. Es wurden keine Registry-/Betriebssystemänderungen vorgenommen.
Die nativen Tests und der echte Terminal-Shutdown bestehen trotz dieser
gesonderten Umgebungsdiagnose. Es wird kein Beweis einer behobenen sporadischen
Internet-/Client-Latenz und keine pauschale Performance-Steigerung behauptet.

Vor einem Produktivupdate alle Welten und Plugin-Daten separat sichern,
regulär stoppen, die JAR ersetzen und neu starten. Kein erzwungener Reconnect
wird als Seamless-Erfolg gewertet. Zum Bauen weiterhin den vorhandenen Task
verwenden; zusätzliche Plugin- oder JVM-Pflichtoptionen werden nicht eingeführt.

## Build und Wiederholung

Vom Repository-Hauptverzeichnis mit Java 25:

```powershell
.\gradlew.bat buildTessera --console=plain --max-workers=2 --no-parallel
```

Testplugin und Dokumentationsbeispiele separat nach `buildTessera` bauen;
keine Testplugin-Aufgabe an dessen verschachtelten Aufruf anhängen:

```powershell
.\gradlew.bat :test-plugin:jar --console=plain --max-workers=2 --no-parallel
.\gradlew.bat -I scripts/check-api-examples.init.gradle.kts :folia-api:checkApiDocumentationExamples --no-configuration-cache --console=plain
```

Native Testwelten werden ausschließlich als markierte, isolierte Loopback-
Fixtures unter `build/` erzeugt. Produktive Welten und vorhandene Saves werden
nicht als Fixtures verwendet. Konkrete Testläufe sind kein pauschaler Nachweis
für jeden Drittplugin-Stack, Linux-Unix-Sockets, langes Vanilla-Gameplay oder
die Ursache sporadischer Spieler-Netzwerklatenz.

[Changelog 0.0.22](builds/26.3/0.0.22.md)
