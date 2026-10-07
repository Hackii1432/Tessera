---
title: "Konsole, RCON und Befehlsgrenzen"
description: "Serverseitiger Weltkontext, regionsichere NBT-Befehle und Grenzen für Pluginbefehle."
navTitle: "Befehle"
order: 140
updated: 2026-10-07
minecraftVersion: "26.3"
badge: "Referenz"
---

Tessera korrigiert Konsolen-/RCON-Kontexte und unterstützt ausgewählte
regionsichere Vanilla-Abfrage- und NBT-Änderungspfade. Dafür müssen Plugins keine internen
`CommandSourceStack`-Instanzen erzeugen.

## Startphase und Weltkontext

Vor fertiger Serverinitialisierung eintreffende stdin-Befehle werden als Text
FIFO-gepuffert und danach mit gültigem Standardweltkontext ausgeführt. Explizite
Dimensionskontexte, Spieler, Entities, Commandblocks und Pluginquellen behalten
ihren Kontext. RCON baut seinen Standardkontext bei Dispatch auf dem Global-
Thread auf. Dies gilt auch für `stop` und Pluginbefehle.

## Unterstützte Abfragen

- `execute in <dimension> if block ...` / `unless block ...`, Blocktypen und Tags.
- `data get block` auf dem Regionsbesitzer, einschließlich NBT-Pfad und Skalierung.
- Ortsgebundene Entity-Selektoren innerhalb der zulässigen Region.
- Unbeschränkte Entity-Typ-/UUID-Auswahl über den nebenläufigen Index. Unterstützte
  Mutationsbefehle wie `kill` verteilen die Arbeit auf den jeweiligen Besitzer;
  die Auswahl allein verleiht einem beliebigen Pluginhandler keine Ownership.

Räumliche Cross-Region-Suchen oder ungeladene unzulässige Ziele werden abgewiesen,
nicht synchron vom Global-Thread gelesen. Eine passende globale Befehlsquelle
macht fremde Block-/Entity-Operationen nicht threadsicher.

## Whitelist und Profilabfragen

Der geerbte Vanilla-Befehl `whitelist` behält seine Unterbefehle und die
Berechtigung `minecraft.command.whitelist` (standardmäßig OP). Tessera bereitet
unbekannte Namen für `add` und `remove` bei Konsolen-, RCON-, Spieler- und
Bukkit-Dispatch außerhalb der Tickthreads vor; auch `minecraft:whitelist` und
vorgeschaltete `execute`-Kontexte werden berücksichtigt. Das ist eine interne
Befehlsanpassung, keine neue Plugin-API.

```text
whitelist add playername
minecraft:whitelist add PlayerName
whitelist remove PLAYERNAME
```

Gültige Cache-Einträge und bei `remove` bereits vorhandene Whitelist-Einträge
werden ohne Beachtung der Groß-/Kleinschreibung gefunden. Ein veralteter Name
in der Whitelist allein bestimmt bei `add` nicht die aktuelle Besitzer-UUID;
umbenannte beziehungsweise erneut vergebene Namen werden frisch aufgelöst.
Online-Profilanfragen und ihre gemeinsamen
In-flight-Schlüssel verwenden `Locale.ROOT`; UUID und kanonische Schreibweise
der Antwort bleiben erhalten. Die Vanilla-Ableitung bisher unbekannter
Offline-UUIDs aus dem exakten Namen wird nicht geändert. Online-/Proxy-Modus
und Login-Authentifizierung bleiben ebenfalls unverändert.

Eine tatsächlich fehlende Profilantwort, einschließlich HTTP 404, liefert den
Vanilla-Fehler für einen unbekannten Spieler. Dienstfehler, Rate-Limits,
Netzwerkfehler, Timeout und volle Abfragekapazität liefern stattdessen einen
englischen, erneut versuchbaren Dienstfehler. Fehlversuche werden nicht negativ
gecached. Zwei Worker und höchstens 32 wartende Anfragen begrenzen die Last;
die Profilvorbereitung hat eine Frist von zehn Sekunden. Nach einer verspäteten
Antwort wird kein nachträglicher Whitelist-Eintrag angelegt.

Nach der Abfrage läuft die Befehlsausführung auf dem ursprünglichen
Quellkontext weiter: global für Konsole/RCON, auf dem aktuellen Entity-Besitzer
für Spieler, auf dem zuständigen Regions-Thread für andere regionale Quellen.
Die Vorbereitung und interne Fortsetzung laufen auch bei `tick freeze`;
die normale Spielsimulation wird dafür nicht freigegeben. Berechtigungen werden
erneut geprüft. Disconnect, Entity-Retirement und Serverstop verhindern eine
später fortgesetzte Änderung. Selektoren behalten ihre bestehenden lokalen
Ownership-Regeln und werden nicht auf dem Abfrage-Worker ausgewertet.

`PreLookupProfileEvent` und die anschließende erfolgreiche Netzwerkbenachrichtigung
`LookupProfileEvent` können hier asynchron laufen; ihr geerbter Vertrag
garantiert keinen festen Thread. Regionsdaten erst über den passenden
[Scheduler](scheduler.md) verwenden. `WhitelistStateUpdateEvent` läuft beim
eigentlichen Add/Remove wieder auf einem Tickthread; sein Veto wird respektiert
und nicht als erfolgreiche Änderung gemeldet. Bereits zuvor ausgeführte
Änderungen einer Mehrziel-Abfrage werden durch ein späteres Veto nicht zurückgerollt.

RCON wartet außerhalb des Global-Ticks bis zur endgültigen Antwort.
Der synchrone Rückgabewert von `Bukkit.dispatchCommand` ist dagegen kein
Nachweis, dass eine externe Abfrage und Whitelist-Änderung bereits beendet sind.
Datapack-Funktionen und direkte interne Brigadier-Ausführung besitzen keinen
solchen asynchronen Dispatch-Vertrag: bekannte Namen bleiben verwendbar,
unbekannte Namen werden dort explizit abgewiesen, statt einen Tickthread mit
einer Netzwerkabfrage aufzuhalten. Keine der internen Hilfsklassen ist eine
öffentliche Erweiterung der Bukkit-/Paper-API.

## NBT-Befehle

Seit Build 017 registriert Tessera folgenden Vanilla-Teilumfang unter `/data`
und dem geerbten Alias `/minecraft:data`:

| Ziel | Lesen | Änderungen |
| --- | --- | --- |
| `block <pos>` | `get [path] [scale]` | `merge`, `remove`, `modify` auf dem aktuellen Besitzer einer geladenen Blockentity |
| `entity <target>` | `get [path] [scale]` auf dem aktuellen Entity-Besitzer | Keine beliebigen Entity-NBT-Mutationen; auch `execute store ... entity` ist nicht registriert |
| `storage <namespace:key>` | `get [path] [scale]` mit abgetrennten NBT-Daten | `merge`, `remove`, `modify` mit atomarem Read/Modify/Write |

`modify` unterstützt die Vanilla-Operationen `set`, `merge`, `append`, `prepend`
und `insert <index>` mit `value`, `from`, `string` einschließlich Substring und
den in 26.3 vorhandenen `compute`-Float-/Integer-Providern. Diese serverseitige
Befehlsunterstützung ist keine neue öffentliche NMS- oder Transaktions-API.

### Besitz, Quelle und Ergebnis

Spieler- und Blockquellen bleiben auf ihrem Besitzer. Ein Spieler kann also
`data get entity @s Health` lesen, nicht beliebig die NBT einer fremden Region.
Entity-Selektoren mit NBT-Abfragen benötigen `@s`, einen Spielernamen/eine UUID
oder eine begrenzte Suchfläche vollständig innerhalb der aktuellen Region;
unbegrenzte Selektoren wie `@e[nbt=...,limit=1]` werden vor der Auswertung der
Live-Predikate abgewiesen. Dies gilt auch für vorgeschaltete `execute`-Selektoren
in einem NBT-Befehl. `limit=1` allein begrenzt die Suchfläche nicht.
Begrenzte Spieler-Suchen werten ihre Filter nur auf regionslokalen Spielern aus.
Ein durch `execute as` auf eine fremde Entity gesetztes `@s` darf dort keine
Live-Filter ausführen; die Ausführung wird vor der Filterauswertung abgewiesen.

Konsole und RCON leiten bekannte Blockpositionen und Entity-Namen/UUIDs vor der
Ausführung auf den Zielbesitzer weiter. Für räumliche Entity-Selektoren einen
passenden Dimensions-/Positionskontext angeben. Bei Regions-/Dimensionswechsel
wird der Besitzer erneut aufgelöst; eine bereits entfernte Entity wird nicht
aus einem alten Handle serialisiert. RCON wartet außerhalb des Global-Ticks auf
die weitergeleitete Ausführung; Tick-/Regions-Threads werden dafür nicht blockiert.
Die interne Regions-Taskqueue verarbeitet diese Arbeit auch bei `tick freeze`,
ohne normale Welt-/Entity-Simulation freizugeben.

`from` und `string` dürfen eine Block-/Entity-Quelle nur auf deren Besitzer lesen.
Quelle und ein räumliches Ziel müssen derselben aktuell besessenen Region
angehören. Eine Storage-Quelle ist abgetrennt und regionsübergreifend lesbar;
eine Storage-Mutation mit lokaler Block-/Entity-Quelle ist ebenfalls zulässig.
Eine atomare Kopiertransaktion zwischen zwei getrennten Regionen oder Dimensionen
wird damit **nicht** angeboten. `compute` benötigt seinen geladenen lokalen
Quellkontext auch bei einem Storage-Ziel.

NBT-Mutationsoperationen behalten die Vanilla-Ergebniswerte und Meldungen.
`execute store result/success block|storage` nutzt dieselben Schreibprüfungen;
ein späterer Callback prüft Blockbesitz und Blockentity-Identität erneut.
`execute if/unless data block|entity|storage` verwendet dieselben lesenden
Accessor-Schutzregeln. Ein Fehler publiziert keine teilweise veränderte
Storage-NBT; parallele Mutationen desselben Dokuments verlieren keine fremden
Felder. Leseantworten, Suggestions und Save-/Snapshot-Kodierung behalten keine
veränderbaren Live-NBT-Referenzen. Dies ist keine Mehrdokument-/Mehrwelttransaktion.

### Verwendung und Fehlerfälle

Die folgenden Beispiele sind normale Befehle für eine berechtigte Konsole/RCON;
Koordinaten müssen eine bereits geladene Blockentity bezeichnen:

```text
execute in minecraft:overworld run data get block 8 70 8
execute in minecraft:overworld run data merge block 8 70 8 {Command:"say example"}
data get entity PlayerName Health
data merge storage example:state {counter:1,values:[1,2]}
data modify storage example:state values append value 3
execute store result storage example:state length int 1 run data get storage example:state values
```

Die geerbte Berechtigung `minecraft.command.data` beziehungsweise Vanilla-
Gamemaster-Berechtigung bleibt erforderlich; OP erfüllt sie standardmäßig.
Fehlende Blockentities, ungeladene/fremde Besitzer, verschwundene Entity-Ziele,
ungültige Pfade/Typen, unveränderte Mutationen und unzulässige Selektoren liefern
Befehlsfehler. Spieler-NBT kann wie in Vanilla nicht mit `merge/remove/modify`
geschrieben werden. Ein normaler Block ohne Blockentity hat kein `/data`-NBT.

Für Plugin-Disable oder mehrstufige asynchrone Pluginarbeit gelten unverändert die
[Scheduler-Verträge](scheduler.md). Ein verzögerter Callback verleiht keine
Ownership, und `Bukkit.dispatchCommand` bestätigt keine gesamte Transaktion.

## Pluginbefehle und Berechtigungen

### BasicCommand und Syntaxfehler

Die geerbte Paper-Signatur lautet seit Build 016:

```java
void execute(CommandSourceStack commandSourceStack, String[] args)
    throws com.mojang.brigadier.exceptions.CommandSyntaxException;
```

`commandSourceStack` enthält Sender und Ausführungskontext, `args` die Argumente
ohne Befehlsnamen. Der Handler liefert keinen Wert; ein geworfener
`CommandSyntaxException` wird vom Brigadier-Dispatcher als Befehlsfehler behandelt.
Bestehende Implementierungen ohne `throws` bleiben gültig. Wer `execute` selbst
direkt aufruft, muss die geprüfte Exception beim Neukompilieren behandeln.
Das JVM-Methodendescriptor und bestehende Plugin-Binärdateien ändern sich nicht.

Der Handler läuft im jeweiligen Dispatch-Kontext, nicht automatisch auf dem
Besitzer beliebiger Ziel-Entities. Für asynchrone Folgearbeit und Plugin-Disable
gelten weiterhin die [Scheduler-Verträge](scheduler.md). Keinen Regions-Thread
blockieren, um eine asynchrone Antwort noch synchron als Syntaxfehler auszugeben.

<!-- compile: SyntaxCommandExample -->
```java
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

public final class SyntaxCommandExample implements BasicCommand {
    @Override
    public void execute(CommandSourceStack source, String[] args) throws CommandSyntaxException {
        if (args.length != 1) {
            throw new SimpleCommandExceptionType(new LiteralMessage("Expected one argument")).create();
        }
        source.getSender().sendMessage("Argument: " + args[0]);
    }
}
```

Seit Build 011 sind die klassischen Bukkit-Command-Typen und zugehörige
Registrierungszugänge wie in Paper mit `@ApiStatus.Obsolete(since = "26.3")`
gekennzeichnet. Sie bleiben vorhanden und ausführbar. Die Markierung empfiehlt
für neue Integrationen Papers Brigadier-/`BasicCommand`-API; sie entfernt keine
Commands und verleiht auch neuen Command-Handlern keine fremde Regions-Ownership.

`Bukkit.dispatchCommand(CommandSender, String): boolean` ist die geerbte
Dispatch-Schnittstelle. Das Ergebnis ist **kein Future**, das sämtliche intern
weitergeleiteten Regionsaufgaben bestätigt. Kein Transaktionsprotokoll aus
Chat-Ausgaben oder einem sofortigen booleschen Befehlsresultat konstruieren.
Konsolenarbeit gehört zum Global-Kontext; Spieler-/Blockarbeit im Pluginhandler
explizit nach den [Ownership-Regeln](ownership.md) verteilen. RCONs interne
Antwortkoordination ist kein öffentliches Plugin-Warteprimitive.

`/tick` verlangt für Spieler echten OP-Status; eine einzelne Permission genügt
nicht. `/tps` nutzt `bukkit.command.tps` und englische, klickbare Regions-/Spieler-
Ansichten. Der Befehl ist keine zusätzliche öffentliche Regions-API; für Plugins
die [dokumentierten TPS-Overloads](region-tps.md) verwenden.

## Native Pregeneration

`/pregen` und `/tessera:pregen` verwenden `tessera.command.pregen` (Standard: OP).
`/pregen status` zeigt ausschließlich den neuesten Auftrag, unabhängig von
seinem Status; `status all` ist ein kompatibler Alias dieser Ansicht. Fortschritt,
Dimension, Modus, Simulation, Rate und ETA bleiben englisch und farblich gegliedert.
Die Schaltflächen bieten nur zum aktuellen Zustand passende Aktionen an.

Ältere abgeschlossene/abgebrochene Checkpoints werden nach dauerhafter Veröffentlichung
eines neueren Auftrags entfernt. Ältere fortsetzbare Aufträge bleiben für explizite
ID-Abfragen und Wiederaufnahme erhalten; parallele Arbeiten und erzeugte Weltdaten
werden nicht verworfen. Neustart-Recovery und `/tick freeze` bleiben unverändert.
Dies ist ein administrativer Serverbefehl, keine öffentliche Bukkit-Pregeneration-API.
