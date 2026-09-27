---
title: "Runtime-Welten entladen"
description: "Spielerbehandlung, reversible Timeouts und Freigabe von Weltdateien."
navTitle: "Welt-Unload"
order: 70
updated: 2026-09-28
minecraftVersion: "26.3"
badge: "Referenz"
---

Tessera entlädt Runtime-Welten nach geordnetem Drain und Storage-Close. Ein abgeschlossenes Future allein bedeutet noch keinen erfolgreichen Unload.

## Signatur und Parameter

```java
CompletionStage<WorldUnloadResult> unloadWorldAsync(
    World world, WorldUnloadOptions options
);
```

`world` muss eine registrierte Runtime-Welt dieses Servers sein; Startup-Welten bleiben geschützt. `options` bestimmt Save, Spielerbehandlung und Timeout. Aufruf von jedem Thread; `successful()` ist das Gate für freigegebene Handles, nicht ein bloß abgebrochenes Future.

## Optionen

Standardverhalten:

```java
WorldUnloadOptions options = WorldUnloadOptions.defaults();
```

Standardwerte:

| Option | Standard |
| --- | --- |
| Speichern | `true` |
| Spielerbehandlung | `FAIL` |
| Teleportziel | keines |
| Timeout | 90 Sekunden |

Ohne Speichern entladen:

```java
WorldUnloadOptions options = WorldUnloadOptions.builder()
    .save(false)
    .failWhenPlayersPresent()
    .timeout(Duration.ofSeconds(45))
    .build();
```

Spieler vor dem Unload asynchron teleportieren:

```java
WorldUnloadOptions options = WorldUnloadOptions.builder()
    .save(false)
    .teleportPlayers(lobbySpawn)
    .timeout(Duration.ofSeconds(45))
    .build();
```

Den Pfad vorher über `World#getWorldPath()` erfassen. Dateilöschung benötigt zusätzlich eine Prüfung des absoluten realen Zielpfads gegen einen ausdrücklich erlaubten, nicht primären Weltordner und gehört auf einen eigenen I/O-Executor. Keine rekursive Löschung aus unvalidierten Namen oder Keys.

Der Ordner darf niemals vor einem erfolgreichen Unload-Ergebnis gelöscht
werden. `successful()` ist insbesondere unter Windows das Gate für die
Dateihandle-Freigabe.

Beim Unload:

1. wird `WorldUnloadEvent` ausgelöst und eine Abbrechung respektiert,
2. werden neue Teleports, Tickets und Regionsaufgaben gesperrt,
3. werden Spieler geprüft oder per `teleportAsync` versetzt,
4. laufen ausschließlich die Regionen der Zielwelt aus,
5. werden Speicher abhängig von `save` geschrieben,
6. werden Chunk-, Entity-, POI- und Storage-Worker geschlossen,
7. wird die Welt aus allen Serverregistrierungen entfernt,
8. wird erst danach das Future abgeschlossen.

Primäre Serverwelten sind geschützt.

### `WorldUnloadResult`

Methoden:

```java
Status status();
String message();
@Nullable Throwable cause();
boolean successful();
```

Statuswerte:

| Status | Bedeutung |
| --- | --- |
| `SUCCESS` | Welt vollständig geschlossen und deregistriert |
| `UNSUPPORTED` | Capability nicht verfügbar |
| `SERVER_STOPPING` | Server nimmt keine neue Operation mehr an |
| `INVALID_REQUEST` | Ungültige Anfrage |
| `NOT_LOADED` | Welt ist nicht registriert |
| `ALREADY_UNLOADING` | Ein Unload läuft bereits |
| `PROTECTED_WORLD` | Primäre oder geschützte Welt |
| `EVENT_CANCELLED` | `WorldUnloadEvent` wurde abgebrochen |
| `PLAYERS_PRESENT` | Spieler befinden sich noch in der Welt |
| `INVALID_TELEPORT_TARGET` | Teleportziel ist ungültig |
| `PLAYER_TELEPORT_FAILED` | Mindestens ein Spieler konnte nicht versetzt werden |
| `QUIESCE_FAILED` | Regionen oder Admission konnten nicht kontrolliert angehalten werden |
| `SAVE_FAILED` | Speichern schlug fehl |
| `STORAGE_CLOSE_FAILED` | Storage-Worker oder Dateihandles konnten nicht geschlossen werden |
| `UNREGISTRATION_FAILED` | Serverregistrierungen konnten nicht vollständig entfernt werden |
| `CLEANUP_FAILED` | Nacharbeiten oder Bereinigung schlugen fehl |
| `TIMEOUT` | Reversible Phase überschritt den konfigurierten Timeout |
| `CANCELLED` | Operation wurde abgebrochen |

Der Timeout ist in der reversiblen Phase wirksam. Hat das irreversible
Schließen bereits begonnen, beendet Tessera die Dateihandle-Freigabe
kontrolliert, bevor das Ergebnis abgeschlossen wird.

## Getter und Builderfehler

Getter: `save()`, `playerHandling()`, `teleportTarget()`, `timeout()`. `PlayerHandling` ist `FAIL` oder `TELEPORT`. Ziel-Locations werden defensiv kopiert. `teleportPlayers(Location)` verlangt ein Ziel mit Welt, `timeout(Duration)` eine positive Dauer; sonst `IllegalArgumentException`. Die Zielwelt darf nicht die zu entladende oder selbst geschlossene Welt sein; dies wird im Auftrag erneut geprüft. Plugin-Vetos können `PLAYER_TELEPORT_FAILED` ergeben.

## Stopp und konkurrierende Arbeit

Neue Teleports, Tickets und Regionsaufgaben zur Zielwelt werden gesperrt. Nur ihre Regionen werden für diesen Unload angehalten. Reversible Fehler geben die Welt wieder frei; irreversibles Close muss auch über das Timeout hinaus enden. Nach `STORAGE_CLOSE_FAILED`, `UNREGISTRATION_FAILED` oder `CLEANUP_FAILED` Zustand untersuchen, nicht blind neu laden oder löschen. Stopp/Disable ist keine Freigabe zum Löschen.
