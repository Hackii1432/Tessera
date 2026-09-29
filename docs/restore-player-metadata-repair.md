# Spieler-Metadaten nach dem Restore-Preview-Fehler reparieren

Stand: 29.09.2026. Betroffen ist die native Restore-Implementierung in Build
010 und 011. Build 012 verhindert das erneute Überschreiben von Offline-
Spielerdaten durch die Validierungsvorschau. Es repariert **nicht automatisch**
bereits beschädigte Dateien. MCC-/MVE-Produktcode und API-Vertrag 1 bleiben gleich.

## Ursache und Grenzen

Der alte Preflight lud echte Dateien in einen losgelösten Spieler und speicherte
diesen wieder. Dabei konnten `bukkit.lastKnownName`, `bukkit.lastPlayed`,
`Paper.LastLogin` und `Paper.LastSeen` überschrieben werden. Der Roundtrip konnte
auch unbekannte Tags verlieren. Eine reine Namenskorrektur beweist daher nicht,
dass der vollständige ursprüngliche Datensatz wiederhergestellt wurde.

`RestorePreview` kann ein echter Benutzername sein. Weder dieser Name allein
noch `LastLogin = 0` beweisen einen Schaden. Die Diagnose benötigt eine belegte
Änderung derselben UUID über einen betroffenen Restore. Keine Namenssperrliste,
keine gelöschten Spielerdateien und keine zusammengelegten UUIDs verwenden.

## 1. Konsistent sichern

1. Laufende Save-/Load-Transaktionen regulär abschließen oder gemäß dem
   bestehenden MCC-Recovery-Journal behandeln. Keine offene Tessera-Schranke
   umgehen und keine erhaltenen Transaktionsverzeichnisse pauschal entfernen.
2. Server geordnet stoppen. Aktive Spieler-Stores (`data`, `stats`,
   `advancements`), zugehörige Welten, MCC-Snapshots/Journale und den vollständigen
   MVE-Statistikstore einschließlich `player-statistics.json` sichern.
3. Backups getrennt ablegen, SHA-256 und Dateizeiten erfassen. Originale nicht
   bearbeiten. Die tatsächlichen Storepfade aus der eigenen Installation
   ermitteln; keine Beispielpfade blind verwenden.
4. Auch gespeicherte Challenges überprüfen: Eine bereits beschädigte Quelle
   bleibt mit Build 012 beschädigt, weil der neue Restore ihre Metadaten bewahrt.

## 2. Vorschau ohne Änderungen erstellen

Komprimierte NBT-Dateien mit einem NBT-fähigen Werkzeug **nur lesend** öffnen.
Dateiname, eingebettete UUID und die UUID des Belegs müssen zusammenpassen.
Als Beleg eignen sich nachweislich unbeschädigte Vorher-Backups oder gesicherte
Profile derselben UUID; ein beliebiger Namensfund genügt nicht. Bei historischen
Challenge-Snapshots auch den Zeitpunkt der Zuordnung beachten: legitime
Umbenennungen sind keine Beschädigung.

Für jede geplante Feldänderung einen Dry-Run-Bericht erstellen, beispielsweise:

| Datei / UUID | Feld | Ist | Belegter Sollwert | Quelle / Datum | Entscheidung |
| --- | --- | --- | --- | --- | --- |
| dieselbe geprüfte UUID | `bukkit.lastKnownName` | abweichender Name | Name aus Backup | Backup vor betroffenem Load | nur nach Prüfung ändern |
| dieselbe geprüfte UUID | `Paper.LastLogin` | `0` | exakter gespeicherter Wert | ursprüngliche NBT-Datei | nur mit Zeitbeleg ändern |
| dieselbe geprüfte UUID | `Paper.LastSeen` | Restore-Zeit | unbekannt | kein sauberer Beleg | unverändert, Verlust dokumentieren |

Zusätzlich prüfen:

- `bukkit.firstPlayed`, `bukkit.lastPlayed`, `Paper.LastLogin`, `Paper.LastSeen`.
- Vorhandensein oder Fehlen der Felder und ihrer übergeordneten Compounds.
- Unbekannte Erweiterungs-Tags, `BukkitValues`/PDC und die drei Store-Dateimengen.
- Das ursprüngliche Änderungsdatum einer `.dat`: Bukkit nutzt es bei fehlenden
  historischen Feldern teilweise als Rückfallwert. Nicht auf das Reparaturdatum
  setzen, falls das ursprüngliche Datum belegt ist.
- UUID und sämtliche Statistik-/Lifetime-Zähler vor und nach dem vorgesehenen
  Eingriff; diese müssen unverändert bleiben.

Ein Profil kann den Namen belegen, aber keine vergangenen Login-/Seen-Zeiten.
Zeitwerte niemals aus dem aktuellen Datum, dem Namen oder anderen Spielern
herleiten. Ohne historischen Beleg bleibt die Rekonstruktion ausdrücklich offen.
Ein fehlendes Feld nur dann wieder entfernen, wenn sein ursprüngliches Fehlen
belegt ist. Unbekannte verlorene Tags sind ohne sauberes Backup nicht rekonstruierbar.

## 3. Nur bestätigte Felder ändern

Erst den Dry Run prüfen und freigeben. Ausschließlich Arbeitskopien bearbeiten
und eine maschinenlesbare Vorher-/Nachher-Liste je UUID/Feld aufbewahren. Nicht
einfach die komplette alte Spielerdatei über die aktuelle schreiben: Das würde
unter anderem Inventar, Position und Spielzustand zurücksetzen.

Bestätigte Namens- und Zeitkorrekturen feldweise übertragen, alle anderen Tags
unverändert lassen. Historische Zeitwerte und ursprüngliche Feld-Abwesenheit
nur aus einem passenden Beleg übernehmen. Bei bereits verlorenen sonstigen Tags
ihre Bedeutung und ihren Zeitbezug separat prüfen; nicht blind mit aktuellen
Gameplay-Daten zusammenführen. Geänderte komprimierte Dateien erneut einlesen,
strukturierte Differenz prüfen und SHA-256 des Ergebnisses dokumentieren.

Erst bei gestopptem Server die geprüften Kopien einsetzen, ursprüngliche Dateien
weiter aufbewahren. Eine normale erneute Anmeldung kann den aktuellen Namen
korrigieren, rekonstruiert aber keine verlorene Historie.

## 4. MVE und Website kontrollieren

Mit Build 012 zunächst **ohne Spieleranmeldung** starten. Über
`Bukkit.getOfflinePlayer(uuid)` und `Bukkit.getOfflinePlayers()` Namen,
Teilnehmermenge, `getFirstPlayed()`, `getLastPlayed()`, `getLastLogin()` und
`getLastSeen()` prüfen. Anschließend den bestehenden MVE-Refresh verwenden und
den gesicherten MVE-Store vergleichen. Dafür wird hier kein neuer MVE-Befehl
oder automatischer Cache-Reset behauptet.

Falls MVE bereits falsche Identität/Zeitwerte dauerhaft übernommen hat und sein
normaler Refresh sie nicht berichtigt, auch dort ausschließlich belegte
Identitätsfelder desselben UUID-Eintrags nach eigenem Dry Run korrigieren.
Das tatsächliche JSON-Schema der installierten MVE-Version prüfen. Keine
geratenen JSON-Pfade oder globale Textersetzung verwenden. Ledger,
Lifetime-Zähler, Baselines und UUID-Schlüssel nicht löschen oder zurücksetzen.

Zum Abschluss auf einer Testkopie mehrfach Save/Load und Rollback ausführen.
Offline-NBT, Bukkit-Ausgaben und MVE-Statistikstore erneut vergleichen; der
verbundene Kontrollspieler muss ohne Reconnect verbunden bleiben. Erst danach
die korrigierte Produktionskopie freigeben. Diese Anleitung ersetzt nicht die
gemeinsame Abnahme mit der konkreten MCC-/MVE-Version.
