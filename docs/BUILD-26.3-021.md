# Build 021 – Regionsichere Navigation

Stand: 20.10.2026. Tessera `26.3-021-beta`, Minecraft/API `26.3`, Java 25.

## Fehler und Korrektur

Der gemeldete Pferdepfad verwies auf einen über 1.100 Blöcke entfernten,
nicht mehr besessenen Wegpunkt. Ein veralteter Pfad erklärt diesen Zustand;
welcher Reit- oder Transferablauf ihn ursprünglich erzeugte, ist aus dem
Log allein nicht nachweisbar.
`PathNavigation.tick()` fragte dessen Bodenhöhe über `Level.getBlockState()` ab.
Bei einem fehlenden Chunk erreichte das den synchronen Chunk-Lader. Folia lehnte
den Zugriff vom falschen Besitzer ab; `Level.guardEntityTick()` entfernte das
Pferd anschließend mit `discard`.

Build 021 prüft Besitzer und Verfügbarkeit, bevor bestehende Wegpunkte verfolgt
oder für die Bodenhöhe verwendet werden. Nach einer Fortschaltung des Pfads wird
der neue Wegpunkt erneut geprüft. Bei breiten Mobs werden der Knoten und die
versetzte Entity-Zielposition berücksichtigt.

Weitere Zugriffe desselben Navigationssystems werden ebenfalls abgesichert:

- `moveTo(Path, ...)` prüft vor `trimPath()` sämtliche gelieferten Knoten,
  da Vanilla dabei auch bereits besuchte Knoten auf Blöcke prüft.
- Verzögerte Neuberechnungen verwerfen nicht mehr besessene oder ungeladene
  Zielpositionen, ohne einen Chunk-Ladevorgang zu starten.
- `PathNavigationRegion` übernimmt ausschließlich besessene geladene Chunks
  in seinen Lesecache. Fremde Chunks werden auch dann nicht übernommen, wenn
  sie bereits geladen sind.
- Der eigene Tick-Pfad fliegender Navigation und die Ersatzsteuerung
  kletternder Mobs verwenden dieselbe Validierung.

Eine ungültige Route wird auf dem tatsächlichen Mob-Besitzer verworfen.
Veraltete Ziel-/Timeout-/Neuberechnungsdaten und die KI-Bewegungsanweisung
werden zurückgesetzt. Reitereingaben und physikalische Bewegung werden nicht
mit `Mob.stopInPlace()` überschrieben. Gültige lokale Routen behalten die
Vanilla-Pfadanpassung und Bodenhöhenberechnung.

Der allgemeine Entity-Fehlerschutz und Folias Thread-Prüfungen bleiben aktiv.
Es gibt weder eine Weiterleitung des Mob-Zustands an einen fremden Besitzer
noch globale oder synchrone Wartearbeit für einen fehlenden Navigationschunk.

## Quellen und Patchworkflow

Ausgangscommit auf `ver/26.3.x`:
`027304d3c31f6ca5be089a70f7d53d5fb9c224ba`.

Der Build enthält die Arbeitsbaumänderungen dieses Auftrags. Der Basiscommit
allein enthält den Fix nicht; kein automatischer Commit oder Push im
Tessera-Hauptrepository wurde durchgeführt.

- [Minecraft-Patch 0068](../folia-server/minecraft-patches/features/0068-Guard-stale-navigation-paths-with-region-ownership-c.patch)
  sichert die Navigations- und Cache-Korrekturen.
- [Server-Testpatch 0061](../folia-server/paper-patches/features/0061-Test-navigation-ownership-and-no-load-guarantees.patch)
  sichert die 18 Java-Regressionstests.
- [Native Testklasse](../test-plugin/src/main/java/dev/tessera/smoke/NativeNavigationSmoke.java)
  und [Runner](../smoke-tests/native-navigation/run.mjs) gehören zum separaten
  Testplugin, nicht zum produktiven Serverplugin-Stack.

Die Patches wurden über die bestehenden `rebuildMinecraftFeaturePatches`- und
`rebuildPaperServerFeaturePatches`-Tasks erzeugt. Sinopia, API-Vertrag, MVE,
MCC und Netty-Konfiguration werden durch diesen Auftrag nicht geändert.

Nach der erneuten Anwendung der gesamten Patchkette liegen die generierten
Quellen unverändert auf folgenden Köpfen:

- Minecraft: `70960c69c03a661c96a3d00cdec1b3037cae7c76`.
- Paper-Server: `5ce14695718804856bfd52434c08d2ccd5531ce3`.

Das sind Köpfe der generierten Quellrepositories, nicht neue Commits im
Tessera-Hauptrepository. Die dauerhafte Grundlage sind dessen Patchdateien.
Die Kette enthält jetzt 68 Minecraft- und 61 Server-Feature-Patches.

## Ausführbares Artefakt

Der vollständige `buildTessera`-Durchlauf nach der Patchsicherung und erneuten
Patch-Anwendung besteht in **10 min 46 s**, einschließlich Server-/API-Tests,
Checkstyle-Aufgaben und Bad-Call-Prüfungen.
Das [vollständige Build-Log](test-evidence/26.3-021/buildTessera.log) ist archiviert.
Die [maschinenlesbare Zusammenfassung](test-evidence/26.3-021/build-summary.json)
enthält auch Patch-Hashes, Quellenköpfe und Testzähler.

Datei: `build/libs/tessera-server-26.3.build.021-beta.jar`.
Größe: **56.612.628 Bytes**.

SHA-256:

```text
7ab2677fd3179a13edf57a21001526de6cc1241ce30e414c2d64ad219b1f1f91
```

## Gezielte Regressionstests

18 Java-Fälle bestehen ohne Failures, Errors oder übersprungene Tests:

- Fremde geladene und ungeladene Wegpunkte sowie eigene ungeladene Chunks.
- Entity-Breite an einer Chunk-Grenze und negative Koordinaten.
- Erneute Prüfung nach Wegpunktfortschaltung; kein vorheriger Boden-Read.
- Unveränderte lokale Bodenhöhenberechnung, Pfadanpassung und Steuerung.
- Keine Änderung von Reitereingaben oder physikalischem Bewegungsvektor.
- Keine ausstehende Neuberechnung eines ungültigen Ziels.
- Ablehnung angelieferter fremder Pfade vor dem Lesen besuchter Knoten.
- Keine Chunk-Abfragen bei fehlendem Pfad; falscher Mob-Besitzer scheitert
  vor der ersten Navigationsmutation.
- Kein fremder geladener Chunk im Cache der Neuberechnung.
- Eigene und fremde Ersatzsteuerung kletternder Mobs sowie die beiden
  Prüfstellen im eigenen Tick-Pfad fliegender Navigation.

Die native Negativkontrolle auf der unveränderten Build-020-JAR reproduziert
den Fehler mit zwei verbundenen Protokollclients: Ein gerittenes, gezähmtes
Pferd mit Sattel und Rüstung erhält nach einem gültigen lokalen Pfad einen
alten entfernten Wegpunkt. Der echte `PathNavigation.tick()` löst innerhalb
des echten `Level.guardEntityTick()` die Thread-Ausnahme und Entfernung aus.
Beide Clients bleiben dabei verbunden. Dieser Lauf ist ein Fehlernachweis,
keine erfolgreiche Abnahme des alten Builds.

## Vollständige Java-Prüfungen

Der vollständige Server-/API-Testlauf umfasst **10.933 Fälle** in 186 Server-
und 42 API-Ergebnisdateien: **10.844 bestanden**, **89 regulär übersprungen**,
keine Failures oder Errors. Alle 18 neuen Navigationsfälle laufen auch in
diesem unbeschränkten Durchlauf erfolgreich, nicht nur im gezielten Aufruf.
Zusätzlich werden die drei unveränderten Checkstyle-Modultests aus dem
Gradle-Cache übernommen.

Die [JUnit-Datei des vollständigen Durchlaufs](test-evidence/26.3-021/TEST-NavigationRegionSafety-full-build.xml)
und die [gezielte JUnit-Datei](test-evidence/26.3-021/TEST-NavigationRegionSafety-targeted.xml)
halten die neuen Fälle getrennt fest.

## Native Abnahme der finalen JAR

Der [native Lauf](test-evidence/26.3-021/navigation-final-021.json) und die
[Wiederholung mit frischen Testwelten](test-evidence/26.3-021/navigation-repeat-021.json)
verwenden die oben gehashte ausführbare JAR mit je zwei verbundenen
Protokollclients, vier Tick-Threads und Kompressionsschwelle 256.
Beide bestehen mit jeweils **107 Einzelprüfungen**, **zwei Logins**,
**keinem Kick oder Disconnect vor PASS**, anschließend normalem Shutdown
und Exitcode 0. Die Einzelprüfungen enthalten wiederholte Identitäts-/
Ausrüstungsprüfungen; das sind nicht 107 verschiedene Szenarien.

| Prüfung | Tatsächlich nachgewiesen |
| --- | --- |
| Originaler Fehlerpfad | Dasselbe entfernte, ungeladene Ziel wie im Fehlerbericht wird in der originalen Pferdenavigation durch `Level.guardEntityTick()` verarbeitet. Der alte Pfad wird verworfen, der Chunk nicht geladen und das Pferd nicht entfernt. |
| Zwei Regionen | Zwei gerittene Pferde auf nachweislich getrennten Besitzern erhalten gleichzeitig Wegpunkte in der jeweils anderen geladenen Region. Keine fremden Boden-Reads oder Entity-Entfernung. |
| Fortschaltung und fehlender Chunk | Ein nach dem Folgen neu erreichter fremder Wegpunkt sowie ein eigener, noch ungeladener Chunk werden vor der Boden-Abfrage abgelehnt. |
| Eingehender Pfad und Cache | Fremder angelieferter Pfad vor `trimPath()` abgelehnt; realer Neuberechnungscache enthält keinen fremden geladenen Chunk. |
| Gültige Navigation | Echte lokale Vanilla-Bodenhöhenabfrage und KI-Bewegungsanweisung bleiben erhalten. |
| Gerittener Regionswechsel | Pferd und verbundener Reiter erreichen einen neuen Besitzer. Alte Wegpunkte werden danach ebenfalls verworfen. |
| Dimensionswechsel | Zweites Pferd und verbundener Spieler wechseln in die Oberwelt. Der Spieler sitzt dafür ausdrücklich ab und anschließend wieder auf; die Navigation verwendet den Ziel-`ServerLevel`. |
| Erhaltung | UUID, gezähmter Besitzer, Sattel, Pferderüstung und Reiterzuordnung bleiben erhalten, auch nach weiteren natürlichen Entity-Ticks. |

Der erste Teststart der finalen JAR innerhalb der Netzwerksandbox ist
ausdrücklich **nicht bestanden** und wird [separat dokumentiert](test-evidence/26.3-021/navigation-sandbox-blocked.json).
Die Sandbox verweigerte schon Javas internem HTTP-Selector die Loopback-
Verbindung (`Permission denied: connect`); es gab keinen Login und keine
ausgeführte Navigationsprüfung. Derselbe unveränderte JAR-Hash besteht mit
der benötigten lokalen Netzwerkfreigabe.

Ein gerenderter Vanilla-Client, langes manuelles Reiten und der produktive
Mehrspieler-/Plugin-Stack wurden nicht nachgespielt. Die exakte Entstehung
des ursprünglichen veralteten Pfads bleibt aus dem Log offen; der gemeldete
Absturz-/Entfernungspfad selbst ist auf 020 reproduziert und auf 021 geprüft.
Die Windows-Umgebung meldet bereits beim Systemreport fehlende Perflib-
Hardwarezähler. Es wurden keine Registry-Änderungen vorgenommen und keine
Hardwarezähler-Abnahme behauptet.

## Build und Wiederholung

Vom Repository-Hauptverzeichnis mit Java 25:

```powershell
.\gradlew.bat buildTessera --console=plain --max-workers=2 --no-parallel
```

Nach dem Build kann der isolierte Navigationslauf wiederholt werden:

```powershell
.\gradlew.bat :test-plugin:jar --console=plain --max-workers=2 --no-parallel
node smoke-tests/native-navigation/run.mjs 'C:/Program Files/Java/jdk-25.0.3/bin/java.exe' 'build/libs/tessera-server-26.3.build.021-beta.jar'
```

Die Testplugin-Aufgabe separat nach `buildTessera` ausführen, nicht an dessen
verschachtelten Gradle-Aufruf anhängen.

Der Runner erstellt markierte Wegwerfwelten unter `build/`, kopiert und hasht
die angegebene JAR und bindet ausschließlich an einen freien Loopback-Port.
Produktive Welten und Saves werden nicht verwendet. Die Negativkontrolle kann
mit der alten 020-JAR und `--expect-bug` ausgeführt werden.

## Dokumentationsprüfung

Die 57 Tests der Dokumentationsvalidatoren und die unveränderte API-Referenz
mit 21 Artikeln und 59 lokalen Links bestehen. Der neue Changelog-Eintrag
wird zusätzlich separat mit dem bestehenden Changelog-Parser geprüft.

Der globale Changelog-Validator stößt wie bereits bei Build 020 auf die
unveränderte historische Datei `docs/builds/26.2/0.0.1.md`, deren YAML-Strings
nicht doppelt quotiert sind. Historische Releases wurden nicht außerhalb
dieses Auftrags umgeschrieben.

[Changelog 0.0.21](builds/26.3/0.0.21.md)
