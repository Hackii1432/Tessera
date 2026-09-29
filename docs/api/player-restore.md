---
title: "Nativer Spieler-Restore mit bestehender Verbindung"
description: "Vertrag 1 für Prepare, Apply, Rollback und Complete einschließlich Schreibschranke, Transfers und Recovery."
navTitle: "Spieler-Restore"
order: 80
updated: 2026-09-29
minecraftVersion: "26.3"
badge: "Referenz"
---

`Bukkit.getPlayerRestoreService()` beziehungsweise der entsprechende `Server`-
Getter liefert Tesseras öffentlichen `io.papermc.paper.world.PlayerRestoreService`.
Der konfigurierte native Server implementiert seit Build 010 **Vertrag 1**.
Vorherige reservierte Implementierungen mit Vertrag 0 sind nicht der aktuelle Stand.

## Umfang und Voraussetzungen

Der Service ersetzt den gemeinsamen Vanilla-Spielerstore und den Zustand
verbundener Spieler. Er erstellt nicht selbst die Ersatzwelten und ersetzt kein
Journal eines Challenge-/Weltverwaltungsplugins. Zielwelten müssen vorbereitet,
registriert und die gespeicherten Weltverweise bereits passend umgeschrieben sein.
Externe Plugin-Stores, UI-Schließung und Welttransaktion bleiben Aufgabe des Callers.

Alle Transaktionsmethoden sind nicht blockierend und von jedem Thread aufrufbar.
Die native Arbeit läuft auf den tatsächlichen Besitzern, I/O außerhalb der
Tickthreads. Kein synchrones Warten auf Region-, Global- oder Netzwerkthreads.
Fortsetzungen erhalten keine allgemeine Bukkit-Threadgarantie.

## Signaturen und Parameter

```java
int contractVersion();
CompletionStage<PlayerRestoreResult> prepareAsync(
    UUID operationId, Path sourcePlayers, Path rollbackPlayers, World fallbackWorld);
CompletionStage<PlayerRestoreResult> applyAsync(UUID operationId, boolean forward);
CompletionStage<PlayerRestoreResult> completeAsync(UUID operationId, boolean committed);
boolean isRestoreTeleport(PlayerTeleportEvent event, UUID operationId);
```

| Parameter | Vertrag |
| --- | --- |
| `operationId` | Nichtnull-UUID einer Operation; für alle Folgeschritte beibehalten, nicht für andere Parameter wiederverwenden |
| `sourcePlayers` | Unveränderliche Quellwurzel mit `data`, `stats`, `advancements`; keine aktive Store-Wurzel oder überlappende Ziele |
| `rollbackPlayers` | Neue, noch nicht existierende Wurzel für den unmittelbar vor Restore erfassten Rückweg |
| `fallbackWorld` | Aktive Zielwelt für frische Spieler ohne Quell-Save; kein bloßer Weltname |
| `forward` | `true`: Quelle anwenden; `false`: vorausgehende Arbeit abwickeln und frisch erfassten Zustand zurückspielen |
| `committed` | Dauerhaft getroffene Entscheidung, passend zum erfolgreich angewendeten Vorwärts-/Rollback-Zustand |
| `event` | Das konkret gerade dispatchte Teleport-Event; Scope nur dort und nur für die passende Operation gültig |

`contractVersion()` ist eine reine Abfrage; für den hier dokumentierten Adapter
exakt `1` erwarten. `0` bedeutet nicht verfügbar. Null-Operations-IDs können
synchron `NullPointerException` auslösen; fehlende Prepare-Argumente ergeben
`INVALID_REQUEST`. Pfad-, NBT-/JSON-, Datenversions- und Weltprüfung passiert
vor nativer Zustandsmutation. Die Quelle wird nicht verändert.

## Transaktion und Abschluss

| Schritt | Erfolgsstatus | Anschließend |
| --- | --- | --- |
| `prepareAsync(...)` | `PREPARED` | Frischer Rollback-Store verfügbar; benötigte Schreib-/Login-Schranke bleibt aktiv |
| `applyAsync(id, true)` | `APPLIED` | Quelle samt verbundenen Spielern und Transfers angewendet |
| Caller persistiert Commit | kein neuer Tessera-API-Aufruf | Eigene Welt-/Store-/Recovery-Entscheidung dauerhaft sichern |
| `completeAsync(id, true)` | `COMPLETED` | Passende Commit-Entscheidung bestätigt, Drain/Cleanup und Freigabe abgeschlossen |
| Alternativ `applyAsync(id, false)` | `ROLLED_BACK` | Vorangehende Arbeit beendet, frischer Rückweg angewendet |
| Caller koordiniert Welt-Rollback, dann `completeAsync(id, false)` | `COMPLETED` | Passende Rollback-Entscheidung abgeschlossen |

Prepare bezieht bereits zugelassene Logins, Logout, Autosave, explizites `saveData`,
Statistikwriter und Snapshot-/Welt-Lifecycle-Koordination ein. Alte Cache-Generationen
dürfen danach keine veralteten Dateien publizieren. Neue gewöhnliche Logins bleiben
während der Transaktion gesperrt; Verbindungspflege und interne Restore-Transfers
laufen auch bei Gameplay-Tick-Freeze weiter.

Apply ersetzt Inventar, Endertruhe, XP, Gesundheit/Hunger, Attribute/Modifikatoren,
Effekte, Fähigkeiten, Spielmodus, Respawn, PDC, Rezepte, Stats und Advancements
einschließlich Triggern und Client-Caches. Fehlende Werte erhalten frische Defaults,
entfernte additive Einträge verschwinden. Fahrzeuge, Schulterentities und Perlen
samt Eigentümer-/Weltverweisen werden ohne Wiederverwendung veralteter Kopien
wiederhergestellt. Native Spielerinstanz, Verbindung, Scheduler und Bukkit-Wrapper
bleiben erhalten; keine künstlichen Join-/Quit-Events.

Der Future-Abschluss umfasst die native Unterarbeit, konkrete Client-Transfer-ACKs
und Cleanup. `APPLIED` ist kein bloßes „Transfer eingeplant“. Erfolg benötigt keinen
Kick/Reconnect. Generator-/Biombindungen vorbereiteter Welten werden nicht durch
globale aktuelle Generatoreinstellungen ersetzt.

## Offline-Identität und gespeicherte Metadaten

Seit Build 012 verwendet der Preflight für bestehende Offline-Spieler den
geprüften, gegebenenfalls datenversionskonvertierten Quelldatensatz. Er erzeugt
keinen Login und schreibt keine Vorschau-Identität in den Player-Store.
Vorhandene Namen, Login-/Seen-/Played-Zeiten, unbekannte zusätzliche NBT-Tags
und PDC bleiben erhalten. Fehlende Identitätsfelder bleiben fehlend. Erlaubte
Welt-/Positionsanpassungen und die Datenversionskonvertierung bleiben bestehen.

Fehlende Statistik-/Fortschrittsdateien für Offline-Spieler werden nicht durch
leere Vorschau-Dateien ersetzt; reine JSON-Einträge erzeugen keine neuen
Spieler-NBT-Dateien. Dateikopien und vorbereitete NBT-Overlays erhalten das
ursprüngliche Änderungsdatum, das Bukkit bei fehlenden Zeitfeldern teilweise
als Rückfallwert verwendet. Ein Restore ist damit keine Offline-Spieleraktivität.
Für tatsächlich verbundene Teilnehmer gelten weiterhin native Zustandsaufnahme
und reguläre Speicherung; ihre laufende Verbindung und Login-Identität bleiben
erhalten. Spieler ohne Quell-Save erhalten weiterhin frische Gameplay-Defaults.

Bereits durch frühere Builds beschädigte Quellen werden nicht automatisch
repariert. Insbesondere ist `RestorePreview` kein verbotener Benutzername.
Belegte Korrekturen anhand derselben UUID, Backup und feldweiser Vorschau sind
im [Reparaturleitfaden](https://github.com/Hackii1432/Tessera/blob/ver/26.3.x/docs/restore-player-metadata-repair.md)
beschrieben. Der öffentliche Vertrag bleibt `1`; es ist kein zusätzlicher
MCC-/MVE-API-Aufruf erforderlich.

## Ergebnis und Fehlerstatus

```java
// Konstruktor und zusätzliche Methode des öffentlichen Records:
PlayerRestoreResult(UUID operationId, PlayerRestoreStatus status, String message);
boolean successful();
```

Alle drei Record-Komponenten sind nichtnull. Anders als Welt-Lifecycle-Resultate
besitzt dieses Ergebnis **kein `cause()`**. `successful()` gilt nur für
`PREPARED`, `APPLIED`, `ROLLED_BACK`, `COMPLETED`; zusätzlich Operation-ID und zum
Schritt passenden Status prüfen. Die Signaturen oben beschreiben den vorhandenen
Record, keine neue Plugin-Implementierung.

| Fehlerstatus | Einordnung |
| --- | --- |
| `UNSUPPORTED` | Kein verfügbarer nativer Vertrag |
| `INVALID_REQUEST`, `VALIDATION_FAILED` | Argumente, Pfade oder gespeicherter Zustand ungültig |
| `CONFLICT` | Parameter/Entscheidung passen nicht zur Identität oder Reihenfolge |
| `BUSY`, `PLAYER_BUSY` | Konkurrierende Transaktion oder nicht übernehmbarer Spielerzustand |
| `SAVE_FAILED` | Prepare-/Speicherarbeit fehlgeschlagen |
| `TRANSFER_FAILED` | Apply/Rollback beziehungsweise Transfer, Veto oder ACK fehlgeschlagen |
| `COMPLETE_FAILED` | Entscheidung/Cleanup/Freigabe nicht erfolgreich abgeschlossen |
| `CANCELLED`, `SERVER_STOPPING` | Abbruch beziehungsweise geordneter Serverstopp |

Zusätzlich synchrone Fehler und außergewöhnlich abgeschlossene Stages behandeln.
Keinen Fehlerstatus als Teil-Erfolg ausgeben und keine benötigte Schranke selbst
mit einer geratenen Complete-Entscheidung öffnen.

## Idempotenz, Disable und Recovery

Passende Wiederholungen beziehen sich auf denselben gespeicherten Zustand;
gegenläufige/späte Requests werden zurückgewiesen. Ein Rollback für eine unbekannte
ID setzt eine Abbruchmarkierung: Ein späteres Prepare derselben ID startet nicht
doch noch. Identitäten bleiben bis zum Serverneustart bekannt.

Caller-Cancellation betrifft nur dessen Future-Sicht; sie bricht keine benötigte
native Arbeit hart ab. Plugin-Disable öffnet die Schranke nicht pauschal. Anders
als bei einer normalen Chat-Rückmeldung dürfen Transaktionsentscheidungen und
Recovery-Arbeit nicht einfach als „später Callback“ ignoriert werden. Vor einem
geplanten Disable laufende Transaktionen kontrolliert abschließen, andernfalls
die dauerhafte Caller-Recovery vorsehen. Neue Plugin-Ticktasks während Freeze
oder nach Disable sind dafür kein verlässlicher Fortschrittspfad.

Vor `completeAsync(id, true)` muss der Caller seinen Commit dauerhaft protokolliert
haben. Nach dieser Entscheidung nicht wegen eines Complete-Fehlers automatisch
auf Rollback umschalten; passende Completion wiederholen oder gemäß Journal recovern.
Serverstop wickelt verbleibende native Arbeit nach dem Stillstand der beteiligten
Worker ab. Nach Neustart spielt Tessera keine alte Transaktion eigenmächtig nach;
der Caller entscheidet offline anhand seines Journals und erhaltener Stores.

## Teleport-Scope

`isRestoreTeleport(event, id)` nur **während des synchronen Handlers desselben
Events** aufrufen. Nach Rückkehr, auf einem anderen Thread oder für eine andere
Eventinstanz ist der Scope nicht gültig. Ein positives Ergebnis ist keine
allgemeine Teleporterlaubnis. Andere Plugins können weiterhin vetoieren oder
umleiten; ein unzulässiger Transfer wird nicht als erfolgreicher Apply verschleiert.
Ein Handlerbeispiel steht unter [Events](events.md#restore-teleports).

## Nachweisgrenzen

Der native Vertrag wurde mit verbundenen Protokollclients, Repeat Load/Resave,
Rollback, mehreren Regionen/Dimensionen, Entity-Anhängen und Recovery geprüft.
Gemeinsame MCC-/MVE-/TAB-/LuckPerms-Tests sowie visuelle Vanilla-Client-Prüfung sind
separate Abnahmen. Native Ergebnisse und Grenzen stehen im
[Build-010-Bericht](https://github.com/Hackii1432/Tessera/blob/ver/26.3.x/docs/BUILD-26.3-010.md).
