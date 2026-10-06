---
title: "Mob-, SulfurCube- und Poplar-APIs"
description: "Übernommene Paper-APIs für Friedlich-Despawn, SulfurCube-Ausrüstung und Poplar-Bäume mit Tesseras Regionsregeln."
navTitle: "Entities und Bäume"
order: 115
updated: 2026-10-06
minecraftVersion: "26.3"
badge: "Paper-Kompatibilität"
---

Diese APIs stammen aus Paper und sind ab Tessera **26.3-011-beta** vollständig
angebunden beziehungsweise ergänzt. Die drei farbigen Poplar-Konstanten sind
zusätzliche Sinopia-Erweiterungen. Alle Entity-Zugriffe gehören auf den aktuellen
[Entity-Owner](ownership.md); Baumgenerierung gehört auf den zuständigen
Regions-Thread. Eine vorhandene Java-Referenz ist kein Ownership-Nachweis.

## Despawn auf Friedlich

Signaturen auf `org.bukkit.entity.Mob`:

```java
boolean shouldDespawnInPeaceful();
void setDespawnInPeacefulOverride(TriState state);
TriState getDespawnInPeacefulOverride();
```

`TriState` ist `net.kyori.adventure.util.TriState`. `TRUE` erzwingt das Entfernen
auf Friedlich, `FALSE` unterdrückt dieses Entfernen, `NOT_SET` verwendet den
Vanilla-Standard des Entity-Typs. Ohne expliziten Override ändert sich Vanilla
nicht. Andere Entferngründe, Tod und andere Despawn-Regeln bleiben möglich.

Der Setter akzeptiert kein `null` (`IllegalArgumentException`). Der Getter liefert
den gesetzten Override; `shouldDespawnInPeaceful()` beschreibt weiterhin den
Standard des Typs, nicht die Auswertung des Overrides. Die Änderung entfernt das
Mob nicht synchron im Setter, sondern wirkt bei der Despawn-Prüfung. Der Wert
wird mit der Entity gespeichert und beim Laden wiederhergestellt. Ohne gespeicherten
Wert gilt `NOT_SET`. Die frühere Deprecation der beiden Override-Methoden entfällt.

Beim NBT-Laden mit Spawn-Grund `LOAD` wird ein explizit gespeichertes `FALSE`
bereits vor der Erzeugung berücksichtigt: Die Friedlich-Spawnprüfung darf diesen
gespeicherten Mob nicht vor dem Einlesen seines Overrides verwerfen.
Feature-Flags und andere Spawn-Gründe werden dadurch nicht freigeschaltet.

Lesen und Schreiben auf dem Mob-Owner ausführen. Tasks über den EntityScheduler
folgen einem Regions-/Dimensionswechsel; bei Retirement wird die Aktion nicht
mehr ausgeführt. Ein Plugin-Disable setzt bereits gespeicherte Overrides nicht
automatisch zurück. Falls die Änderung nur temporär sein soll, muss das Plugin
die Rücknahme auf den Entity-Ownern vor seinem Disable organisieren.

## SulfurCube-Ausrüstung

Signaturen auf `org.bukkit.entity.SulfurCube`:

```java
boolean swallow(ItemStack itemStack);
void setEquipped(ItemStack itemStack);
ItemStack getEquipped();
```

Die Methoden ergänzen die bestehenden Alters-, Zündungs- und Fuse-APIs.
`ItemStack` stammt aus `org.bukkit.inventory`.

### Schlucken mit Vanilla-Verhalten

`swallow` kopiert einen Gegenstand aus dem übergebenen Stack; das Argument wird
nicht verbraucht. Ein Baby schluckt nicht. Derselbe bereits geschluckte Item-Typ
wird nicht erneut ausgetauscht. Beim Austausch wird der vorherige Gegenstand
gedroppt und der Schluck-Sound abgespielt. Mit `ItemStack.empty()` lässt sich der
Slot leeren. Items außerhalb des Tags `SULFUR_CUBE_SWALLOWABLE` werden im Client
möglicherweise nicht passend dargestellt.

Die Rückgabe gibt an, ob der native Schluckvorgang die Ausrüstung ersetzt hat.
Ein Baby, derselbe Item-Typ oder ein vor dem Austausch durch Callback ungültig
gewordener Owner liefern `false`. Das ist **keine atomare Transaktion**: bereits
ausgelöste Drops oder Plugin-Events werden nicht zurückgerollt. Nach einem
Callback können Entity-Entfernung oder Transfer weitere Nebenwirkungen wie den
Sound verhindern, obwohl der Austausch bereits erfolgt ist.

Der native Pfad kann `EntityEquipmentChangedEvent` und beim Drop
`EntityDropItemEvent` sowie `EntityAddToWorldEvent` auslösen. Während eines
Tracking-Statuswechsels kann Moonrise das Entfernen von Entities ablehnen;
insbesondere ist `EntityAddToWorldEvent` kein allgemein zulässiger Ort dafür.
Dies ist kein künstlicher Spieler-Interact-Aufruf
und erzeugt nicht automatisch `SulfurCubeSwallowItemEvent`. Eventhandler dürfen
keine anschließende gültige Ownership voraussetzen; siehe [Events](events.md).

### Direkter Slot-Zugriff

`setEquipped` und `getEquipped` sind Convenience-Methoden für `EquipmentSlot.BODY`.
Der Setter übernimmt den Stack ohne den Schluck-/Drop-Ablauf; der Getter liefert
eine Kopie, bei leerem Slot einen leeren Stack. Zum Ändern die Kopie zurücksetzen.
Die normale Equipment-Änderungserkennung und deren Event bleiben erhalten;
ein Event ist nicht als synchroner Abschluss-Callback des Setters zugesichert.

Leere Stacks ausdrücklich mit `ItemStack.empty()` übergeben. `null` gehört nicht
zum öffentlichen Non-null-Vertrag. Die Methoden sind synchron und planen sich
nicht selbst ein. Fremder Thread-Zugriff wird durch die Folia-Ownership-Prüfung
abgewiesen. Der EntityScheduler erledigt die Zuordnung zum aktuellen Besitzer;
bei einem nicht mehr gültigen Entity darf keine verspätete Aktion erfolgen.

### Scheduler-Beispiel

Die vollständige Hilfsklasse zeigt eine einmalige Aktion. Ein Retirement bedeutet
hier bewusst „nichts mehr ausführen“. Die Scheduler-Aufgaben gehören dem Plugin
und werden beim Disable nicht nachträglich auf einem fremden Executor fortgesetzt.

<!-- compile: CubeEquipmentExample -->
```java
import net.kyori.adventure.util.TriState;
import org.bukkit.Material;
import org.bukkit.entity.SulfurCube;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class CubeEquipmentExample {
    public static void configure(Plugin plugin, SulfurCube cube) {
        if (!plugin.isEnabled()) return;
        cube.getScheduler().run(plugin, task -> {
            if (!plugin.isEnabled() || !cube.isValid()) return;
            cube.setDespawnInPeacefulOverride(TriState.NOT_SET);
            cube.swallow(new ItemStack(Material.STONE));
            // Plugin callbacks may have removed or transferred the cube.
            // Schedule any follow-up on its EntityScheduler again.
        }, () -> {});
    }
}
```

## Poplar-Bäume

`org.bukkit.TreeType.POPLAR` ist Papers allgemeine Poplar-Konstante. Bei
`World#generateTree` beziehungsweise `RegionAccessor#generateTree` verwendet sie
wie Paper die orange Poplar-Variante. `RED_POPLAR`, `ORANGE_POPLAR` und
`YELLOW_POPLAR` bleiben als vorhandene Sinopia-Konstanten nutzbar.

Die bisherigen farbspezifischen Typen in Tesseras natürlichen Wachstums-Events
bleiben erhalten. Plugins, die auf Poplar-Wachstum filtern, sollten deshalb alle
vier Konstanten berücksichtigen; `POPLAR` ist kein Java-Enum-Alias der anderen.
Enum-Namen verwenden, keine persistierten Ordinalzahlen.

Die bestehenden `generateTree`-Overloads und Rückgaben bleiben unverändert:
`true` bedeutet erfolgreiche Generierung, `false` einen erfolglosen
Generierungsversuch. Block-Prädikate können einzelne Änderungen ablehnen.
Vorbereitung asynchron laden, die Generierung danach über den RegionScheduler
ausführen. Kein Future-Abschluss allein berechtigt zu Blockzugriffen. Der
Baum und alle betroffenen Nachbarpositionen müssen geladen und in der eigenen
Region sein; diese neue Konstante umgeht keine bestehenden Grenzen.

## Plattformgrenzen

### Spawn-Passagiere und DragonBattle

Ab Build 016 übernimmt der geerbte `RegionAccessor#spawn`-Pfad auch natürliche
Passagiere in die Welt. Ein im Pre-Spawn-Consumer bereits hinzugefügter gültiger
Passagier verhindert nicht mehr das Hinzufügen der Wurzel. Bestehende Entities
werden nicht ein zweites Mal gespawnt. Die Signaturen und Spawn-Grund-Events
bleiben unverändert. Die komplette Passagiergruppe muss im selben geladenen
Owner-Kontext liegen; Cross-Region-Gruppen werden vor der Mutation abgewiesen.
Callbacks können Entities entfernen oder transferieren; der Server prüft den
Besitzer vor jeder weiteren Aufnahme erneut. Für Transfers den EntityScheduler
verwenden und keine alten Welt-/Regionsreferenzen nach einem Callback weiterbenutzen.

`org.bukkit.boss.DragonBattle#initiateRespawn(Collection<EnderCrystal>): boolean`
unterstützt nun auch eine leere gültige Kristallliste. Die Rückgabe bedeutet,
dass die Respawn-Sequenz gestartet wurde, nicht dass bereits ein Drache entstanden
ist. Ohne zuvor getöteten Drachen oder bei laufendem Respawn liefert sie `false`.
`null`-Einträge und Kristalle anderer Welten werden wie in Paper herausgefiltert;
eine danach leere Liste darf den API-Respawn starten. Der Aufruf gehört zum
Besitzer der geladenen End-Arena, verwendete Kristalle ebenfalls. Eine nicht
vollständig besessene Arena führt zu `IllegalStateException`, ungültige entfernte
Kristalle zu `IllegalArgumentException`; Zugriff auf einen fremden Kristallbesitzer
wird durch Folias Threadprüfung abgewiesen. Ungeladene oder fremde Regionen
werden nicht vom Global-Thread synchron mutiert. Entfernen oder Transfer
eines benötigten Kristalls bricht die Sequenz regionssicher ab. Die normale
Vanilla-Anforderung mit vier Kristallen wird durch diesen API-Pfad nicht geändert.

Nicht jeder ältere Paper-/Folia-Build enthält diese API-Mitglieder. Gegen die
passende Tessera-API kompilieren und bei mehreren Zielplattformen entsprechende
Adapter verwenden. Die Ergänzungen machen keine ansonsten nicht Folia-fähigen
Plugins regionssicher. Siehe [Projekt-Setup](project-setup.md) und
[Plattformkompatibilität](compatibility.md).
