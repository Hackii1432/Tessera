# Paper-Beta-Regressionsprüfung

Dieser Test verwendet ausschließlich neue, markierte Fixture-Verzeichnisse unter
`build/paper-beta-smoke-*`, einen freien Loopback-Port und das separate Testplugin.
Er verändert keine produktiven Welten oder MCC-/MVE-Produktdateien.

Nach `buildTessera`, mit Node.js und JDK 25:

```powershell
node smoke-tests/paper-beta/run.mjs "C:/Program Files/Java/jdk-25.0.3/bin/java.exe" build/libs/tessera-server-26.3.build.011-beta.jar
```

Den Java-Pfad auf anderen Rechnern anpassen. Der Runner protokolliert Fixture-Pfad,
Port, SHA-256 und Ergebnis in `result.json` sowie vollständige Ausgabe in `runner.log`.
Er legt für seinen isolierten Testserver `eula=true` an; nur ausführen, wenn die
Minecraft-EULA akzeptiert wird. Der Testserver ist ausschließlich an Loopback
gebunden und verwendet Offline-Testidentitäten, keine öffentlichen Logins.

## Prüfungen

- Zwei entfernte Regionen mit Owner-Prüfung und echten Bukkit-/NMS-Aufrufen.
- Mob-Override auf Friedlich: FALSE bleibt bestehen, TRUE despawnt trotz Persistenz.
- SulfurCube: Baby/Erwachsener, Eingabekopie, Einzelgegenstand, Wiederholung,
  Austausch/Drop, direkter Slot, leere Ausrüstung und isolierte Getter-Kopie.
- Entfernung im Drop-Callback: keine weitere Ausrüstungsänderung am entfernten Cube.
- Alle vier Poplar-Konstanten über den realen Baumgenerator mit Block-Prädikat.
- Gespeicherter Runtime-World-Unload und Reload: dieselben Entity-UUIDs und
  wiederhergestellter Override/Ausrüstung, keine zusätzlichen Zombies/Cubes.

Der zusätzliche Modus `TESSERA_SMOKE_MODE=full` startet die bereits vorhandene
Runtime-World-Lifecycle-Suite auf derselben ausführbaren JAR. Verbundene Clients
und Snapshot-/Restore-Regressionsprüfungen laufen separat über
`smoke-tests/native-player-restore/run.mjs`. Keine sichtbare Vanilla-Client-Prüfung
und keine pauschale Plugin-Kompatibilitätszusage aus diesen Tests ableiten.
