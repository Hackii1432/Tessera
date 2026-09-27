---
title: "Gamerules im richtigen Thread ändern"
description: "Tesseras Enderaugen-Regel und globale Zuständigkeit für weltbezogene Einstellungen."
navTitle: "Gamerules"
order: 120
updated: 2026-09-28
minecraftVersion: "26.3"
badge: "Referenz"
---

Gamerules sind weltbezogene Einstellungen mit globaler Änderungszuständigkeit.
Tessera ergänzt eine Regel für Enderaugen und Endportal-Reisen; die Bukkit-API
bleibt der Einstieg.

## Öffentliche Regel

```java
GameRule<Boolean> rule = GameRules.ALLOW_EYES_OF_ENDER_USE;
```

Die Tessera-Regel `allow_eyes_of_ender_use` hat standardmäßig den Wert `true`.
`false` sperrt Enderaugen-Projektile, das Einsetzen in Endportalrahmen und die
von diesem Pfad kontrollierten Endportal-Reisen. Sie ersetzt keine allgemeine
Teleport-Permission. Der alte Alias `GameRule.ALLOW_EYES_OF_ENDER_USE` ist seit
26.2 veraltet; für neuen Code `org.bukkit.GameRules` verwenden.

`ALLOW_ENTERING_NETHER_USING_PORTALS` und `LOCATOR_BAR` sind dagegen geerbte
Vanilla/Bukkit-Regeln, keine neu erfundenen Tessera-APIs. Tessera berücksichtigt
sie in seinen betreffenden Portal-/Locator-Pfaden.

## Signaturen und Ergebnisse

```java
<T> boolean setGameRule(GameRule<T> rule, T newValue);
<T> T getGameRuleValue(GameRule<T> rule);
```

Die Darstellung benennt Instanzmethoden auf `World`, keine statischen Aufrufe. `rule`
und Wert müssen vorhanden und typ-/wertegültig sein. `setGameRule` liefert `false`
bei nicht verfügbarer Regel oder Event-Abbruch. `true` sagt, dass der Setzpfad
nicht abgebrochen wurde, **nicht**, dass sich der Wert geändert hat. Plugins
können den effektiven Wert anpassen; bei Bedarf anschließend erneut abfragen.
Ungültige Eingaben können synchron `IllegalArgumentException` ergeben.

## GlobalRegionScheduler

`CraftWorld#setGameRule` und der alte String-Setter verlangen ausdrücklich den
Global-Thread. Das frühere RegionScheduler-Beispiel war falsch. Die gespeicherte
Weltreferenz kann bis zur Ausführung entladen/ersetzt werden; deshalb am Global-
Thread gegen die aktuelle Registrierung prüfen.

<!-- compile: RuleExample -->
```java
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

public final class RuleExample {
    public static void setEyesAllowed(Plugin plugin, World world, boolean allowed) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            if (Bukkit.getWorld(world.getUID()) != world) return;
            boolean accepted = world.setGameRule(GameRules.ALLOW_EYES_OF_ENDER_USE, allowed);
            plugin.getLogger().info("Accepted: " + accepted + "; effective value: "
                + world.getGameRuleValue(GameRules.ALLOW_EYES_OF_ENDER_USE));
        });
    }
}
```

Die Methode liefert keine synchrone Erfolgsbestätigung und wird nur mit aktivem
Plugin aufgerufen. Disable-/Einplanungsfehler wie bei allen
[Scheduler-Aufgaben](scheduler.md) behandeln. Für neue globale Lese-/Schreibketten
denselben Global-Kontext verwenden; das erlaubt weiterhin keine fremden Blockzugriffe.
