---
version: 0.0.10
title: "26.3 – Nativer Spieler-Restore: Prüfbuild"
description: "Tessera Build 010 – native Restore-Komponenten und verbundene Clienttests; vollständiger MCC-Seamless-Load noch nicht freigegeben"
date: 2026-09-27
minecraftVersion: "26.3"
status: alpha
breaking: false
tags:
  - Tessera
  - Player-Restore
  - Region-Threading
  - Prüfbuild
releaseUrl: "https://github.com/Hackii1432/Tessera/"
downloadUrl: "https://home.mosaikdev.com"
---

## Beschreibung

### Native Speicher- und Regionsanbindung

- Native Spielerdatei-, Statistik- und Fortschrittszugriffe an gezählte Speicherzulassungen und Cache-Generationen angebunden.
- Laufende Login-Vorbereitung und Runtime-Snapshot-Saves in diese Zulassung aufgenommen. Neue Runtime-Snapshots bei geschlossener Restore-Schranke kontrolliert zurückgewiesen.
- Interne Restore-Aufgaben auf dem tatsächlichen Entity-Owner ausgeführt und deren Unteraufgaben in den Stage-Abschluss aufgenommen. Caller-Cancellation gibt intern laufende Arbeit nicht vorzeitig frei.
- Spieler-Gameplay bei geschlossener nativer Schranke zusätzlich angehalten. Vanilla-Tick-Freeze allein lässt Spieler weiter ticken. Verbindungspflege und Transferbestätigungen bleiben möglich.

### Spielerzustand und Client

- Einen nativen Komponentenpfad zum Vorprüfen und Ersetzen von Spielerzuständen ergänzt, einschließlich kopierter NBT-/JSON-Daten und Datenfixierung.
- Inventar, Endertruhe, XP, Gesundheit, Hunger, Attribute, Effekte, Fähigkeiten, Spielmodus, Rezepte und PDC ersetzt; nicht mehr gespeicherte additive Einträge entfernt.
- Native Spielerinstanz, Verbindung, Entity-Scheduler sowie Inventar- und Endertruhen-Wrapper beibehalten.
- Statistik-Maps ersetzt und entfernte Client-Werte explizit auf Null gesetzt. Fortschritte und Trigger ersetzt und auch bei leerem Zustand einen Client-Reset gesendet.
- Restore-Teleports mit einem auf das konkrete Ereignis und die Operation begrenzten Scope versehen. Veto und Umleitung werden als Fehler behandelt; der Abschluss wartet auf die Client-Bestätigung.
- Enderperlen-Laden um nachvollziehbare Unteraufgaben erweitert und den Zielchunk vor dem World-Add asynchron vorbereitet. Perlenzustand wird auf der Zielregion dekodiert.

### Eigene native Tests

- Einen separaten, wiederholbaren Testserver-Lauf mit freien Loopback-Ports, neuen Testwelten und zwei verbundenen Protokollclients ergänzt.
- Wiederholte Zustandsersetzung über getrennte Regionen und Dimensionen, frische Defaults, entfernte additive Werte, Wrapperidentität und Teleport-Veto geprüft.
- Eigene Enderperlen auf dem Ziel-Owner wiederhergestellt, wiederholt ohne zusätzliche registrierte Perlen ersetzt und beim frischen Spielerzustand entfernt.
- Echte Runtime-Snapshots vor und nach der Ersetzung erstellt; gespeicherte komprimierte Spieler-NBT-Dateien und die drei Store-Pfade geprüft.
- Login-/Kick-/Disconnect-Zähler sowie tatsächlich empfangene Statistik-Nullwerte und Fortschritts-Resets als JSON-Nachweis erfasst.
- Auf der finalen 010-JAR mit zwei verbundenen Clients bestanden: jeweils ein Login, kein Kick, kein Disconnect. Dies prüft den nativen Komponentenpfad, nicht die noch fehlende öffentliche Restore-Transaktion.
- Regressionstests für Speicherzulassungen, Scope-Reihenfolge, Owner-Queue, Unteraufgaben, Retirement und die Trennung von Gameplay-/Verbindungspaketen ergänzt.

### Buildstand und Prüfgrenzen

- Buildnummer auf `010-alpha` angehoben. Minecraft/API `26.3`, Java 25 und die bisherigen Buildwerkzeuge beibehalten.
- Die Wolfs-KI-Korrekturen aus Build 009 erhalten. MCC, MVE und Sinopia nicht verändert.
- Änderungen als eigene Minecraft-/Server-Feature-Patches gesichert; ihre erneute Anwendung anhand identischer Quellbaum-Hashes geprüft.
- `buildTessera` einschließlich Tests und Qualitätsprüfungen erfolgreich ausgeführt und die ausführbare `tessera-server-26.3.build.010-alpha.jar` erzeugt. Server: 10.150 erfasste Tests, keine Fehler, 87 übersprungen. API: 529 Tests, keine Fehler, zwei übersprungen.
- **Vertrag 0 beibehalten.** Die öffentliche Prepare/Apply/Complete-Dateitransaktion samt vollständigem Disconnect-/Shutdown- und Crash-Recovery ist noch nicht fertig. Dieser Build ist ausdrücklich kein freigegebener MCC-Seamless-Load-Fix.
- Native Komponententests nicht als bestandene MCC-/MVE-Integration ausgewiesen. Vollständiger Store-Rollback, Fahrzeug-/Schulterentity-Abnahme und Generator-/Biombindungen nach Challenge-Reset sind nicht abgenommen.

Konkrete Build-, Patch- und JAR-Nachweise:
[Build 010](BUILD-26.3-010.md). Der genaue Implementierungsstand ist im
[Restore-Status](mcc-player-restore-status.md) dokumentiert.
