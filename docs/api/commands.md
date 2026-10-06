---
title: "Konsole, RCON und Befehlsgrenzen"
description: "Serverseitiger Weltkontext, regionsichere Abfragen und Grenzen für Pluginbefehle."
navTitle: "Befehle"
order: 140
updated: 2026-10-06
minecraftVersion: "26.3"
badge: "Referenz"
---

Tessera korrigiert Konsolen-/RCON-Kontexte und unterstützt ausgewählte
regionsichere Vanilla-Abfragepfade. Dafür müssen Plugins keine internen
`CommandSourceStack`-Instanzen erzeugen.

## Startphase und Weltkontext

Vor fertiger Serverinitialisierung eintreffende stdin-Befehle werden als Text
FIFO-gepuffert und danach mit gültigem Standardweltkontext ausgeführt. Explizite
Dimensionskontexte, Spieler, Entities, Commandblocks und Pluginquellen behalten
ihren Kontext. RCON baut seinen Standardkontext bei Dispatch auf dem Global-
Thread auf. Dies gilt auch für `stop` und Pluginbefehle.

## Unterstützte Abfragen

- `execute in <dimension> if block ...` / `unless block ...`, Blocktypen und Tags.
- `data get block` als read-only Abfrage auf dem Regionsbesitzer.
- Ortsgebundene Entity-Selektoren innerhalb der zulässigen Region.
- Unbeschränkte Entity-Typ-/UUID-Auswahl über den nebenläufigen Index. Unterstützte
  Mutationsbefehle wie `kill` verteilen die Arbeit auf den jeweiligen Besitzer;
  die Auswahl allein verleiht einem beliebigen Pluginhandler keine Ownership.

Räumliche Cross-Region-Suchen oder ungeladene unzulässige Ziele werden abgewiesen,
nicht synchron vom Global-Thread gelesen. `data merge/modify/remove block` sind
kein pauschal freigeschalteter Schreibpfad. Eine passende globale Befehlsquelle
macht fremde Block-/Entity-Operationen nicht threadsicher.

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
