---
title: "Scoreboards und Entity-Tags"
description: "Nebenläufigkeitsvertrag für Bukkit-Scoreboards, Zuweisung und Spieler-Lebenszyklus."
navTitle: "Scoreboards"
order: 90
updated: 2026-09-28
minecraftVersion: "26.3"
badge: "Referenz"
---

Die Oberfläche stammt aus Bukkit/Adventure, die regionsichere Publikation aus Tessera. Entity-Tags bleiben besitzergebundener Entity-Zustand.

Capability:

```java
boolean supported = Bukkit.getTesseraCapabilities()
    .supportsRegionSafeScoreboards();
```

Ist die Capability nicht vorhanden, kann ein Plugin nur seine Sidebar
deaktivieren. BossBars, ActionBars und sonstige Spiellogik sind davon
unabhängig.

## Unterstützte Bukkit-Oberfläche

- `Bukkit.getScoreboardManager()`
- `ScoreboardManager#getMainScoreboard()`
- `ScoreboardManager#getNewScoreboard()`
- `Player#getScoreboard()`
- `Player#setScoreboard(Scoreboard)`
- `Scoreboard#registerNewObjective(...)`
- Adventure-Komponenten als Objective-Anzeigename
- `Objective#setDisplaySlot(DisplaySlot.SIDEBAR)`
- `Objective#getScore(...).setScore(...)`
- `Scoreboard#registerNewTeam(...)`
- `Team#addEntry(...)` und `removeEntry(...)`
- Adventure-Komponenten für Team-Präfix und -Suffix
- `Scoreboard#resetScores(...)`
- Entfernen von Objectives und Teams
- Wiederherstellen des vorherigen oder Main-Scoreboards

`getNewScoreboard()` erzeugt ein unabhängiges Modell. Scoreboards verschiedener
Spieler oder Arenen teilen ihre Objectives, Teams und Scores nicht.

## Threadmodell

Objective-, Team-, Score- und Display-Slot-Änderungen werden innerhalb des
jeweiligen Scoreboard-Modells serialisiert. Das Modell erzeugt unveränderliche,
versionierte Paketänderungen.

Die Anwendung auf einen Spieler erfolgt ausschließlich über dessen
Entity-Owner. Vor dem Senden prüft Tessera:

- ob der Spieler noch online ist,
- ob er weiterhin dasselbe Scoreboard verwendet,
- ob der aktuelle Entity-Owner zuständig ist,
- ob der Snapshot noch zur aktuellen Revision gehört.

Mehrere Änderungen innerhalb eines Update-Bursts werden bis zum nächsten
Spielertick zusammengefasst. Es existiert keine zusätzliche öffentliche
Tessera-Transaktionsmethode; normale Bukkit-Aufrufe werden intern gebündelt.

## Sidebar-Beispiel

```java
ScoreboardManager manager = Bukkit.getScoreboardManager();
Scoreboard previous = player.getScoreboard();
Scoreboard sidebar = manager.getNewScoreboard();

Objective objective = sidebar.registerNewObjective(
    "mcc",
    Criteria.DUMMY,
    Component.text("Monster Army Battle")
);
objective.setDisplaySlot(DisplaySlot.SIDEBAR);

for (int line = 0; line < 15; ++line) {
    String entry = "mcc_line_" + line;
    Team team = sidebar.registerNewTeam("mcc_" + line);

    team.addEntry(entry);
    team.prefix(Component.text(renderLine(line)));
    objective.getScore(entry).setScore(15 - line);
}

player.setScoreboard(sidebar);
```

Mehrere Zeilen aktualisieren:

```java
for (int line = 0; line < 15; ++line) {
    Team team = sidebar.getTeam("mcc_" + line);
    if (team != null) {
        team.prefix(Component.text(renderLine(line)));
    }
}

sidebar.resetScores("obsolete_entry");
```

Vorheriges Scoreboard wiederherstellen:

```java
Scoreboard target = previous != null
    ? previous
    : manager.getMainScoreboard();

player.setScoreboard(target);
```

Beim Plugin-Disable Update-Producer stoppen und gespeicherte eigene Boards gezielt durch die vorherigen ersetzen, sofern die Zuordnung noch zum Plugin gehört. `Player#setScoreboard` darf in Tessera auch hier aufgerufen werden; es verwendet interne Owner-Weiterleitung statt neuer Tasks des deaktivierten Plugins. Verlässt ein
Spieler während der Übergabe den Server, verwirft Tessera die veraltete
Publikation.

## Entity-Scoreboard-Tags

Entity-Scoreboard-Tags sind kein Sidebar-Scoreboard. Sie gehören direkt zum
Entity-Zustand:

```java
Set<String> getScoreboardTags();
boolean addScoreboardTag(String tag);
boolean removeScoreboardTag(String tag);
```

Diese Methoden müssen auf dem jeweiligen Entity-Owner ausgeführt werden:

```java
entity.getScheduler().execute(
    plugin,
    () -> entity.addScoreboardTag("mcc-active"),
    null,
    1L
);
```

`getScoreboardTags()` liefert unter Tessera eine unveränderliche Kopie. Eine
Entity kann maximal 1024 Tags besitzen.

## Signaturen und Fehler

`ScoreboardManager#getNewScoreboard(): Scoreboard` erzeugt ein unabhängiges Modell; `getMainScoreboard(): Scoreboard` liefert das gemeinsame Main-Board. `Player#getScoreboard(): Scoreboard` liest die aktuelle Zuweisung. `Player#setScoreboard(Scoreboard): void` ist in Tessera auch off-owner erlaubt; die Anwendung wird intern weitergeleitet. **void ist keine Abschlussbestätigung**: Ein folgendes `getScoreboard()` muss off-owner noch nicht das neue Board sehen. Stillgelegte Spieler erhalten keine Publikation.

Verwendete geerbte Methoden: `registerNewObjective(String, Criteria, Component): Objective`, `registerNewTeam(String): Team`, `Objective#getScore(String): Score`, `Score#setScore(int): void`, `Team#addEntry(String): void`, `removeEntry(String): boolean`, `Scoreboard#resetScores(String): void`. Namen müssen gültig und bei Registrierung eindeutig sein. Ungültige/null Werte, doppelte Namen oder unregistrierte Komponenten können `IllegalArgumentException` beziehungsweise `IllegalStateException` auslösen. `getTeam`/`getObjective` können `null` liefern. `setScoreboard` akzeptiert keine selbst implementierte Fremdinstanz.

Kurze Modelloperationen werden pro Board serialisiert; mehrere Aufrufe sind keine atomare Plugin-Transaktion. Das schützt nicht alle übrigen Player-Methoden. Client-Publikation prüft Revision, Board, Verbindung und Entity-Besitzer. Es gibt keinen öffentlichen Flush-/Batch-Future.

## Spielerwechsel und Prüfgrenzen

Join abonniert das Board; Quit entfernt die Zuordnung. Respawn und Welt-/Regionswechsel folgen dem aktuellen Spieler-Handle. Boardwechsel entfernt das alte Board clientseitig und publiziert das neue; das Main-Board hebt den Override auf. Tests für 50 unabhängige Boards mit je 15 Zeilen und Delta-Verarbeitung sind keine allgemeine Kompatibilitätszusage für konkurrierende Sidebar-Plugins.

## Vollständiges Sidebar-Muster

Auf dem Spieler-Owner aufrufen, damit Lesen der aktuellen Zuweisung und Setzen
keine off-owner Read-after-Write-Annahme benötigen. Mehrere Zeilen dürfen dieselbe
Prefix-Darstellung haben, benötigen aber unterschiedliche Entry-Keys. Beim Ende
der Runde auf dem aktuellen Owner das vorherige Board wiederherstellen, sofern
das Plugin noch das angezeigte Board besitzt. Disable-Details stehen oben.

<!-- compile: SidebarExample -->
```java
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

public final class SidebarExample {
    public record View(Scoreboard previous, Scoreboard board) {}

    public static View show(Player player) {
        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            throw new IllegalStateException("Player owner required");
        }
        ScoreboardManager manager = Objects.requireNonNull(Bukkit.getScoreboardManager());
        Scoreboard previous = player.getScoreboard();
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("arena", Criteria.DUMMY,
            Component.text("Arena"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int line = 0; line < 15; line++) {
            String entry = "line_" + line;
            Team team = board.registerNewTeam("row_" + line);
            team.addEntry(entry);
            team.prefix(Component.text("Value " + line + " "));
            objective.getScore(entry).setScore(15 - line);
        }
        player.setScoreboard(board);
        return new View(previous, board);
    }

    public static void restore(Player player, View view) {
        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            throw new IllegalStateException("Player owner required");
        }
        if (player.getScoreboard() == view.board()) player.setScoreboard(view.previous());
    }
}
```
