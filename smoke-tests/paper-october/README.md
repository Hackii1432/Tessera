# Native Oktober-Regressionen

Dieser Runner startet die angegebene finale Tessera-JAR mit Java 25 in einem
neuen, markierten Fixture-Verzeichnis unter `build/`. Er verwendet zwei echte
Offline-Protokollclients und das separate `test-plugin`, keinen MCC-/MVE-
Produktcode und keine produktiven Welten.

```powershell
.\gradlew.bat :test-plugin:jar
node smoke-tests/paper-october/run.mjs "C:\Program Files\Java\jdk-25.0.3\bin\java.exe" build/libs/tessera-server-26.3.build.016-beta.jar
```

Geprüft werden zwei getrennte Regionsbesitzer, explizite Live-/Snapshot-
Blockzustände, BlockType-Instrument, regionslokale Rüstungsständer-Schadensquelle,
Spawns mit bereits gültigen und noch nicht registrierten Passagieren sowie
Shelf-Netzwerk- gegenüber Speicherkodierung in beiden Cache-Reihenfolgen.
Ein tatsächlicher Welt-Unload und Reload muss verschachtelte Bundle-Inhalte
exakt erhalten und Entities genau einmal laden.

`result.json` enthält JAR-Prüfsumme, Fixture-Pfad, Port, konkrete Assertions
und Login-/Kick-/Disconnect-Zähler. Verbindungen werden nur bis zur bestätigten
Abnahme gezählt, nicht über den anschließend absichtlich ausgelösten Shutdown.
Ein Fehler, Timeout oder Cross-Region-Verstoß erzeugt Exitcode 1.

Die Clients belegen echte Verbindungen, nicht die visuelle Darstellung in einem
Vanilla-Client. Shelf-Kodierung und Persistenz werden im nativen Serverpfad
geprüft; daraus folgt keine vollständige Plugin-Stack-Abnahme. Weitere Tests
für Fehlerfälle von Buckets, Codecs, Registry, Chat, Spam-Schwellen und
Post-Effects stehen in `PaperOctoberRegressionTest` und der vorhandenen Suite.
