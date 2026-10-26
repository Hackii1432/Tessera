---
title: "Rezeptkataloge und Batch-APIs"
description: "Paper-Rezepte in Tessera atomar auf dem Global-Besitzer ändern und Clients auf ihren aktuellen Entity-Besitzern aktualisieren."
navTitle: "Rezepte"
order: 135
updated: 2026-10-26
minecraftVersion: "26.3"
badge: "Paper-Kompatibilität"
---

Build 019 integriert Papers Batch-Rezept-APIs mit einer Folia-spezifischen
Umsetzung. Der gemeinsame Katalog wird unveränderlich publiziert; Spieler in
anderen Regionen lesen keinen teilweise veränderten Katalog. Es handelt sich
um die bestehende Bukkit-/Paper-Oberfläche, nicht um eine neue Transaktions-API.

## Signaturen und Ergebnisse

Auf `org.bukkit.Server` und als statische Bukkit-Delegation verfügbar:

```java
boolean addRecipes(Iterable<Recipe> recipes);
boolean removeRecipes(Iterable<NamespacedKey> keys);
boolean hasRecipe(NamespacedKey key);
boolean addRecipe(Recipe recipe);
boolean removeRecipe(NamespacedKey key);
```

`addRecipes` konvertiert die unterstützten Bukkit-Rezepte zuerst vollständig,
prüft Duplikate und publiziert dann den ganzen Batch. `true` bedeutet mindestens
ein hinzugefügtes Rezept; leere Batches und nicht konvertierbare Einträge bewirken
keine Hinzufügung. Wie beim Einzelaufruf werden nicht unterstützte Recipe-Typen
ignoriert. Ein doppelter Key im Batch oder bereits im Katalog führt zu
`IllegalStateException` und publiziert keinen Teil des Batches. Ein Konvertierungs-
fehler bleibt ebenfalls ohne teilweise Katalogmutation.

`removeRecipes` entfernt die vorhandenen angegebenen Keys in einer Publikation,
ignoriert fehlende Keys und liefert `true`, wenn mindestens eines entfernt wurde.
Ein leerer oder vollständig fehlender Batch liefert `false`. Null-Iterable oder
Null-Keys führen zu `IllegalArgumentException`; die Prüfung geschieht vor der
Publikation. `hasRecipe` liest einen veröffentlichten Katalog und liefert den
aktuellen Key-Befund; `null` ist ungültig. Zwischen zwei getrennten Abfragen kann
der Katalog bereits wechseln. Keine atomare Check-then-Add-Garantie ableiten.

`addRecipe(null)` bleibt `false`. Die Overloads
`addRecipe(Recipe, boolean resendRecipes)` und
`removeRecipe(NamespacedKey, boolean resendRecipes)` sind wie in Paper deprecated;
das boolesche Argument wird ignoriert. Erfolgreiche Änderungen aktualisieren
die Clients immer. Das Batch-API vermeidet einen vollständigen Refresh pro
einzelnem Rezept. Spieler entdecken neue Rezepte weiterhin ausdrücklich, etwa
über `Player#discoverRecipe`, auf ihrem eigenen Besitzer.

## Globaler Schreiber und parallele Leser

Ab Build 022 verwendet die Auswahl unter passenden nativen Rezepten wieder die
Vanilla-Reihenfolge. Während der Laufzeit hinzugefügte Rezepte haben Vorrang;
unter ihnen gewinnt das zuletzt passend eingefügte Rezept. Das gilt auch für
Einzel-, Batch- und Ersetzungsoperationen. Die zusätzliche Prioritätsmarkierung
ändert weder Rezeptschlüssel noch die Unveränderlichkeit veröffentlichter Kataloge.
Ein Reset auf den nativen Basiskatalog übernimmt keine alten Runtime-Markierungen.

Nach Serverinitialisierung müssen **alle** Katalogmutationen auf dem
GlobalRegionScheduler laufen: auch bisheriges `addRecipe`, `removeRecipe`,
`clearRecipes`, das Entfernen über `recipeIterator().remove()` und Potion-
Mix-Mutationen. Ein Aufruf von einer Region oder einem Async-Thread wird mit
`IllegalStateException` abgewiesen und nicht heimlich weitergeleitet. Der
kontrollierte initiale Server-Startpfad bleibt möglich. Ein Eventhandler auf
einem Spieler-/Blockbesitzer ist deshalb kein gültiger Katalogschreiber.

`hasRecipe` und Katalogiteration benötigen keine Spielerregion. Der Iterator
liest einen stabilen Snapshot. Sein `remove` entfernt den Key aus dem aktuellen
Katalog, nicht nur aus dem Snapshot, und folgt den gewöhnlichen Iterator-
Zustandsregeln. Recipe-Ergebnisse/ItemStacks oder selbst veränderbare Recipe-
Objekte nicht parallel bearbeiten; die Publikationssicherheit macht diese
Plugin-Objekte nicht allgemein thread-safe.

<!-- compile: BatchRecipesExample -->
```java
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

public final class BatchRecipesExample {
    public static void register(Plugin plugin) {
        if (!plugin.isEnabled()) return;
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            if (!plugin.isEnabled()) return;
            var first = new ShapelessRecipe(new NamespacedKey(plugin, "first"),
                new ItemStack(Material.DIAMOND)).addIngredient(Material.DIRT);
            var second = new ShapelessRecipe(new NamespacedKey(plugin, "second"),
                new ItemStack(Material.EMERALD)).addIngredient(Material.STONE);
            Bukkit.addRecipes(List.of(first, second));
        });
    }
}
```

Das Beispiel ist eine einmalige Registrierung; wiederholter Aufruf verursacht
Duplikatfehler. Scheduling kann beim Disable abgewiesen werden. Eine spätere
Entfernung im Rahmen einer kontrollierten aktiven Cleanup-Phase ebenfalls global
ausführen; nach Disable keine neuen Tasks unter dem deaktivierten Plugin erzwingen.
Serverneustart lädt Datapack-Rezepte neu, Pluginrezepte müssen erneut registriert
werden. Bereits bekannte Spieler-Rezeptkeys bleiben serverseitig erhalten;
entfernte Displays verschwinden durch einen ersetzenden Client-Reset.

## Client-Updates und Transfers

Die bestehenden Server-Methoden sind parameterlos und liefern keinen Wert:

```java
void updateRecipes();
void updateResources();
```

`updateRecipes` plant aktuelle Rezeptdaten und einen vollständigen RecipeBook-
Reset für jeden verbundenen Spieler ein. `updateResources` plant zusätzlich
Advancement-Resets und sendet die aktuellen Tags. Sie laden keine neuen
Datapacks. Globale Organisation ist der passende Plugin-Aufrufkontext; kein
Aufruf verleiht Zugriff auf fremde Spieler. Rückkehr bestätigt weder Abschluss
aller Owner-Aufgaben noch Empfang oder Darstellung im Client. Die API bietet
dafür kein Future.

Tesseras interne Aufgaben laufen auf dem aktuellen Entity-Besitzer, folgen
Regions-/Dimensionswechseln und lösen nach Respawn die aktuelle Entity auf.
Getrennte oder retirierte Spieler erhalten keine verspätete Reset-Mutation.
Diese interne Ressourcenarbeit läuft auch bei `tick freeze`; Gameplay bleibt
angehalten. Normale Plugin-EntityScheduler-Aufgaben erhalten dadurch keinen
neuen Freeze-Bypass. Rezepte, Displays und RecipeBook-Reset eines Aufrufs werden
aus einem gemeinsamen unveränderlichen Katalog entnommen. Entfernte Display-IDs
werden nicht für fremde neue Rezepte wiederverwendet.

Der ältere `PotionBrewer#resetPotionMixes()` publiziert seine Brewing-Ersetzung
ebenfalls als einen globalen Batch. Er ist weiterhin deprecated zugunsten von
`BrewingRecipe`. `resetRecipes` bleibt der bestehende datengetriebene Reload-Pfad,
keine neue atomare Mehrwelt-/Datapack-Transaktion. Für Scheduling und asynchrone
Folgearbeit gelten [Ownership](ownership.md) und [Scheduler](scheduler.md).
