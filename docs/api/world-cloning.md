---
title: "Statische Weltvorlagen klonen"
description: "Validierte Template-Kopien mit neuen Weltidentitäten, Optionen und Fehlerstatus."
navTitle: "Weltvorlagen"
order: 50
updated: 2026-10-05
minecraftVersion: "26.3"
badge: "Referenz"
---

Tessera klont statische geladene Vorlagen. Für veränderliche Welten dient der [Snapshot-Vertrag](world-snapshots.md).

## Signatur und Parameter

```java
CompletionStage<WorldCloneResult> cloneWorldAsync(
    World source, NamespacedKey target, WorldCloneOptions options
);
```

`source` ist die geladene unveränderte Template-Welt ohne Spieler, `target` ein neuer eindeutiger Key und `options` die unveränderlichen Einstellungen. Aufruf von jedem Thread; kein blockierendes Warten. Nach Erfolg ist die neue Welt aktiv. Der Future-Callback besitzt aber nicht automatisch ihre Chunks.

## Beispiel

`worlds`, `plugin` und `templateWorld` sind bereits beschaffte Referenzen:

```java
NamespacedKey target = new NamespacedKey(plugin, "arena_team_red");

worlds.cloneWorldAsync(
    templateWorld,
    target,
    WorldCloneOptions.defaults()
).thenAccept(result -> {
    if (!result.successful()) {
        plugin.getLogger().severe(
            "Clone fehlgeschlagen: "
                + result.status() + " / " + result.message()
        );
        return;
    }

    World arena = result.world();
});
```

Ein Clone ist kein ungeprüftes rekursives Kopieren eines Live-Ordners.
Tessera hält eine geladene Vorlage kontrolliert an, flusht ihre Speicher und validiert
die Regiondateien.

Kopiert werden wiederverwendbare Weltdaten:

- `region`
- `entities`
- `poi`
- optional `data`

Nicht übernommen werden unter anderem:

- Spieler- und Statistikdaten
- Advancements
- `uid.dat`
- `session.lock`
- `level.dat` und `level.dat_old`
- offene Storage-Handles
- UUID und Dimension-Identität der Vorlage

Die Zielwelt erhält einen eigenen Key, eine eigene UUID, eigene Metadaten und
eigene Storage-Worker.

Seit Build 015 hält eine neue Template-Sicherung die native Vorgenerierung der
Quelle während ihres Lifecycle-Wechsels an und drainiert zuvor zugelassene
Arbeit. Nach Freigabe der Quelle darf ein laufender Job weiterarbeiten. Ein
bereits vorhandener gültiger Template-Cache bleibt davon unberührt; die
öffentliche Clone-Signatur ist unverändert.

### `WorldCloneOptions`

```java
WorldCloneOptions options = WorldCloneOptions.builder()
    .requireReadOnlyTemplate(true)
    .flushSource(true)
    .copyDataDirectory(true)
    .spawnChunkRadius(2)
    .build();
```

Standardwerte:

| Option | Standard | Bedeutung |
| --- | --- | --- |
| `requireReadOnlyTemplate` | `true` | Vorlage muss als statisches Template behandelt werden |
| `flushSource` | `true` | Vor dem Snapshot werden Quelldaten gespeichert und geflusht |
| `copyDataDirectory` | `true` | Wiederverwendbare Dimensionsdaten werden kopiert |
| `spawnChunkRadius` | `2` | 5 × 5 Chunks um den Spawn werden vorbereitet |

`spawnChunkRadius` darf zwischen `0` und `8` liegen; andernfalls wirft der Builder `IllegalArgumentException`. Die gleichnamigen Getter liefern unveränderliche Optionen. `requireReadOnlyTemplate(false)` hebt die Sicherheitsbedingung nicht auf, sondern ergibt aktuell `SOURCE_NOT_READ_ONLY`.

Während des Snapshots darf die Template-Welt nicht als spielbare Arena
verwendet oder extern verändert werden. Mehrere Zielwelten können parallel aus
demselben unveränderten Snapshot installiert werden.

### `WorldCloneResult`

Methoden:

```java
Status status();
@Nullable World world();
String message();
@Nullable Throwable cause();
boolean successful();
@Nullable NamespacedKey worldKey();
@Nullable Path worldPath();
```

Statuswerte:

| Status | Bedeutung |
| --- | --- |
| `SUCCESS` | Zielwelt vollständig geklont und aktiv |
| `UNSUPPORTED` | Capability nicht verfügbar |
| `SERVER_STOPPING` | Server fährt herunter |
| `INVALID_REQUEST` | Ungültige Anfrage |
| `INVALID_SOURCE` | Quelle ist keine geeignete Welt |
| `SOURCE_NOT_READ_ONLY` | Read-only-Anforderung wurde verletzt |
| `SOURCE_BUSY` | Quelle befindet sich in einer inkompatiblen Operation |
| `TARGET_EXISTS` | Zielstorage existiert bereits |
| `SNAPSHOT_FAILED` | Snapshot oder Flush schlug fehl |
| `CORRUPT_TEMPLATE` | Template-Regiondateien sind beschädigt |
| `COPY_FAILED` | Zielkopie konnte nicht installiert werden |
| `LOAD_FAILED` | Kopie wurde erstellt, aber nicht erfolgreich geladen |
| `CLEANUP_FAILED` | Partielle Zieldaten konnten nicht vollständig bereinigt werden |
| `CANCELLED` | Operation wurde abgebrochen |

## Snapshot und Identität

Die Zielwelt erhält neue UUID, Metadaten und Storage-Worker. Die primäre Session-Sperre wird nie kopiert. MCA-Tabellen, Allokationsbereiche, Überschneidungen, Chunklängen und Kompressionsarten werden geprüft. Installation über privates Staging und atomaren Move, soweit unterstützt. Mehrere Clones können denselben unveränderten validierten Snapshot teilen: kein neuer Live-Save pro Clone. Das Template weder bespielen noch extern verändern.

`requireReadOnlyTemplate(true)` ist die Zusage des Aufrufers, ergänzt um Serverprüfungen, kein öffentlicher Schreibschutz-Schalter. Aufruf- und Future-Ausnahmen zusätzlich zu Ergebnisstatus behandeln. Abbruch/Disable beendet nicht eigenmächtig bereits laufende Server-I/O.

## Mehrere Arenen

```java
CompletionStage<WorldCloneResult> red = worlds.cloneWorldAsync(
    template,
    new NamespacedKey(plugin, "arena_red"),
    WorldCloneOptions.defaults()
);

CompletionStage<WorldCloneResult> blue = worlds.cloneWorldAsync(
    template,
    new NamespacedKey(plugin, "arena_blue"),
    WorldCloneOptions.defaults()
);

CompletionStage<Void> ready = red.thenCombine(blue, (redResult, blueResult) -> {
    if (!redResult.successful() || !blueResult.successful()) {
        throw new IllegalStateException(
            "Arena-Clone fehlgeschlagen: "
                + redResult + " / " + blueResult
        );
    }

    World redArena = redResult.world();
    World blueArena = blueResult.world();
    return null;
});
```

Die Clone-Vorbereitung darf parallel laufen. Nach der Aktivierung ticken die
Welten über ihre normalen unabhängigen Tickregionen und nicht auf einem
gemeinsamen Arena-Thread.

Nach `thenCombine` Entity-Zugriffe erneut auf deren Besitzer weiterleiten. Erfolgreiche Teilwelten bei Fehlschlag der zweiten Anfrage gegebenenfalls kontrolliert entladen. Beispiel für nicht blockierende Folgearbeit: [Teleports](error-handling.md#teleports-und-dimensionen).
