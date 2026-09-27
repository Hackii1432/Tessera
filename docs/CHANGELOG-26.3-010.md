---
version: 0.0.10
title: "26.3 – Nativer Seamless-Spieler-Restore"
description: "Tessera Build 010 – Restore-Transaktionen mit verbundenen Spielern, regionsicherem Welttransfer und frischem Rollback"
date: 2026-09-27
minecraftVersion: "26.3"
status: alpha
breaking: false
tags:
  - Tessera
  - Player-Restore
  - Seamless-Load
  - Region-Threading
  - Bugfixes
releaseUrl: "https://github.com/Hackii1432/Tessera/"
downloadUrl: "https://home.mosaikdev.com"
---

## Beschreibung

### Nativer Seamless-Load

- Den bisher nicht aktivierten Spieler-Restore durch eine native Prepare-/Apply-/Complete-Transaktion ersetzt und Vertrag `1` über `Bukkit.getPlayerRestoreService()` aktiviert.
- Gespeicherte Spielerzustände bei bestehender Verbindung in vorbereitete Zielwelten übertragen. Im Erfolgsfall weder Kick oder Reconnect noch künstliche Join-/Quit-Ereignisse erzeugt.
- Wiederholtes Laden, erneutes Speichern, frische Spieler ohne Save-Eintrag und Wiederherstellung aus einem unmittelbar zuvor erstellten Rollback-Store ergänzt.
- Native Spielerinstanz, Verbindung, Scheduler sowie Inventar- und Endertruhen-Wrapper beibehalten.

### Speicher, Regionen und Clientzustand

- Laufende Login-Zulassungen, Logout, Autosave, `saveData`, Runtime-Snapshots sowie Online- und Offline-Statistikzugriffe in die native Schreibschranke einbezogen. Alte Cache-Generationen können wiederhergestellte Dateien nicht überschreiben.
- NBT-/JSON-Daten, Weltverweise und Entity-Identitäten vor der Zustandsänderung geprüft und mit Vanilla-Datenfixierung verarbeitet. Die Quelle bleibt unverändert.
- Inventar, Endertruhe, XP, Gesundheit/Hunger, Attribute und Modifikatoren, Effekte, Fähigkeiten, Spielmodus, Respawn, PDC, Rezepte, Statistiken und Fortschritte einschließlich Triggern ersetzt. Entfernte additive Einträge bleiben nicht erhalten.
- Client-Resets einschließlich expliziter Statistik-Nullwerte und Fortschritts-Reset gesendet. Tote Spieler erhalten einen nativen Respawn-/Transfer-Reset bei unveränderter Verbindung.
- Restore-Arbeit auf den tatsächlichen Besitzern auch bei eingefrorenen Spielticks ausgeführt. Verbindungspflege und Transferbestätigungen bleiben aktiv; Region- und Global-Thread warten nicht synchron auf Transaktions-Futures.
- Teleport-Ereignisse an das konkrete Ereignis und die Operation gebunden. Veto, Umleitung und fehlende Client-Bestätigungen werden nicht als erfolgreicher Transfer ausgegeben.

### Fahrzeuge und Fehlerbehandlung

- Gespeicherte Fahrzeuggraphen samt Inventar, Schulterentities und Enderperlen wiederhergestellt. Vorhandene Kopien auf ihren Besitzern ersetzt statt mit veraltetem Zustand wiederverwendet.
- Regionsfremde Perlen beim Abmelden über die interne Owner-Queue entfernt. Restore-Abschlüsse berücksichtigen Unteraufgaben und Retirement auch bei Tick-Freeze.
- Die drei Player-Stores über vorbereitete, synchronisierte Verzeichnisbäume publiziert. Ursprüngliche Bäume und Rollback-Daten für Recovery erhalten; kurzzeitige Windows-Dateisperren begrenzt erneut versucht und dauerhafte Fehler zurückgemeldet.
- Apply und Complete mit eindeutiger Entscheidung und idempotenten Wiederholungen versehen. Abgebrochene Caller-Futures öffnen keine noch benötigte Schreibschranke.
- Beim Shutdown verbleibende native Arbeit erst nach dem Anhalten von Regionen und Chunk-Arbeit abgeschlossen. Nach Neustart keine alte Tessera-Transaktion eigenmächtig über eine MCC-Recovery-Entscheidung abgespielt.

### Eigene Server-/Clientprüfung

- Öffentliche Transaktion mit zwei tatsächlich verbundenen Protokollclients über getrennte Regionen und Dimensionen geprüft: wiederholter Load/Resave, exakter Zustand, frische Defaults, echter Rollback und Teleport-Veto.
- Im erfolgreichen Seamless-Lauf jeweils einen Login, keinen Kick und keinen Disconnect gemessen; echte Transfers, Statistik-Nullwerte und Fortschritts-Resets erfasst.
- Boot-Inventar, Schulterentity, Perlen, entfernte Attribute/Rezepte/Fortschritte/PDC, tote Spieler sowie konkurrierende Requests und verzögerte Writer geprüft.
- Echte Windows-Sperrfehler beim nativen Statistik-Save und bei der Store-Publikation ausgelöst und anschließend den nativen Rollback ausgeführt.
- In separaten Fehlerläufen Disconnect während Restore, bis Complete gesperrten Login sowie fehlenden Client-ACK mit Timeout und Rollback geprüft. Den absichtlichen Kick/Rejoin nicht als Seamless-Erfolg gezählt.
- Stop/Neustart nach Prepare, nach Apply, während Apply und nach Commit geprüft. Danach funktionierten neuer Restore und Save; keine alte Transaktion wurde nachgespielt.
- Unterschiedliche Zielwelt-Bindungen erhalten. Ein nach mehreren Restores neu erzeugter Chunk behielt seine Wüsten-/Rotsand-Generierung statt der globalen Ebenen-Einstellungen.

### Buildstand und Prüfgrenzen

- Build `010-alpha`, Minecraft/API `26.3`, Java 25 und die vorhandenen Buildwerkzeuge beibehalten. Wolfs-KI-Korrekturen aus 009 und Sinopia erhalten.
- Änderungen im bestehenden Tessera-Patchworkflow gesichert; MCC- und MVE-Produktcode nicht verändert.
- MCC 0.7.5 kann den unveränderten Vertrag `1` automatisch erkennen; kein zusätzlicher API-Pfad oder erzwungener Reconnect gehört zum nativen Erfolgsablauf.
- Die vollständige MCC-/MVE-/TAB-/LuckPerms-Integration bleibt eine gesonderte Folgeabnahme. Native Protokolltests ersetzen diesen Stack und eine visuelle Vanilla-Client-Prüfung nicht.

Exakter Quellcommit, JAR-Prüfsumme, vollständiger Build und finale Prüfungen:
[Build 010](BUILD-26.3-010.md). API und gemeinsame Folgeabnahme:
[Restore-Status](mcc-player-restore-status.md).
