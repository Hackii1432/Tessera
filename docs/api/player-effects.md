---
title: "Post-Processing-Effekte für Spieler"
description: "Die aus Paper über Sinopia integrierte PlayerPostEffects-API mit Tessera-Ownership-Regeln."
navTitle: "Spieler-Effekte"
order: 130
updated: 2026-10-11
minecraftVersion: "26.3"
badge: "Referenz"
---

`Player#postEffects()` liefert `io.papermc.paper.entity.PlayerPostEffects`.
Diese öffentliche API stammt aus Paper und wurde über Sinopia integriert;
Tessera ergänzt ihren regionsgebundenen Zugriff, keine zweite Effekte-API.

## Signaturen und Ergebnisse

```java
List<Key> values();
boolean set(SequencedCollection<Key> postEffects);
boolean add(Key effect);
boolean remove(Key effect);
boolean clear();
void update();
```

`Key` stammt aus `net.kyori.adventure.key`, nicht aus NMS. `values()` liefert eine
unveränderliche Momentaufnahme in Reihenfolge. `set` ersetzt die geordnete Liste
und liefert `true` bei Änderung. `add` liefert `false`, wenn der Effekt vorhanden
ist; `remove` bei fehlendem Effekt; `clear` bei bereits leerer Liste.

Ab Build 019 sendet `update()` die aktuelle geordnete Liste ausdrücklich erneut
an denselben Client, ohne sie zu ändern. Die Methode hat keine Parameter und
keinen Rückgabewert. Das ist beispielsweise nach einem Ressourcenpaketwechsel
nützlich; sie bestätigt keine Darstellung oder Netzwerkzustellung. Ein Ressourcen-
paket muss bereits passend geladen sein. Die Änderungsmethoden senden bei einer
tatsächlichen Änderung weiterhin selbst; `update()` ist kein notwendiger Zusatz
für jeden `add`-/`clear`-Aufruf.

## Besitzer und Lebenszyklus

**Jeder** Aufruf einschließlich `values()` gehört auf den aktuellen Besitzer
eines aktiven Spielers. Die API bleibt synchron und leitet falsche Threads nicht
automatisch weiter. Threadverletzungen/inaktive Handles sowie ungültige/null
Eingaben können Ausnahmen auslösen. Client-Ressourcen müssen den Effekt kennen;
ein Key allein installiert kein Shader-/Ressourcenpaket.

Aufbewahrte API-Objekte lösen nach Respawn das aktuelle Spieler-Handle auf;
das ersetzt nicht das erneute Einplanen auf den EntityScheduler nach Transfer.
Listen-Snapshots nicht als veränderlichen Livezustand behandeln. Bei Disable
keine neuen Tasks des deaktivierten Plugins einplanen; gewünschte Effekt-
Wiederherstellung zuvor als kontrollierte aktive Cleanup-Phase durchführen.

<!-- compile: EffectsExample -->
```java
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class EffectsExample {
    public static boolean clearOnOwner(Plugin plugin, Player player) {
        return player.getScheduler().execute(plugin, () -> {
            boolean changed = player.postEffects().clear();
            player.postEffects().update(); // Explicit resend, even when already empty.
            plugin.getLogger().fine("Effects cleared: " + changed);
        }, () -> plugin.getLogger().fine("Player retired"), 1L);
    }
}
```

Der Rückgabewert des Beispiels bestätigt ausschließlich die Einplanung, nicht
die Effektänderung. Bei `false` läuft auch der Retirement-Callback nicht.
Siehe [Scheduler](scheduler.md#entityscheduler) für Einplanungsfehler.
