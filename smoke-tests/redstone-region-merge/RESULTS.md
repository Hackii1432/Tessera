# Lokaler Redstone-Merge-Test, 16.09.2026

## Ergebnis

**Bestanden.** In zwei abschließenden, vollständigen Serverläufen funktionierten
die getesteten langen Redstone-Leitungen während echter Regionen-Merges genauso
wie in ihren Vergleichsläufen. Es gingen keine Impulse verloren, es entstanden
keine zusätzlichen Impulse, und Reihenfolge, Verzögerung in Spielticks sowie
Impulsbreite blieben identisch.

Die Auswertung umfasst **6 nachgewiesene Regionen-Merges, 120 eingespeiste
Impulse** (60 im Vergleichslauf, 60 im Merge-Lauf) und **35.840 protokollierte
Repeater-Schaltflanken**. Jeder Server wurde anschließend sauber beendet.

## Umgebung und Aufbau

- Tessera `26.3-5-4ac2731 (MC: 26.3)`, Build `005-alpha`.
- Lokal vorhandener, unveränderter Bundler; JDK 25.0.3; vier Region-Threads.
- Standard-Regionsraster: `grid-exponent: 4`.
- Pro Serverlauf eine neue flache Testwelt, Seed `829104`, keine Spieler.
- Zwei Schaltungen je Länge, zunächst in getrennten Regionen, Abstand 2.048 Blöcke.
- Ein Repeater auf Stufe 1 je vier Leitungsblöcke; dazwischen Redstone-Staub.
- Fünf Impulse je Schaltung und Phase, mit Breiten von 4, 8, 12, 16 und 20 Spielticks.
- Der Merge wurde durch zusätzlich geladene Chunks zwischen den Regionen ausgelöst.
- Beim längsten Paar erhielt Region B in beiden Phasen 65 ms zusätzliche Wartezeit
  je Tick, um unterschiedlich schnell laufende Regionen zu erzeugen.

| Leitungslänge | Repeater je Schaltung | Vergleich gegen Merge | Beobachteter Wechsel der Regionsuhr |
| --- | --- | --- | --- |
| 256 Blöcke | 64 | Beide Läufe tickgenau identisch | +24 Ticks |
| 512 Blöcke | 128 | Beide Läufe tickgenau identisch | +34 Ticks |
| 1.024 Blöcke, eine Region verlangsamt | 256 | Beide Läufe tickgenau identisch | −337 bzw. −340 Ticks |

Beim beobachteten Regionswechsel standen jeweils 2, 4 bzw. 6 Block-Updates aus.
Die Regions-IDs waren vorher verschieden und danach gleich. Die gesamte
Schaltung blieb bei jeder Tick-Abtastung geladen, korrekt zugeordnet und
block-tickend. Nach dem Auslaufen waren alle Repeater ausgeschaltet und keine
Block-Updates mehr ausstehend.

## Was bedeutet das praktisch?

Die Zeitumrechnung beim Merge hat in diesen Tests funktioniert: Auch ein großer
Sprung der Regionsuhr hat die laufenden Signale nicht übersprungen oder verdoppelt.
Das passt zur Implementierung in `RegionizedWorldData` und `LevelTicks`, die
ausstehende Updates um die Zeitdifferenz zur Zielregion verschiebt.

**Eine langsame gemeinsame Region kann die Schaltung in echten Sekunden
verlangsamen.** Der komplette Messabschnitt der zuvor schnellen
1.024-Block-Schaltung dauerte beispielsweise 39,560 Sekunden im Vergleichslauf
und 51,308 Sekunden im Merge-Lauf. Beide Abschnitte enthielten dieselben 792
lokalen Tick-Abtastungen und dieselben Schaltflanken. Das ist die Laufzeit des
gesamten Messabschnitts einschließlich Wartephase, nicht die Laufzeit eines
einzelnen Impulses.

Eine vollständig geladene, zusammenhängende Leitung gehört bereits zu einer
Tick-Region, auch wenn sie mehrere Rasterzellen durchquert. Geprüft wurde daher
das Zusammenführen zweier zuvor getrennter Regionen mit jeweils laufenden
Schaltungen. Redstone über ungeladene Zwischenstücke war nicht Teil dieses Tests.

## Messdaten und Wiederholung

Ausgewertete Läufe:

1. [Lauf 1: vollständige Messdaten](build/runs/20260916-003246-1/redstone-merge-result.json)
2. [Lauf 2: vollständige Messdaten](build/runs/20260916-003458-2/redstone-merge-result.json)

Die jeweiligen Ordner enthalten außerdem `logs/latest.log`, `stdout.log`,
`stderr.log`, `invocation.json` mit Artefakt-Hashes und `log-validation.json`.
Beide Prozesse endeten mit Exitcode 0 und ohne unerwartete Logfehler.

Auf diesem Windows-Rechner meldet OSHI beim Serverstart fehlende
Performance-Counter (`Perflib 009`). Diese konkrete Host-Diagnose ist gesondert
protokolliert; sie betrifft das Auslesen der Systeminformationen. Andere
Fehler, Thread-Verletzungen und Watchdog-Probleme führen weiterhin zum Testfehler.
Ein vorbereitender funktionaler PASS-Lauf wurde zunächst allein wegen dieser
Host-Diagnose vom Runner abgelehnt und ist in den obigen Zahlen nicht enthalten.

Bundler-SHA-256:

```text
AA0925C1ED4CF4272A0B01D838834B55530F8FBB1AF1E932B6B1B62E00CD8DBA
```

Ausführung und genaue Prüfkriterien: [README](README.md).

## Grenzen

Das Ergebnis gilt für die geprüften Staub-/Repeater-Leitungen und diesen lokalen
Build. Komparator-Rückkopplungen, gesperrte Repeater, Observer, Kolben, Fackeln,
Chunk-Unloading und Regionen-Splits wurden nicht geprüft. Zwei Wiederholungen
schließen außerdem nicht jede seltene Race Condition aus. Servercode und
bestehende Welten wurden für den Test nicht geändert.
