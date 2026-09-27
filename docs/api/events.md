---
title: "Events bei Welt-Lifecycle, Portalen und Respawn"
description: "Geerbte Events im tatsächlichen Tessera-Threadkontext und der operationsgebundene Restore-Teleport-Scope."
navTitle: "Events"
order: 100
updated: 2026-09-28
minecraftVersion: "26.3"
badge: "Referenz"
---

Tessera verwendet vorhandene Bukkit-/Paper-/Folia-Events. Es gibt keine neue
öffentliche Familie von „TesseraRestoreEvent“- oder internen Queue-Events.
Listener laufen im Kontext des jeweiligen Dispatchs, nicht automatisch auf
einem universellen Bukkit-Main-Thread.

## Serverinitialisierung

`io.papermc.paper.threadedregions.RegionizedServerInitEvent` wird nach der
Initialisierung, vor parallelem Regionsticken ausgelöst. Listener per
`PluginManager#registerEvents(Listener, Plugin)` registrieren. Der Event trägt
keine Zielregion und ist nicht abbrechbar. Er bietet einen einmaligen Hook,
keine Sonderrechte für spätere asynchrone Aufgaben.

## Runtime-Welten

Im Runtime-Lifecycle laufen `WorldInitEvent`, `WorldLoadEvent`, `WorldUnloadEvent`
und die dortigen `WorldSaveEvent`s auf dem Global-Thread. `WorldUnloadEvent`
ist abbrechbar (`isCancelled()`/`setCancelled(boolean)`); ein Veto ergibt
`WorldUnloadResult.Status.EVENT_CANCELLED`. Load/Init sind keine Zusage, dass
ein Listener ungeprüft entfernte Chunkdaten lesen darf. Für Chunkarbeit
[RegionScheduler](scheduler.md#regionscheduler) verwenden und nicht im Listener
auf dessen Abschluss warten. Diese Kontextaussage betrifft die genannten
Lifecycle-Pfade, nicht jedes Event mit einem Weltparameter.

## Asynchrone Portale

Die geerbten APIs bleiben unverändert:

| Event | Relevante Methoden und Wirkung |
| --- | --- |
| `io.papermc.paper.event.entity.EntityPortalReadyEvent` | `getTargetWorld(): @Nullable World`, `setTargetWorld(@Nullable World)`, `getPortalType()`, Cancel; Entscheidung vor Portalsuche |
| `PlayerPortalEvent` | `getTo()/setTo(Location)`, `getSearchRadius()/setSearchRadius(int)`, `getCanCreatePortal()/setCanCreatePortal(boolean)`, `getCreationRadius()/setCreationRadius(int)`, Cancel |
| `EntityPortalEvent` | Entity-Variante mit Ziel, Suchradius, Erzeugungsradius/-erlaubnis und Cancel |
| `PlayerChangedWorldEvent` | `getFrom(): World`; nach tatsächlichem Weltwechsel, keine Zielumleitung |

Ready-/Portal-Handler des asynchronen Portalpfads laufen beim Ursprungsbesitzer.
Das Nether-Ziel ist zunächst der Ausgangspunkt der Portalsuche, nicht unbedingt
die spätere exakte Austrittsposition. Nur bereits vorbereitete/aktive Zielwelten
verwenden; die Event-Zieldaten synchron festlegen, nicht nach Rückkehr aus einem
Future ändern. Fremde Zielblöcke nicht im Ursprungs-Handler lesen.

Die Entity bleibt während der Suche im Ursprung. Vor Ablösen und Transfer wird
auf ihrem aktuellen Besitzer erneut validiert. Veto, fehlendes Portal bei
verbotener Erzeugung, Logout oder entladene/ersetzte Zielwelten sind kein Erfolg.
Nach tatsächlicher Platzierung läuft der ChangedWorld-Handler auf der Zielregion.
Vanilla-Skalierung, Weltgrenze, Portalrelativposition, Cooldown und Plattformregeln
bleiben Teil des nativen Pfads.

## Respawn

`org.bukkit.event.player.PlayerRespawnEvent` läuft im Folia-Asyncpfad auf der
aktuellen Spielerregion. Über `getRespawnLocation()` und `setRespawnLocation(Location)`
ein gültiges Ziel mit Welt setzen; `null`/weltlose Ziele werden abgewiesen.
`isBedSpawn()`, `isAnchorSpawn()`, `isMissingRespawnBlock()` und `getRespawnReason()`
beschreiben den nativen Ausgang. Das Event ist **nicht Cancellable**.

Der Endportal-Ausgang kann den Respawnpfad mit `RespawnReason.END_PORTAL` benutzen,
nicht einfach einen gewöhnlichen Portal-Teleport. Nach Einsetzen des lebenden
Spielers folgt `com.destroystokyo.paper.event.player.PlayerPostRespawnEvent`
auf der Zielregion. Die aktuelle API erbt `getRespawnLocation()` aus
`AbstractRespawnEvent`; `getRespawnedLocation()` ist der ältere Alias.
Erst hier beginnt normale Folgearbeit am lebenden Zielspieler.

Ein Respawnanker wird nur bei unverändertem Eventziel auf dem Besitzer des
Ankerblocks belastet. Die native Reservierung verhindert doppelte Auswahl der
letzten Ladung. Diese Reservierung ist keine öffentliche Plugin-Schnittstelle;
Plugins dürfen sie nicht über NMS manuell verwalten.

## Restore-Teleports

Dieser vollständige Beispiel-Listener beobachtet nur eine **vom eigenen
Transaktionskoordinator bekannte** Operations-ID. Er erzeugt keine Events,
erteilt keine pauschale Teleportfreigabe und hebt fremde Vetos nicht auf:

<!-- compile: RestoreListener -->
```java
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class RestoreListener implements Listener {
    private final Supplier<UUID> activeOperation;
    private final Logger logger;

    public RestoreListener(Supplier<UUID> activeOperation, Logger logger) {
        this.activeOperation = activeOperation; // Threadsafe Snapshot, z.B. AtomicReference::get
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        UUID id = activeOperation.get();
        if (id != null && Bukkit.getPlayerRestoreService().isRestoreTeleport(event, id)) {
            logger.fine("Restore transfer observed; veto=" + event.isCancelled());
        }
    }
}
```

Der Scope gilt nur für dieses Event während seines Dispatchs. Registrierung nur
im nachgewiesenen Tessera-Adapter. Bei Plugin-Disable werden keine neuen
Listener-Folgeaufgaben geplant; die Transaktionsverantwortung bleibt trotzdem
beim [Restore-Koordinator des Callers](player-restore.md#idempotenz-disable-und-recovery).
