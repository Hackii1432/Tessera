# Build 020 – Regionsicherer Spark-Profiler

Stand: 17.10.2026. Tessera `26.3-020-beta`, Minecraft/API `26.3`, Java 25.

## Implementierung

Der gebündelte Spark-Kern `1.10.191` wird durch einen internen Tessera-Adapter
aktiviert. Es wird weder ein zusätzliches Produktionsplugin benötigt noch eine
Paper-/Moonrise-Migration durchgeführt.

- Keine gemeinsamen Paper-Tick-Hooks oder Tick-Reporter aus parallelen Regionen.
  Schedulerberichte liefern die Messwerte; ein echtes zusätzliches
  10-Sekunden-Fenster ersetzt keine anders benannte 15-Sekunden-Näherung.
- Default-Sampling erfasst dynamische Folia-Worker und den Startthread.
  Alle-Thread-Aufnahmen können Netty gesondert erfassen. Ein Worker wird nicht
  dauerhaft mit einer bestimmten Region gleichgesetzt.
- Regionale TPS, MSPT-Mittelwert/Minimum/Median/Maximum, Utilisation und
  publizierte Zähler stehen in `tessera-regions`. Globale Wartung und Freeze
  sind getrennt gekennzeichnet. Caches enthalten keine World-/Region-/Entity-
  Referenzen; Topologielocks werden nur zum Kopieren von Regionsreferenzen gehalten.
- Spark-TPS bedeuten das Minimum gemessener aktiver Regionen, nicht eine
  globale Tickrate. Nicht verfügbare gepoolte MSPT-Perzentile bleiben abwesend.
  Spark fragt die Dauerstatistik intern auch ohne Duration-Unterstützung ab;
  der dafür vorgesehene Null-Rückgabewert wird benutzt und am echten
  `WindowStatisticsCollector` getestet.
- Unsichere Live-Enumeration von Chunk-Entities und Blockentity-Maps ist
  deaktiviert. Die Abwesenheit dieser Details erzeugt auch beim frühen Start
  keine unnötige Global-Thread-Warteschleife.
- Berechtigungen und Senderidentität werden im ursprünglichen Kontext kopiert.
  Spielerantworten folgen dem aktuellen Besitzer über die vorhandene native
  Wartungsqueue, auch bei Freeze. Console-/RCON-Antworten laufen über globale
  Koordination. Retirement und Deaktivierung verwerfen verspätete Antworten.
- Vorhandene Konfiguration und externe Plugin-Priorität werden beachtet.
  Der normale Startup-Pfad registriert nach der Plugin-Erkennung den
  Fallback-Befehl; der frühere Vorher-Aufruf konnte das Backend ohne Befehl starten.

## Quellen und dauerhafte Patches

Ausgangscommit auf `ver/26.3.x`:
`0e31a2cab94f1f8e06921a7872b962bd027d25f7`.

Der Build enthält zusätzlich die Arbeitsbaumänderungen dieses Auftrags. Der
Basiscommit allein reproduziert ihn nicht. Es wurde kein automatischer
Root-Commit oder Push durchgeführt.

Die nach dem Abschlusslauf regenerierten Arbeitsquellen stehen auf
`ec8310ccb37c1499b0020550157d69d1a202c8d0` (`paper-server`) und
`fbae92a5a7d20d21adb8f88b6733f487b02c7c50` (Minecraft-Quellen).

- Server-Feature-Patches **0053–0060** unter
  [folia-server/paper-patches/features](../folia-server/paper-patches/features/):
  Adapter, Statistik-/Sender-/Sampler-Regressionstests, Besitzerzustellung,
  Sendergleichheit, Nullable-Dauerwerte und asynchron nicht verfügbare Weltdetails.
- Minecraft-Feature-Patches **0066–0067** unter
  [folia-server/minecraft-patches/features](../folia-server/minecraft-patches/features/):
  10-Sekunden-Berichte und korrekte Startup-Registrierung.
- `tesseraBuildVersion=020`, `tesseraBuildChannel=beta`; API-Patches und
  integrierte Sinopia-Basis wurden für diesen Auftrag nicht migriert.

Die vollständige Buildvorbereitung wendet die Patches erneut an. Änderungen
nur an generierten Quellen sind daher nicht die einzige Sicherung.

## Ausführbare Datei

[tessera-server-26.3.build.020-beta.jar](../build/libs/tessera-server-26.3.build.020-beta.jar)

Pfad:
`C:\Users\hunte\IdeaProjects\Tessera\build\libs\tessera-server-26.3.build.020-beta.jar`

Größe: **56.611.885 Bytes**.

SHA-256:
`67918a3bc6fd3a8572920ef644d463d83dc34347c799d591ca54c494965906d2`.

## Native Abnahme

Die [Nachweise](test-evidence/26.3-020/) enthalten unveränderte Runner-Ergebnisse,
Assertions, Regionsdaten, Threadnamen und die 17 neuen JUnit-Tests. Die vier
Spark-Läufe und der Disconnect-Regressionslauf verwenden die oben gehashte JAR.
Jeder Spark-Runner kopiert die JAR in seine eigene markierte Fixture und prüft
deren Hash. Testwelten sind neu, loopbackgebunden und keine produktiven Saves.

| Prüfung | Tatsächlich nachgewiesen |
| --- | --- |
| Standard-Start | Native API und nativer Fallback-Befehl ohne externes Spark-Plugin; 28 Assertions, zwei Logins, kein Kick/Disconnect vor PASS. |
| Regions-/Dimensionswechsel | Zwei echte voneinander getrennte Spielerregionen, anschließender Nethertransfer während laufender Aufnahme und Freeze; keine Cross-Region-Ausnahme. |
| Echter Clientbefehl | Ein unsigned Kommandopaket des verbundenen Clients erreicht den aktuellen Spielerbesitzer, nicht nur `Player.performCommand` im Testplugin. |
| Langer Freeze | Vorher nachweislich tickende ArmorStand-Entity bleibt über mehr als 60 Sekunden unverändert. Spieler-/Verbindungswartung läuft entsprechend Vanilla weiter; je Client 62 Play-Keepalives im Standardlauf. |
| Antworten bei Freeze | 42 beobachtete Profiler-Antwortpakete im Standardlauf; zustellbare Antworten auch nach dem Dimensionswechsel. |
| Reale Profil-Dateien | Gespeicherte Protobufs mit Stackframes, mindestens zwei 60-Sekunden-Spark-Fenstern, vier Tick-Workern und vier Netty-NIO-Threads. Zweite Aufnahme nur mit Default-Threads und erhaltener Spieleridentität. |
| Früher Start | `enable-immediately: true` mit `-Dpaper.preferSparkPlugin=false`; dieselben 28 Assertions, zwei Logins ohne Kick/Disconnect, zwei Profile. |
| Deaktivierung | `spark.enabled: false`: API nicht aktiv, Befehl mit Deaktivierungsdiagnose vorhanden, keine Profile. |
| Plugin-Priorität | Ein separates Testplugin namens `spark` wird bevorzugt; kein zweites natives Backend und unveränderte Command-Zuständigkeit. Die Probe ersetzt keine Abnahme des echten externen Spark-Plugins. |
| Shutdown | Normaler Stop bei noch aktivem dritten Sampler; Exitcode 0, kein Timeout und keine Terminal-Closed-/Ownership-Exception in den Spark-Läufen. |
| Disconnect-Regression | Bestehender nativer Lauf besteht mit aktivem gebündeltem Spark: abgebrochener/angenommener Kick, verzögerter Netty-Write, Play-/Configuration-Timeout und normaler Quit; keine wiederholten Timeout-Disconnects. |

Zwei lokale Protokollclients, vier Tick-Threads, Kompressionsschwelle 256,
Windows 11 und Oracle Java `25.0.3` wurden für die Spark-Läufe benutzt.
Aufnahmen wurden ausdrücklich lokal gespeichert und nicht hochgeladen.

Die ersten Entwicklungsdurchläufe wurden **nicht** als Erfolg gewertet: Sie
deckten die unbedingte Dauerabfrage im Spark-Sampler und den fehlenden
Fallback-Befehl auf. Beide Pfade wurden korrigiert. Für Freeze wird bewusst
eine nachweislich aktive Nicht-Spieler-Entity geprüft: Vanilla nimmt Spieler
vom normalen Entity-Freeze aus. Spark-Timeline-Fenster dauern in dieser Version
60 Sekunden; das ist nicht das 10-Sekunden-TPS-Messfenster.

## Build und Prüfkommandos

Der abschließende vollständige `buildTessera`-Durchlauf nach der
Startup-Korrektur besteht in **10 min 31 s**, einschließlich erneuter
Patch-Anwendung, Tests, Checkstyle und Bad-Call-Prüfungen. Die 185 Server-
und 42 API-Ergebnisdateien umfassen **10.915 Fälle**, davon **10.826 bestanden**
und **89 regulär übersprungen**; keine Failures oder Errors. Darin enthalten
sind alle 17 neuen Spark-Fälle.

Die 57 Tests der Dokumentationsvalidatoren und die bestehende API-Referenz
mit 21 Artikeln/59 Links bestehen. Der neue Release-Eintrag und die neuen
Build-/Bedienungs-/Nachweisdokumente wurden zusätzlich separat mit dem
Changelog-Parser und 58 lokalen Linkzielen geprüft.

Vom Repository-Hauptverzeichnis, mit Java 25:

```powershell
.\gradlew.bat buildTessera --console=plain --max-workers=2 --no-parallel
```

Für diese Abnahme wurden vorhandene Abhängigkeiten zusätzlich mit `--offline`
und `--no-configuration-cache` verwendet. Testplugin-Zusatzaufgaben nicht im
selben Aufruf an `buildTessera` anhängen: der verschachtelte Build und die
Patchvorbereitung treffen sonst auf Gradles implizite Abhängigkeitsprüfung.

Nach abgeschlossener Patchvorbereitung separat:

```powershell
.\gradlew.bat :test-plugin:jar :test-plugin:sparkPreferenceProbeJar --console=plain
$java = 'C:/Program Files/Java/jdk-25.0.3/bin/java.exe'
$jar = 'build/libs/tessera-server-26.3.build.020-beta.jar'
node smoke-tests/native-spark/run.mjs $java $jar
node smoke-tests/native-spark/run.mjs $java $jar --early
node smoke-tests/native-spark/run.mjs $java $jar --disabled
node smoke-tests/native-spark/run.mjs $java $jar --external-probe
node smoke-tests/disconnect-lifecycle/run.mjs $java $jar
```

## Grenzen und Dokumentationsprüfung

- Kein 18-Spieler-/32-Tick-Thread-Lastbenchmark und kein gerenderter Vanilla-
  Clienttest. Linux/async-profiler, RCON-Ausgabe und der produktive
  MCC-/MVE-/TAB-/LuckPerms-Stack wurden hier nicht integriert abgenommen.
- Die vollständige native Spieler-Restore-/World-Lifecycle-Abnahme aus älteren
  Builds wird durch diese gezielten Spark-Tests nicht erneut behauptet.
- Die isolierte Windows-Umgebung meldet bereits beim Vanilla-Systemreport
  fehlende Perflib-009-Zähler. Das wurde nicht durch Registry-Eingriffe kaschiert;
  Thread-/Stackaufnahmen funktionieren. Hardwarezähler sind damit kein separat
  bestandener Prüfpunkt.
- Tickmonitor, Tickfilter, gepoolte MSPT-Perzentile und Live-Weltdetails bleiben
  ausdrücklich begrenzt; siehe [Bedienung und Grenzen](spark-profiling.md).
- Der globale Changelog-Validator stößt bereits auf die unveränderte historische
  Datei `docs/builds/26.2/0.0.1.md` mit nicht doppelt quotierten YAML-Strings.
  Historische Metadaten wurden nicht außerhalb dieses Auftrags umgeschrieben.
  Der neue Eintrag wird separat mit demselben Parser und den lokalen Links geprüft.

[Changelog 0.0.20](builds/26.3/0.0.20.md)
