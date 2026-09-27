---
title: "Tessera-Funktionen sicher erkennen"
description: "Öffentliche Capabilities, Restore-Vertragsversion und reflektive Adapterauswahl."
navTitle: "Capabilities"
order: 15
updated: 2026-09-28
minecraftVersion: "26.3"
badge: "Referenz"
---

Tessera bietet eine implementierungsbasierte Feature-Erkennung. Weder Servername,
Buildstring noch das Vorhandensein einer geerbten Schedulerklasse ersetzt sie.

## Öffentliche Einstiegspunkte

Auf `Bukkit` statisch und auf `Server` als Instanzmethoden:

```java
TesseraCapabilities getTesseraCapabilities();
RuntimeWorldManager getRuntimeWorldManager();
PlayerRestoreService getPlayerRestoreService();
```

`TesseraCapabilities` liegt in `io.papermc.paper.threadedregions`; die beiden
Services in `io.papermc.paper.world`. Diese Server-Services nicht selbst
instanziieren oder durch CraftBukkit-Casts ersetzen.

| Methode | Ergebnis und Bedeutung |
| --- | --- |
| `boolean supportsRuntimeWorldLifecycle()` | Asynchroner Runtime-Welt-Lifecycle implementiert |
| `boolean supportsRegionSafeScoreboards()` | Regionsicheres Bukkit-Scoreboard-Modell implementiert |
| `PlayerRestoreService#contractVersion(): int` | Separater Restore-Vertrag; `0` nicht verfügbar, aktuell `1` |

Die parameterlosen Capability-Abfragen lesen Fähigkeiten, keine Entities oder
Chunks; nach Serverinitialisierung von jedem Thread nutzbar. Sie starten keine
Operation und garantieren weder, dass eine bestimmte Welt noch aktiv ist, noch
eine erfolgreiche konkrete Transaktion. Getter nicht aus Plugin-Konstruktoren
vor initialisiertem Bukkit-Server verwenden. Manager-Futures und Ergebnisse
haben ihren eigenen [Lebenszyklus](runtime-worlds.md).

## Direkte Nutzung

```java
boolean runtimeWorlds = Bukkit.getTesseraCapabilities()
    .supportsRuntimeWorldLifecycle();
boolean scoreboards = Bukkit.getTesseraCapabilities()
    .supportsRegionSafeScoreboards();
int restoreContract = Bukkit.getPlayerRestoreService().contractVersion();
```

Der native 010-Server liefert Restore-Vertrag 1. Für einen Vertrag-1-Adapter
exakt `1` verlangen, nicht
pauschal jede künftige größere Nummer akzeptieren.

## Adapterauswahl ohne frühe Verlinkung

Vollständige Hilfsklasse; keine Tessera-Typen in ihren öffentlichen Signaturen:

<!-- compile: TesseraDetection -->
```java
import java.lang.reflect.InvocationTargetException;
import org.bukkit.Bukkit;

public final class TesseraDetection {
    public record Features(boolean runtimeWorlds, boolean scoreboards) {}

    public static Features detect() {
        try {
            Class<?> type = Class.forName(
                "io.papermc.paper.threadedregions.TesseraCapabilities",
                false, Bukkit.class.getClassLoader());
            Object value = Bukkit.class.getMethod("getTesseraCapabilities").invoke(null);
            return new Features(
                (boolean) type.getMethod("supportsRuntimeWorldLifecycle").invoke(value),
                (boolean) type.getMethod("supportsRegionSafeScoreboards").invoke(value));
        } catch (ClassNotFoundException | NoSuchMethodException absent) {
            return new Features(false, false);
        } catch (IllegalAccessException | InvocationTargetException broken) {
            throw new IllegalStateException("Capability lookup failed", broken);
        }
    }
}
```

Erst nach positivem Ergebnis die eigene, separat gehaltene Tessera-Adapterklasse
laden. Ein Fehler des vorhandenen Getters wird bewusst nicht als „kein Tessera“
verschluckt. Fehlende Capability bedeutet nur „diese Erweiterung nicht benutzen“.

**Kein Paper/Folia-Test über `RegionizedServerInitEvent`:** Diese Klasse und die
Scheduler-Interfaces sind bereits in der integrierten Paper-Basis vorhanden.
Der frühere Fallback anhand allein dieser Klasse war daher ungeeignet. Eine
alternative Plattformstrategie muss separat explizit ausgewählt und geprüft
werden; siehe [Kompatibilität](compatibility.md).
