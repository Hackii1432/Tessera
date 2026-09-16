# Tick-Befehl: Zugriff nur für OPs

## Änderung

`minecraft.command.tick` war bereits standardmäßig eine OP-Berechtigung.
Ein Plugin konnte sie jedoch auch normalen Spielern geben. Dadurch konnten
diese Spieler bisher `/tick` benutzen.

Tessera prüft nun zusätzlich den tatsächlichen OP-Status des Absenders.
Eine einzeln vergebene Berechtigung reicht für Nicht-OPs nicht mehr aus.
Das gilt für `/tick`, `/minecraft:tick` und sämtliche Unterbefehle.
Konsole und RCON behalten ihren bisherigen Zugriff. Bei `/execute as` zählt
der ursprüngliche Absender. Funktions-Kompilierung behält die bisherige
Prüfung der Vanilla-Berechtigungsstufe.

## Prüfung am 16.09.2026

`TickCommandOperatorTest` prüft die echte Registrierung und das Parsen der
Befehle, einschließlich beider Arten von Namespace-Aliasen. Absender und
Spieler sind Testdoubles; ein Minecraft-Client wird nicht gestartet.

- Vor der Korrektur: 17 Tests, davon 5 fehlgeschlagen wegen erlaubtem Nicht-OP-Zugriff.
- Nach der Korrektur: **17 bestanden**, keine Fehler oder übersprungenen Tests.
- Erfasst: Nicht-OP mit und ohne Permission, OPs verschiedener Stufen,
  OP-Entzug, geänderter `/execute`-Ausführer, Konsole, RCON und Funktionskontexte.
- `scanJarForBadCalls` und `createPaperclipJar` erfolgreich; die korrigierte
  JAR liegt unter `build/libs/tessera-server-26.3.build.005-alpha.jar`.

Die Änderungen sind dauerhaft in Minecraft-Patch `0039` und Server-Testpatch
`0030` gespeichert. Die Patchinhalte wurden gegen die Arbeitsquellen geprüft.
Der laufende Server erhält die Änderung erst nach Austausch seiner JAR und
einem Neustart.
