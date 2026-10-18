# Native Nachweise für Tessera 26.3-020-beta

Eigene isolierte Läufe vom 17.10.2026, keine produktiven Welten. Die JSON-Dateien
sind unveränderte Runner-Ergebnisse; die vollständigen Befehle, Quellen und
Prüfgrenzen stehen im [Buildbericht](../../BUILD-26.3-020.md).

Geprüfte JAR: `tessera-server-26.3.build.020-beta.jar`, 56.611.885 Bytes.
SHA-256: `67918a3bc6fd3a8572920ef644d463d83dc34347c799d591ca54c494965906d2`.

- [Standard-Start](spark-default.json), [28 Assertions](spark-default-checks.txt):
  zwei verbundene Protokollclients in getrennten Regionen, echter Clientbefehl,
  mehr als 60 Sekunden Freeze, Keepalives, Nethertransfer und zwei gespeicherte
  Protobuf-Profile mit tatsächlichen Stackframes. Keine Kicks/Disconnects vor PASS.
- [Früher Start](spark-early.json), [28 Assertions](spark-early-checks.txt): dieselbe
  native Abnahme mit unmittelbar aktiviertem eingebautem Backend.
- [Konfiguration aus](spark-disabled.json): keine API-Aktivierung, Diagnosebefehl
  vorhanden, keine Profildatei.
- [Plugin-Präferenz](spark-external-probe.json): bevorzugtes gleichnamiges
  Testplugin, kein zweites natives Backend. Kein Test des echten externen Spark.
- [Disconnect-Regression](disconnect-lifecycle.json): bestehende echte
  Play-/Configuration-/Netty-Pfade mit aktiviertem Spark bestehen. Beabsichtigte
  Kicks/Quits gehören zum Test und sind nicht mit den verbindungserhaltenden
  Spark-Profilläufen zu verwechseln.

Die Threadlisten belegen vier `Folia Region Scheduler Thread #…` und vier
`Netty NIO IO #…` im Alle-Thread-Profil. Default-Profile enthalten dagegen die
Tick-Worker und den Startthread, keine Netty-Threads. Regionsdaten werden als
ungefähr zeitgleiche Momentaufnahme, nicht als dauerhafte Worker-Zuordnung exportiert.

Die beiden `TEST-*.xml`-Dateien enthalten 17 neue Java-Fälle: 11 Statistik-/
Sampler-/Worldinfo- und 6 Sender-/Berechtigungsfälle. Darunter ist der wirkliche
gebündelte Spark-Window-Collector, nicht lediglich eine nachgebildete Hilfsklasse.
Der abschließende vollständige Build besteht mit 10.826 ausgeführten
bestandenen Fällen, 89 regulären Skips und ohne Failures/Errors.

Die tatsächlichen Testprofile werden lokal unter
[build/verification/spark-020/profiles](../../../build/verification/spark-020/profiles/)
aufbewahrt. Die JSON-Ergebnisse enthalten die Profil-Hashes. Ihre früheren
Fixture-Pfade dürfen nach Archivierung entfernt werden; die Quellen und
Prüfergebnisse bleiben erhalten.

Protokollclients rendern keine Vanilla-Oberfläche. Die Tests sind weder ein
Produktionslastbenchmark noch eine Abnahme des vollständigen Pluginstacks.
