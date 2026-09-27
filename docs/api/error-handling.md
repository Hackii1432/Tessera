---
title: "Asynchrone Abläufe, Fehler und Plugin-Disable"
description: "Ergebnisse auswerten, eigene Futures zuverlässig beenden und Folgearbeit zum aktuellen Besitzer übertragen."
navTitle: "Fehlerbehandlung"
order: 160
updated: 2026-09-28
minecraftVersion: "26.3"
badge: "Anleitung"
---

Nichtblockierende APIs beseitigen weder Fehler noch Lebenszyklus-Rennen.
Ein erfolgreich eingeplanter Task, ein abgeschlossenes Future und eine
erfolgreiche fachliche Operation sind drei unterschiedliche Dinge.

## Drei Fehlerpfade

1. Bereits der Aufruf kann werfen, etwa bei ungültigen Optionen oder gesperrter Welt.
2. Die Stage kann außergewöhnlich enden, einschließlich Caller-Cancellation.
3. Ein reguläres Ergebnis kann einen Fehlerstatus tragen.

Weltresultate besitzen `status()`, `message()`, optional `cause()` und
`successful()`; Load/Clone zusätzlich nullable Welt/Key/Pfad. Restore-Ergebnisse
besitzen stattdessen Operation-ID, Status und Nachricht, **kein `cause()`**.
Erst nach geprüftem Erfolg weiterarbeiten. Callback-Exceptions in zurückgegebenen
Stages beobachten, nicht als unreferenzierte Future-Ketten verlieren.

## Owner-Uebergabe

Die folgende vollständige Plugin-Hilfsklasse ist **keine Tessera-API**. Sie nimmt
unveränderliche Eingaben entgegen und überträgt kurze Aktionen auf einen Entity-
Besitzer. Der Aufrufer muss `close()` beim Disable aufrufen. Ausführungsfehler,
fehlende Einplanung, Retirement und Disable schließen eigene wartende Futures.
Auch diese Hilfsklasse kann bereits laufende Aktionen nicht zurückrollen.

<!-- compile: OwnerTasks -->
```java
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class OwnerTasks implements AutoCloseable {
    private final Plugin plugin;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();

    public OwnerTasks(Plugin plugin) { this.plugin = plugin; }

    public <T> CompletableFuture<T> submit(Entity entity, Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        AtomicReference<ScheduledTask> handle = new AtomicReference<>();
        pending.add(result);
        result.whenComplete((value, error) -> {
            pending.remove(result);
            ScheduledTask task = handle.get();
            if (task != null) task.cancel();
        });
        if (!active.get() || !plugin.isEnabled()) {
            result.completeExceptionally(new CancellationException("Plugin inactive"));
            return result;
        }
        try {
            ScheduledTask task = entity.getScheduler().run(plugin, ignored -> {
                if (!active.get() || result.isDone()) {
                    result.completeExceptionally(new CancellationException("Request inactive"));
                    return;
                }
                try { result.complete(action.get()); }
                catch (Throwable error) { result.completeExceptionally(error); }
            }, () -> result.completeExceptionally(new CancellationException("Entity retired")));
            handle.set(task);
            if (task == null) {
                result.completeExceptionally(new CancellationException("Entity already retired"));
            } else if (result.isDone()) {
                task.cancel();
            }
        } catch (RuntimeException error) {
            result.completeExceptionally(error);
        }
        if (!active.get()) {
            result.completeExceptionally(new CancellationException("Plugin disabled"));
        }
        return result;
    }

    @Override
    public void close() {
        active.set(false);
        for (CompletableFuture<?> result : pending) {
            result.completeExceptionally(new CancellationException("Plugin disabled"));
        }
    }
}
```

In der eigenen `JavaPlugin#onDisable()`-Methode `ownerTasks.close()` ausführen,
zusätzlich andere Producer und gespeicherte Scheduler-Handles stoppen sowie
Global-/Async-Aufgaben über deren `cancelTasks(plugin)` abbrechen. Keine neuen
Plugin-Ticktasks erst nach Disable anlegen. Kein `shutdownNow()` auf servereigenen
Executors. Ein zukünftiger Consumer darf aus `submit` keinen blockierenden Aufruf
machen. Im Supplier ist **nur die angegebene Entity** einschließlich ihres lokalen
Kontexts sicher, nicht beliebige zweite Entities.

## Teleports und Dimensionen

Die folgenden vollständigen Methoden verwenden bekannte, vorab validierte
Zielkoordinaten. Das Ziel wird nicht in einem fremden Spieler gelesen. Die
Zuständigkeit nach Transfer wird erneut über `OwnerTasks` hergestellt:

<!-- compile: TeleportExample -->
```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class TeleportExample {
    public static CompletionStage<Boolean> move(
            OwnerTasks owners, Player player, Location target) {
        Location destination = target.clone();
        return owners.submit(player, () -> player.teleportAsync(destination))
            .thenCompose(stage -> stage)
            .thenCompose(success -> {
                if (!success) return CompletableFuture.completedFuture(false);
                return owners.submit(player, () -> {
                    player.sendMessage("Transfer completed");
                    return true;
                });
            });
    }
}
```

Aufruf- und Future-Fehler getrennt beobachten. Die Rückgabe umfasst hier auch
die Nachricht; ein inzwischen deaktiviertes Plugin kann diese verhindern, obwohl
der Teleport bereits gelang. Cancellation beendet eine Beobachtung, nicht einen
bereits gestarteten nativen Transfer. Das Beispiel ist **keine** Restore-Transaktion.

## Welten und servereigene Arbeit

Create/Load/Clone/Snapshot/Unload-Ergebnisse auswerten und Folgearbeit explizit
übergeben. Template-Kopien können über `thenCombine` verbunden werden; auf eine
Welt nicht aus einem unbekannten Callback blockierend zugreifen. Nach teilweisem
Erfolg einer Mehrweltoperation müssen erfolgreiche Teilwelten gesondert verwaltet
werden. Pfade vor Unload erfassen und erst nach erfolgreichem Close, mit separater
Pfadprüfung, auf dem I/O-Executor weiterverwenden.

Beim Disable neue Weltoperationen stoppen und gewöhnliche eigene UI-/Gameplay-
Callbacks inaktiv machen. Tessera beendet bereits begonnene Lifecycle-Bereinigung.
**Restore ist anders:** Eine benötigte Schreibschranke und die dauerhafte Commit-
Entscheidung dürfen nicht durch Ignorieren des Callbacks verloren gehen.
[Restore-Recovery](player-restore.md#idempotenz-disable-und-recovery) bleibt eine
Pflicht des Transaktions-Callers.
