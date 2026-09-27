---
title: "Runtime-Welten erstellen und laden"
description: "Lifecycle-Schnittstelle, stabile Weltidentität und Ladeergebnisse."
navTitle: "Runtime-Welten"
order: 40
updated: 2026-09-28
minecraftVersion: "26.3"
badge: "Referenz"
---

Tesseras `RuntimeWorldManager` ist eine öffentliche Erweiterung und verwaltet tatsächlich geladene Welten, nicht nur Ordner. Interne CraftBukkit-Klassen sind dafür nicht erforderlich.

Package:

```text
io.papermc.paper.world
```

Manager:

```java
RuntimeWorldManager worlds = Bukkit.getRuntimeWorldManager();
```

Alle Lifecycle-Methoden sind nicht blockierend und dürfen von Plugin-,
Async-, Entity-, Region- oder Global-Threads aufgerufen werden. Das
Erst ein **erfolgreiches** Create/Load/Clone-Ergebnis bestätigt die vollständig
aktive Welt, ein erfolgreicher Unload ihren vollständigen Abschluss. Ein
Fehlerresultat oder abgebrochenes Caller-Future ist keine solche Bestätigung.
Snapshot-Abschluss und Cleanup besitzen ihren eigenen Vertrag.

## Methoden

```java
boolean supportsRuntimeWorldLifecycle();

CompletionStage<WorldLoadResult> createWorldAsync(
    WorldCreator creator
);

CompletionStage<WorldLoadResult> loadWorldAsync(
    WorldCreator creator
);

CompletionStage<WorldLoadResult> loadWorldAsync(
    NamespacedKey key
);

CompletionStage<WorldCloneResult> cloneWorldAsync(
    World source,
    NamespacedKey target,
    WorldCloneOptions options
);

CompletionStage<WorldSnapshotResult> snapshotWorldsAsync(
    List<World> worlds,
    Path snapshotPath
);

CompletionStage<WorldUnloadResult> unloadWorldAsync(
    World world,
    WorldUnloadOptions options
);
```

`supportsRuntimeWorldLifecycle()` ist eine parameterlose Fähigkeitsabfrage ohne
Weltmutation, keine Prüfung, ob eine konkrete Welt gerade verfügbar ist. Sie ist
von jedem Thread nutzbar, sobald der Manager initialisiert ist.

Die alten synchronen Bukkit-Methoden sind auf Region- und Entity-Threads kein
sicherer Ersatz:

```java
Bukkit.createWorld(...);
Bukkit.unloadWorld(...);
```

Verwende für dynamische Tessera-Welten ausschließlich den
`RuntimeWorldManager`.

## Thread- und Eventkontext

Intern verteilt Tessera die Arbeit auf mehrere Kontexte:

- Validierung und per-Welt-Serialisierung auf dem Lifecycle-Executor
- Registrierung und Bukkit-Weltevents auf dem Global Region Thread
- Chunkvorbereitung über die vorhandenen Chunk-Futures und Worker
- Snapshot-, Kopier- und Close-Arbeit auf dedizierten I/O-Workern
- normales Ticken der aktiven Welt über unabhängige Tickregionen

Folgende Events laufen im Global-Region-Kontext:

- `WorldInitEvent`
- `WorldLoadEvent`
- `WorldSaveEvent` während Lifecycle-Speicherungen
- `WorldUnloadEvent`

Nach erfolgreichem Laden läuft die Welt nicht dauerhaft auf dem Global Region
Thread. Ihre Chunks werden wie alle anderen Tessera-Chunks regionalisiert.

## Stabile Weltidentität

Eine Runtime-Welt besitzt drei unterschiedliche Werte:

| Wert | Beispiel | Verwendung |
| --- | --- | --- |
| `World#getKey()` | `mosaikchallenges:mcc_arena` | Stabile Identität zum Speichern und erneuten Laden |
| `World#getName()` | `mosaikchallenges_mcc_arena` | Sichtbarer Bukkit-Kompatibilitätsname |
| `World#getWorldPath()` | `<level>/dimensions/mosaikchallenges/mcc_arena` | Tatsächlicher Speicherpfad |

Speichere immer:

```java
config.set("arena-world-key", world.getKey().toString());
```

Lade nach einem Neustart:

```java
String serialized = config.getString("arena-world-key");
NamespacedKey key = NamespacedKey.fromString(serialized);

if (key == null) {
    throw new IllegalStateException("Ungültiger gespeicherter World-Key");
}

Bukkit.getRuntimeWorldManager()
    .loadWorldAsync(key)
    .thenAccept(result -> {
        if (!result.successful()) {
            plugin.getLogger().severe(
                result.status() + ": " + result.message()
            );
            return;
        }

        World world = result.world();
        NamespacedKey stableKey = result.worldKey();
        Path actualPath = result.worldPath();
    });
```

Leite niemals einen Key aus den Unterstrichen von `World#getName()` ab. Eine
solche Umwandlung ist nicht eindeutig.

Wenn Tessera genau eine gespeicherte Welt erkennt, deren sichtbarer Name zum
versehentlich übergebenen Namen passt, liefert der Load-Vorgang
`IDENTITY_MISMATCH`. Tessera lädt niemals stillschweigend eine andere
Identität.

## Welt erstellen

```java
NamespacedKey key = new NamespacedKey(plugin, "farming");
WorldCreator creator = WorldCreator.ofKey(key);

worlds.createWorldAsync(creator).thenAccept(result -> {
    if (!result.successful()) {
        plugin.getLogger().severe(
            "Welt konnte nicht erstellt werden: "
                + result.status() + " / " + result.message()
        );
        return;
    }

    World farming = result.world();
});
```

Flatworld:

```java
WorldCreator creator = WorldCreator
    .ofKey(new NamespacedKey(plugin, "flat_arena"))
    .type(WorldType.FLAT);

CompletionStage<WorldLoadResult> result =
    worlds.createWorldAsync(creator);
```

`createWorldAsync` lehnt bereits vorhandene Weltdaten ab.
`loadWorldAsync` verlangt dagegen eine vorhandene und vollständige Welt.

Gültige Weltnamen bestehen aus 1 bis 64 Zeichen:

```text
A-Z a-z 0-9 . _ -
```

Ungültige Windows-Gerätenamen, doppelte Registrierungen, kollidierende Keys und
unsichere Zielpfade werden abgelehnt.

## `WorldLoadResult`

Felder und Methoden:

```java
Status status();
@Nullable World world();
String message();
@Nullable Throwable cause();
boolean successful();
@Nullable NamespacedKey worldKey();
@Nullable Path worldPath();
```

`world`, `worldKey()` und `worldPath()` sind nur bei erfolgreicher
Registrierung verfügbar.

Statuswerte:

| Status | Bedeutung |
| --- | --- |
| `SUCCESS` | Welt vollständig aktiv |
| `UNSUPPORTED` | Capability nicht verfügbar |
| `SERVER_STOPPING` | Server nimmt keine neue Operation mehr an |
| `INVALID_REQUEST` | Allgemein ungültige Anfrage |
| `INVALID_NAME` | Ungültiger Weltname |
| `INVALID_KEY` | Ungültiger oder kollidierender Dimension-Key |
| `ALREADY_EXISTS` | Zielstorage existiert bereits |
| `ALREADY_LOADED` | Welt ist bereits registriert |
| `NOT_FOUND` | Vorhandene Welt wurde nicht gefunden |
| `IDENTITY_MISMATCH` | Sichtbarer Name wurde mit stabilem Key verwechselt |
| `INCOMPLETE_WORLD` | Weltordner oder Metadaten sind unvollständig |
| `CORRUPT_WORLD` | Weltdaten oder Regiondateien sind beschädigt |
| `INITIALIZATION_FAILED` | ServerLevel oder Teilsysteme konnten nicht initialisiert werden |
| `CHUNK_PREPARATION_FAILED` | Startchunks konnten nicht vorbereitet werden |
| `REGISTRATION_FAILED` | Registrierung im Server schlug fehl |
| `ROLLBACK_FAILED` | Fehlerbereinigung konnte nicht vollständig abgeschlossen werden |
| `CANCELLED` | Operation wurde vor Abschluss abgebrochen |



## Eingaben und Generatorbindung

`WorldCreator` beschreibt Key, Konfiguration und gegebenenfalls Plugin-Generator/Biomprovider. Nach Übergabe nicht parallel verändern; Tessera übernimmt einen Snapshot der Anfrage. Generatorobjekte müssen ihre nebenläufigen Callbacks selbst unterstützen. Namen sind auf 1–64 Zeichen aus Buchstaben, Ziffern, Punkt, Unterstrich und Bindestrich begrenzt; reservierte Windows-Namen werden abgewiesen. Key, Name, Ordner und Registrierung müssen zusammenpassen und eindeutig sein.

Dimensionswurzeln besitzen getrennte `region`, `entities`, `poi` und `data`-Bäume; gemeinsame Level-Metadaten und die primäre Session-Sperre gehören nicht der Runtime-Welt allein. Ladeprüfungen berücksichtigen Weltgenerierungs-/UUID-Metadaten, auch beim Legacy-Layout mit eigener `level.dat`. Kopien benötigen passende unterschiedliche Identitäten. Gespeicherte Zielwelten nicht eigenmächtig an globale Generatoreinstellungen binden. Neue Welten ohne explizite Spawnposition beginnen in dieser Implementierung bei `(0, 64, 0)` mit 5 × 5 vorbereiteten Chunks; sichere Spielerspawns passend zum Generator festlegen.

## Abschluss und Lebenszyklus

Aufträge für dieselbe Welt werden koordiniert. Erfolgreiches Create/Load folgt auf Registrierung, Spawnvorbereitung, Aktivierung und `WorldLoadEvent`. Danach tickt die Welt regional. Ein Resultat ist keine dauerhafte Besitz- oder Ladegarantie.

Parameterfehler können als Ergebnis oder unmittelbar erscheinen: `loadWorldAsync((NamespacedKey) null)` wirft bereits im Default-Overload. Completion-Callbacks haben keine allgemeine Threadgarantie. [Fehlerbehandlung](error-handling.md) beschreibt Disable und Future-Ketten; [Events](events.md) beschreibt die Zuständigkeiten.

## Weitere Operationen

- [Statische Vorlagen klonen](world-cloning.md)
- [Frische Snapshots](world-snapshots.md)
- [Welten entladen](world-unloading.md)
- [Spielerzustände übertragen](player-restore.md)

## Vollständiges Beispiel für Ergebnisverkettung

Diese Plugin-Hilfsklasse kapselt Create/Load/Clone-Ergebnisse, ohne einen neuen
Serververtrag zu behaupten. Aufrufer verwalten Aktivität/Disable und beobachten
die zurückgegebenen Stages; keine Welt-/Entity-Mutation im Completion-Callback.

<!-- compile: WorldExamples -->
```java
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import io.papermc.paper.world.*;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;

public final class WorldExamples {
    public record Arenas(World red, World blue) {}

    public static CompletionStage<World> create(NamespacedKey key) {
        return requireLoad(Bukkit.getRuntimeWorldManager()
            .createWorldAsync(WorldCreator.ofKey(key)));
    }

    public static CompletionStage<World> load(String savedKey) {
        NamespacedKey key = savedKey == null ? null : NamespacedKey.fromString(savedKey);
        if (key == null) return CompletableFuture.failedFuture(
            new IllegalArgumentException("Invalid saved world key"));
        return requireLoad(Bukkit.getRuntimeWorldManager().loadWorldAsync(key));
    }

    public static CompletionStage<Arenas> clonePair(
            World template, NamespacedKey red, NamespacedKey blue) {
        RuntimeWorldManager manager = Bukkit.getRuntimeWorldManager();
        CompletionStage<World> first = requireClone(manager.cloneWorldAsync(
            template, red, WorldCloneOptions.defaults()));
        CompletionStage<World> second = requireClone(manager.cloneWorldAsync(
            template, blue, WorldCloneOptions.defaults()));
        return first.thenCombine(second, Arenas::new);
    }

    public static CompletionStage<WorldSnapshotResult> snapshot(List<World> worlds, Path root) {
        return Bukkit.getRuntimeWorldManager().snapshotWorldsAsync(List.copyOf(worlds), root);
    }

    public static CompletionStage<WorldUnloadResult> unloadEmpty(World world) {
        return Bukkit.getRuntimeWorldManager().unloadWorldAsync(world,
            WorldUnloadOptions.builder().save(true).failWhenPlayersPresent().build());
    }

    private static CompletionStage<World> requireLoad(CompletionStage<WorldLoadResult> stage) {
        return stage.thenApply(result -> {
            if (!result.successful()) throw new IllegalStateException(
                result.status() + ": " + result.message(), result.cause());
            return Objects.requireNonNull(result.world());
        });
    }

    private static CompletionStage<World> requireClone(CompletionStage<WorldCloneResult> stage) {
        return stage.thenApply(result -> {
            if (!result.successful()) throw new IllegalStateException(
                result.status() + ": " + result.message(), result.cause());
            return Objects.requireNonNull(result.world());
        });
    }
}
```

`snapshot`/`unloadEmpty` reichen fachliche Resultate bewusst unverändert durch:
Der Aufrufer muss `successful()` prüfen. `clonePair` ist keine atomare Zweiwelt-
Transaktion: Bei Teilfehlern erfolgreiche Welten nicht vergessen; benötigte
Cleanup-Informationen in der eigenen Ressourcenverwaltung halten.
