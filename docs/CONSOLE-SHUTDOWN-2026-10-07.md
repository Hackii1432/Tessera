# Build 017 Windows Konsolenshutdown

Stand: 07.10.2026. Tessera `26.3-017-beta`, Minecraft/API `26.3`, Java 25.
Der Fehler `Terminal has been closed` beim normalen `stop` ist behoben.
Konsoleneingabe, Farben und asynchrones Logging bleiben aktiviert. Die Buildnummer
bleibt unverändert; Sinopia, Abhängigkeiten und öffentliche APIs sind unverändert.

Dieser Bericht belegt die hier genannten Konsolentests auf seiner eigenen JAR.
Die später gebaute Datei mit zusätzlicher Whitelist-Korrektur und deren Hash
steht im [Whitelist-Nachtrag](BUILD-26.3-017.md#whitelist-nachtrag-vom-07102026).
Die nativen Windows-Terminal-Tests dieses Berichts wurden dort nicht erneut ausgeführt.

## Ursache und Korrektur

Der bisherige `DedicatedServer.onServerExit()` startete mit `System.exit()`
die JVM-Shutdown-Hooks, bevor die Log-Warteschlange geleert war. JLine schloss
sein Windows-Terminal parallel zum Log4j-Dispatcher. Der Dispatcher griff
anschließend auf das bereits geschlossene Terminal zu. Die geerbte
Konsolenintegration ließ diesen Wettlauf zu; MCSM ist zur Reproduktion nicht nötig.

Tessera beendet nun nach dem bestehenden Regions-, Welt-, Spieler- und
Dateischreiber-Shutdown zunächst Log4j, schließt danach das Terminal und ruft
erst dann `System.exit()` auf. Der bestehende Exitcode bleibt erhalten.
`LoggerShutdown.shutdownLogging()` ist synchronisiert und wiederholungssicher:
der normale Exitpfad und der JVM-Hook führen die Bereinigung nicht doppelt aus.
Bei einem fehlgeschlagenen Log4j-Flush wird das Terminal nicht geschlossen;
ein erneuter Bereinigungsaufruf bleibt möglich.

Das betrifft den Abschluss des Serverprozesses, nicht das Tickverhalten,
Pregen, Chunk-Erzeugung oder Spieler-Restore. Keine Fehlermeldung wurde durch
einen Logging-Filter oder das Abschalten des Terminals versteckt.

## Code und Patches

Branch `ver/26.3.x`, Root-Basiscommit
`752dced66a794e6617406c7eca131ae86bef658d` **plus die vorhandenen lokalen
Änderungen und diese beiden neuen Patches**. Kein Commit oder Push im Hauptrepository.
Sinopia bleibt `cebdb10d9418432a18b920f7f2e902a8d03a43f4`.

- [Minecraft Patch 0058](../folia-server/minecraft-patches/features/0058-Drain-logging-before-dedicated-server-JVM-exit.patch):
  `aa9a46b0a4d40535720d2cc76ae9f50cdb40270fac8d024c4fe2650a9fd21100`.
- [Server und Testpatch 0047](../folia-server/paper-patches/features/0047-Serialize-logging-shutdown-and-test-terminal-close-o.patch):
  `4985490c96e37fd45a11365cb8c073724ddb51b1730d6747770e77ca8fd0137f`.

Die bestehenden Feature-Rebuild-Aufgaben exportierten die Änderungen;
`buildTessera` wendete sie erneut erfolgreich an. Generierte HEADs nach der
finalen Anwendung: Minecraft `6a4ffb33e3fa62a2f6bd5bfc8dc563ca4368aa47`,
Server `26de4bd4d131dc8878a3493a487c1823cabd3051`; beide Arbeitsbäume sauber.
Die vorherigen TPS-/Pregen-Patches bleiben erhalten.

## Build und Regressionstests

```powershell
.\gradlew.bat buildTessera --console=plain --no-configuration-cache --max-workers=2 --no-parallel
```

Finaler vollständiger Durchlauf: **BUILD SUCCESSFUL**, 8 Minuten 3 Sekunden,
Java 25.0.3, Gradle 9.8.0, Windows 11. Server: **10.266 Fälle**, 87 bestehende
Skips, 0 Fehler/Failures. Die unveränderten API-Tests sind UP-TO-DATE:
529 Fälle, 2 bestehende Skips, 0 Fehler/Failures. Vorhandene Qualitätsprüfungen
einschließlich Bad-Call-Scan bleiben aktiv; Checkstyle-Hilfstests aus dem Cache.

Die fünf neuen Tests sind im regulären `Normal`-Suitefilter ausgeführt:
Flush vor Terminal-Close, wiederholter Aufruf, Fehler beim Terminal-Close,
fehlgeschlagener Flush mit Retry, konkurrierende Bereinigung und die Reihenfolge
vor sämtlichen `System.exit`-Instruktionen des Exitpfads. Der erste Build
bestand die vorhandenen Tests; danach wurde die fehlende Suitezuordnung der
neuen Tests ergänzt, erneut als Patch exportiert und der vollständige Build
mit allen fünf neuen Tests wiederholt.

## Native Windows Prüfung

Ein isolierter Server mit vier Tickthreads, drei Vanilla-Dimensionen und dem
vorhandenen Testplugin lief über Windows-ConPTY. Der echte
`org.jline.terminal.impl.ffm.NativeWinSysTerminal` und der JLine-Reader waren
aktiv. Der normale `stop` erzeugte beim Plugin-Disable 512 nummerierte
Logmeldungen. Ein separater Log4j-Statuslistener erfasste Appenderfehler auch
nach Plugin-Disable, unabhängig von `latest.log`.

| Ausführbare Datei und Prüfung | Ergebnis |
| --- | --- |
| Vorheriger Build, SHA-256 `037a380…223c9` | Fehler exakt reproduziert: vier TerminalConsole-Ausnahmen mit geschlossenem Terminal, trotz Exit 0 und vorherigem Speicherabschluss |
| Finale JAR, normaler Stop | Exit 0; alle 512 Meldungen vorhanden; Welt-/Spieler-/RegionFile-I/O-Abschluss; keine Terminal-/Appender- oder Regionsausnahme |
| Dieselbe finale JAR nach Neustart, `tick freeze`, dann Stop | Gleiches erfolgreiches Ergebnis; ohne zusätzlichen Native-Access-Flag |

Ein Zwischenbuild bestand ebenfalls den Windows-Test. Er hat einen anderen
Hash und ist kein zusätzlicher Nachweis für die finale Datei. Zwei vorbereitende
DumbTerminal-Läufe dienen nur der Konsolenkonfiguration, nicht der Windows-Abnahme.
Die [Fixture-Anleitung und strenge Prüfung](../smoke-tests/console-shutdown/README.md)
beschreiben den Test; der Prüfer weist den alten Build ohne erwarteten Fehler
zurück und akzeptiert einen behobenen Build nicht als reproduzierten Negativfall.

## Ausführbare Datei und Nachweise

`build/libs/tessera-server-26.3.build.017-beta.jar`, **55.618.118 Bytes**, SHA-256:

```text
99c167a1e3b6a44590479d8b9d3206db4e11239a3c3ade825b31abfd0855be79
```

Der [JSON-Nachweis](test-evidence/26.3-017/console-shutdown.json) enthält
Quellstand, Patch- und JAR-Hashes sowie getrennte Vorher-, Zwischen- und
Endprüfungen. Unveränderte Logs, Statusdateien und der neue JUnit-Bericht liegen
unter `build/reports/native-fixtures/console-shutdown-20261007/`.

Nach Archiv-Hash-, Marker-, Pfad- und Reparse-Prüfung wurden nur die beiden
beendeten, selbst angelegten Testserver mit ihren wegwerfbaren Welten und
Serverkopien entfernt: **465.127.685 Bytes**, rund **444 MiB**. Logs und
Prüfberichte, produktive Daten und die finale ausführbare JAR bleiben erhalten.

Die Prüfung deckt den normalen Stop und Neustart mit aktivem Windows-Terminal
ab, nicht Force-Kill, Stromausfall oder ein vorzeitig von Fremdcode geschlossenes
Terminal. MCSM selbst und der konkrete Produktiv-Pluginstack wurden nicht
integriert; der MCSM-Startbefehl benötigt für diesen Fix keine neue JVM-Option.
Die bereits vorhandenen lokalen OSHI-Registry- und JVM-Warnungen sind davon
unabhängig und unverändert. Die Native-Stop-Fixture enthält keine verbundenen
Spieler und ersetzt keine erneute MCC-/MVE-/Restore-Abnahme.

Die früheren [NBT-Nachweise](BUILD-26.3-017.md) und
[TPS-/Pregen-Nachweise](TPS-PREGEN-STATUS-2026-10-07.md) gehören zu ihren jeweiligen
älteren JAR-Hashes, nicht automatisch zur jetzt gebauten Datei.
