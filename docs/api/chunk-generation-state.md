---
title: "Chunk-Generierungsstatus ohne Chunk-Laden"
description: "Gespeicherte und geladene Chunk-Metadaten ab Build 022 regionsicher abfragen, ohne Generierung auszulösen."
navTitle: "Chunk-Status"
order: 85
updated: 2026-10-26
minecraftVersion: "26.3"
badge: "Paper-Kompatibilität"
---

Diese geerbten Paper-Methoden stehen ab Tessera Build 022 auf `org.bukkit.World`
bereit. Sie prüfen Metadaten, laden aber keinen Chunk in die Welt und starten
keine Generierung. Die Abfrage ist kein Ticket und hält keine Region am Leben.

## Signaturen und Ergebnisse

```java
ChunkGenerationState getChunkGenerationState(int x, int z);
CompletableFuture<ChunkGenerationState> getChunkGenerationStateAsync(int x, int z);
```

`x` und `z` sind Chunk-, nicht Blockkoordinaten. Der Ergebnistyp ist
`io.papermc.paper.world.chunk.ChunkGenerationState`:

| Ergebnis | Bedeutung |
| --- | --- |
| `NOT_GENERATED` | Nicht geladen und keine gespeicherten Chunk-Daten vorhanden |
| `PARTIAL` | Generierung begonnen, noch nicht vollständig |
| `OUTDATED` | Daten müssen aktualisiert oder umgebunden werden; vollständiger und partieller Fortschritt werden nicht durch einen Datafix ermittelt |
| `GENERATED` | Vollständig und auf aktueller Datenversion |

Das Ergebnis ist eine Momentaufnahme. Gleichzeitiges Generieren, Speichern oder
Entladen kann den Zustand unmittelbar danach ändern. `GENERATED` garantiert
nicht, dass keine Entities ticken oder die Welt unverändert bleibt.

## Threadkontext und Fehler

Geladene Chunks liefern eine unveränderlich veröffentlichte Statusaufnahme,
auch bei einem fremden Regionsbesitzer. Es wird dabei kein fremder veränderlicher
Chunk gelesen. Fehlt die Aufnahme, wird über die native I/O-Schicht gelesen.

Die synchrone Methode darf nur außerhalb von Tick- und Netzwerkthreads auf I/O
warten. Regions-, Global- und Netty-Threads erhalten in diesem Fall eine
`IllegalStateException`. Für Plugins ist die asynchrone Methode der bevorzugte
Weg. **Auch deren Future nicht mit `join()` oder `get()` auf einem Tickthread
abwarten.** Tick-Freeze verhindert die reine asynchrone Metadatenabfrage nicht.

Lesefehler, neuere gespeicherte Datenversionen und unzulässige Weltzustände
scheitern explizit; die asynchrone Methode beendet das Future außergewöhnlich.
Während Snapshot, Quiescing, Unload oder geschlossenem Weltzustand werden keine
neuen öffentlichen Abfragen angenommen. Bereits registrierte I/O gehört zur
nativen Drain-Reihenfolge; ein inzwischen inaktiver Weltzustand kann auch die
Ergebnislieferung ablehnen. Fehler sind kein `NOT_GENERATED`.

Callbacks laufen auf keinem zugesicherten Scheduler. Nur Ergebnisdaten dürfen
dort unmittelbar verarbeitet werden. Weltarbeit muss erneut zum Regionsbesitzer,
Spielerarbeit zum aktuellen EntityScheduler wechseln. Ein abgebrochenes Future
stoppt nicht zwingend den bereits angenommenen Leseauftrag; er mutiert keine Welt.

## Beispiel mit Disable und Spielertransfer

Die Referenzen werden im erlaubten Aufrufkontext übergeben. Der Callback verwendet
keine alte Spielerposition; die Mitteilung wird auf dem dann aktuellen Besitzer
ausgeführt und folgt einem Regions- oder Dimensionswechsel.

<!-- compile: ChunkStateExample -->
```java
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class ChunkStateExample {
    public static void inspect(Plugin plugin, World world, int chunkX, int chunkZ, Player player) {
        if (!plugin.isEnabled()) return;
        world.getChunkGenerationStateAsync(chunkX, chunkZ).whenComplete((state, failure) -> {
            if (!plugin.isEnabled()) return;
            try {
                player.getScheduler().execute(plugin, () -> {
                    if (!plugin.isEnabled() || !player.isOnline()) return;
                    player.sendMessage(failure == null ? "Chunk state: " + state : "Chunk query failed");
                }, () -> {}, 1L);
            } catch (org.bukkit.plugin.IllegalPluginAccessException disabled) {
                // Disable may race the scheduling call; no follow-up work is required.
            }
        });
    }
}
```

Für Generierung den vorhandenen Pregen-Befehl oder die zugelassenen asynchronen
Chunk-Lade-APIs verwenden. Eine Statusabfrage ist keine Generierungsoperation.
Siehe [Ownership](ownership.md), [Scheduler](scheduler.md) und
[Welt-Unload](world-unloading.md).
