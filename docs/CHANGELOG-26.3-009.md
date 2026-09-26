---
version: 0.0.9
title: "26.3 – Regionsichere Wolfsverteidigung"
description: "Tessera Build 009 – Besitzer-Kampf-KI gegen Regions- und Dimensionswechsel abgesichert"
date: 2026-09-26
minecraftVersion: "26.3"
status: alpha
breaking: false
tags:
  - Tessera
  - Region-Threading
  - Entities
  - Bugfixes
releaseUrl: "https://github.com/Hackii1432/Tessera/"
downloadUrl: "https://home.mosaikdev.com"
---

## Beschreibung

### Wolfsverteidigung und Besitzer-Kampf-KI

- Den Fehler `World mismatch: expected world_the_end but got world` bei der Besitzer-Verteidigung gezähmter Wölfe behoben. Ein Wolf fragt keine Schadensdaten seines Besitzers mehr ab, wenn dieser in einer anderen Region oder Dimension liegt.
- Auch das verwandte KI-Ziel „Besitzer greift ein Ziel an“ gegen fremde Regionen und Dimensionen abgesichert.
- Besitzer und Angreifer beziehungsweise Kampfziel vor dem Lesen ihrer Kampfdaten und vor der Zielauswertung auf Regionszuständigkeit, Weltzugehörigkeit und Entfernungsstatus geprüft.
- Dadurch den gezeigten Ausnahmeweg verhindert, der anschließend zur Entfernung des betroffenen Wolfs durch die allgemeine Entity-Fehlerbehandlung führte. Bereits zuvor entfernte Tiere werden nicht automatisch wiederhergestellt.

### Wechsel und Plugin-Events

- Gespeicherte Zielauswahlen vor dem Start erneut validiert. Ein inzwischen gewechselter Besitzer, ein anderes Kampfziel, ein neuer Kampfzeitstempel oder ein Regionswechsel verwendet keine veraltete Auswahl weiter.
- Nach dem Target-Event Regionszuständigkeit und Besitzeridentität erneut geprüft. Der Zeitstempel wird aus der geprüften Auswahl übernommen, ohne danach Kampfdaten eines inzwischen fremden Besitzers auszulesen.
- Neue Angriffe während eines Plugin-Callbacks werden nicht versehentlich zusammen mit der ursprünglichen Auswahl als bereits verarbeitet markiert.
- Normale Verteidigung und Angriffsunterstützung in derselben Region, die bestehenden Zielgründe, Target-Event-Abbrüche, Sitzverhalten, Schadens-Timeout und `NO_WOLF_RETALIATION` beibehalten.
- Besitzerbindung und allgemeines Haustier-Teleportverhalten unverändert gelassen. Die Prüfung betrifft die Auswahl neuer Besitzer-Kampfziele; ein bereits laufender Kampf gegen ein weiterhin gültiges lokales Ziel wird nicht allein wegen der Abreise des Besitzers beendet.
- Keine globale Änderung an Entity-Referenzen oder regionslokalen Uhren vorgenommen und keine Thread-Schutzprüfung abgeschaltet.

### Patchpflege und Buildstand

- Korrektur in einem eigenen Tessera-Minecraft-Patch `0045` und Regressionstests in einem eigenen Server-Patch `0034` abgelegt.
- Buildnummer von `008-alpha` auf `009-alpha` angehoben und README sowie Build-Workflow aktualisiert.
- Minecraft `26.3`, Mache `26.3+build.1`, Java 25, Gradle `9.8.0`, Paperweight `2.0.0-beta.24` und die Plugin-API-Version `26.3` beibehalten.
- MCC, MVE und Sinopia unverändert gelassen.

### Automatisierte Prüfung

- Beide neuen Patches erneut angewendet und die erzeugten Quellen ohne Abweichung zum exportierten Stand geprüft.
- Vollständigen `buildTessera`-Durchlauf unter `009-alpha` in 7 Minuten 51 Sekunden erfolgreich abgeschlossen, einschließlich Patch-Anwendung, Tests, Checkstyle-/Bad-Call-Prüfungen und ausführbarer Paperclip-JAR.
- Alle `60` neuen Regressionstestfälle erfolgreich ausgeführt, ohne Fehler oder übersprungene Fälle. Geprüft wurden lokale Kämpfe, fremde Regionen und Dimensionen, entfernte und fehlende Entities, ungültig gewordene Auswahlen, Wechsel während Target-Callbacks, neue Kampfereignisse, Event-Abbrüche und bestehende Vanilla-Zielbedingungen.
- Server-Testsuite mit `10.140` erfassten Testfällen ohne Fehler oder Fehlschläge geprüft; `87` Fälle wurden übersprungen. Unveränderte API-Prüfergebnisse aus dem Gradle-Cache übernommen: `529` erfasste Fälle, keine Fehler, `2` übersprungen.
- Ausführbare Serverdatei unter `build/libs/tessera-server-26.3.build.009-alpha.jar` erzeugt. Das lokale Buildprotokoll liegt unter `build/owner-combat-full-build-2026-09-26.log`.

Tessera bleibt ein Alpha-Build. Die automatisierten Regionsprüfungen arbeiten
mit kontrollierten Entity- und Thread-Mocks; ein Live-Test mit Spielern und
Wölfen am Endportal war nicht Bestandteil dieser Prüfung.
