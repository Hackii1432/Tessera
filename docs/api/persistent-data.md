---
title: "Typisierte Schlüssel für Persistent Data"
description: "Papers PersistentDataKey bündelt Schlüssel und Datentyp für bestehende PDC-Stores; Ownership und Lebenszyklus bleiben unverändert."
navTitle: "Persistent Data"
order: 125
updated: 2026-10-11
minecraftVersion: "26.3"
badge: "Paper-Kompatibilität"
---

Ab Build 019 integriert Tessera die geerbte Paper-API
`io.papermc.paper.persistence.PersistentDataKey<C>`. Sie bündelt einen Adventure-
Key mit seinem Bukkit-PDC-Typ, statt beide bei jedem Aufruf separat zu übergeben.
Es entsteht kein neuer Store und kein geändertes Dateiformat.

## Schlüssel erstellen

```java
static <C> PersistentDataKey<C> of(Key key, PersistentDataType<?, C> type);
NamespacedKey key();
PersistentDataType<?, C> dataType();
```

`Key` ist `net.kyori.adventure.key.Key`; `type` ist ein bestehender
`org.bukkit.persistence.PersistentDataType`. `C` bezeichnet den zurückgegebenen
komplexen Java-Typ. `of` liefert ein unveränderliches Schlüssel-/Typ-Paar,
`key()` den normalisierten Bukkit-`NamespacedKey`, `dataType()` denselben
Datentyp. `null` für Key oder Typ wird mit `IllegalArgumentException` abgewiesen;
ungültige Namespace-/Key-Zeichen bleiben unzulässig. Das Erstellen und Lesen
eines Schlüssels erfordert keinen Welt-/Entity-Besitz. Benutzerdefinierte
Datentypen müssen die Threadregeln ihres eigenen Konvertierungscodes erfüllen.

## Container und Views

Folgende Overloads stehen auf `PersistentDataContainer` beziehungsweise der
lesenden `PersistentDataContainerView` bereit; Signaturübersicht:

```java
<C> C get(PersistentDataKey<C> key); // Nullable; read-only view also supports this.
<C> C getOrDefault(PersistentDataKey<C> key, C defaultValue);
boolean has(PersistentDataKey<?> key);
<C> void set(PersistentDataKey<C> key, C value); // Mutable container only.
void remove(PersistentDataKey<?> key);          // Mutable container only.
```

`get` liefert den konvertierten Wert oder `null`, falls der Schlüssel fehlt;
`getOrDefault` dann den übergebenen Default. `has` prüft vorhandene Daten und
deren primitiven Speichertyp. Unterschiedliche benutzerdefinierte Konverter mit
demselben primitiven Typ sind dadurch nicht unterscheidbar. `set` ersetzt den
Wert; `remove` entfernt ihn und ist bei fehlenden Daten wirkungslos. Beide haben
keinen Ergebniswert. Typfehler beim Lesen und ungültige Eingaben folgen den
bisherigen PDC-Ausnahmen, insbesondere `IllegalArgumentException`. Die neuen
Overloads leiten an die bestehenden Key-/Type-Methoden weiter.

Alte Aufrufe mit `key.key()` und `key.dataType()` sehen dieselben Daten.
Wechselt ein Plugin den Speichertyp seines Keys, muss es die Daten selbst
migrieren; ein anderer generischer Typ allein ist keine Migration.

## Thread und Lebenszyklus

Ein Entity-/Spieler-PDC gehört weiter zum aktuellen Entity-Besitzer, ein
Blockentity-PDC zum geladenen Blockbesitzer. Der typed Key verleiht keinen
Async-Zugriff. Eine lesende View ist nicht automatisch ein isolierter Snapshot.
Bei ItemMeta-Kopien gelten deren vorhandene Regeln; dieselbe veränderbare Kopie
nicht zwischen konkurrierenden Tasks teilen, und Änderungen an lebenden
Inventaren wieder auf dem Besitzer ausführen.

Die Werte werden wie bisher mit dem Container gespeichert. Disable entfernt
keine Daten; temporäre Einträge vor Disable auf dem Besitzer kontrolliert
bereinigen. Der EntityScheduler folgt Transfers und Respawns, während ein alter
Container oder ein NMS-Handle nicht als aktuelle Referenz weiterverwendet werden
sollte. Async vorbereitete Werte abtrennen und im Owner-Callback frisch auf den
Container zugreifen. Retirement und eine nicht angenommene Task sind keine
erfolgreiche Änderung.

<!-- compile: TypedPersistentDataExample -->
```java
import io.papermc.paper.persistence.PersistentDataKey;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class TypedPersistentDataExample {
    private static final PersistentDataKey<Integer> COUNT = PersistentDataKey.of(
        Key.key("example:count"), PersistentDataType.INTEGER);

    public static boolean increment(Plugin plugin, Player player) {
        if (!plugin.isEnabled()) return false;
        return player.getScheduler().execute(plugin, () -> {
            if (!player.isOnline()) return;
            var pdc = player.getPersistentDataContainer();
            pdc.set(COUNT, Math.addExact(pdc.getOrDefault(COUNT, 0), 1));
        }, () -> {}, 1L);
    }
}
```

Das Beispiel lässt Retirement bewusst ohne Mutation enden. Für ein eigenes
asynchrones Ergebnisprotokoll auch `false` beim Einplanen und Plugin-Disable
behandeln; siehe [Scheduler](scheduler.md) und [Fehlerbehandlung](error-handling.md).
