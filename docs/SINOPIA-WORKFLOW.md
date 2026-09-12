# Sinopia und Tessera – ein Repository

Sinopia ist die im Verzeichnis `sinopia/` versionierte Paper-abgeleitete Basis.
Tessera bleibt der darauf aufbauende regionsbasierte Server. Es gibt kein
Submodule, kein zweites zu klonendes Repository und keine notwendige separate
Paper-JAR-Veröffentlichung. Der Einstieg ist immer der Root-Gradle-Wrapper.

## Zentrale Befehle

Voraussetzungen: Git, Java/JDK 25 und die im Wrapper festgelegte Gradle-Version.
Build-Werkzeuge und Maven-Abhängigkeiten werden weiterhin benötigt; nur der
automatische Bezug der Paper-Git-Quellen entfällt.

`buildTessera` wendet die Patchschichten neu an. Eigene Änderungen in generierten
Tessera-Quellen deshalb vorher über die bisherigen Rebuild-Aufgaben in Patches
sichern – auch bereits dort lokal committete Feature-Änderungen. Zum reinen
Kompilieren/Testen eines bereits vorbereiteten, gerade bearbeiteten Arbeitsstands
ohne erneute Patch-Anwendung weiterhin `.\gradlew.bat test build` verwenden.

```powershell
# Lokale Sinopia-Basis -> alle Patches -> Tests -> Tessera-Paperclip
.\gradlew.bat buildTessera

# Nur alle Patchschichten vorbereiten (auch vor dem ersten IDE-Import sinnvoll)
.\gradlew.bat applyAllPatches

# Tests der neuen Build-/Git-Schutzmechanismen
.\gradlew.bat -p buildSrc selfTest
```

Auf Linux/macOS entsprechend `./gradlew` verwenden. Interne Modulnamen wie
`folia-server`, `paper-api` und die Paper-/Bukkit-Java-Pakete bleiben aus
Kompatibilitätsgründen erhalten. Sie bedeuten nicht mehrere separate Produkte.

Bei `Filename too long` unter Windows einen kurzen Checkout-Pfad verwenden oder
Git für lange Pfade konfigurieren. Für eine neue PowerShell-Sitzung ohne bereits
gesetzte `GIT_CONFIG_*`-Einträge genügt diese prozesslokale Einstellung vor dem
Gradle-Aufruf (sie verändert keine globale Git-Konfiguration):

```powershell
$env:GIT_CONFIG_COUNT = '1'
$env:GIT_CONFIG_KEY_0 = 'core.longpaths'
$env:GIT_CONFIG_VALUE_0 = 'true'
```

Das finale Artefakt liegt wie bisher unter `build/libs/tessera-server-*.jar`.
Ein fehlgeschlagener Build erzeugt keine Freigabe; vorhandene alte JARs in
diesem Verzeichnis sind kein Nachweis für einen erfolgreichen neuen Build.

## Quellen und Arbeitskopien

| Ort | Bedeutung |
| --- | --- |
| `sinopia/` | Versionierte Basis einschließlich API, Servercode, Minecraft-Patches, Lizenzen |
| `folia-api/paper-patches/` | Bestehende Tessera-/Folia-API-Patches |
| `folia-server/paper-patches/` | Bestehende Implementierungs-Patches einschließlich Tessera-Branding |
| `folia-server/minecraft-patches/` | Bestehende Regions-/Gameplay-Patches auf Minecraft |
| `build/sinopia-snapshot/` | Automatisch erzeugter Git-Snapshot für Paperweight; nicht bearbeiten |
| `build/sinopia-workspace/` | Separater, generierter Arbeitsbereich zum Bearbeiten der Sinopia-Minecraft-Patches |
| `paper-api/`, `paper-server/`, `folia-server/src/minecraft/` | Generierte Tessera-Arbeitsquellen, nicht als vollständige Vanilla-Quellen einchecken |

Der Snapshot enthält auch noch nicht im Root-Git gestagte Änderungen an
`sinopia/`. Seine Revision wird aus dem Inhalt erzeugt. Ein gleichbleibender
Inhalt liefert dieselbe Git-Revision. Der technische Paperweight-Upstream
heißt intern weiterhin `paper`, wird aber aus diesem lokalen Snapshot gespeist.

Beim Übernehmen des Umbaus in Git gehören die neuen Ordner `sinopia/` und
`buildSrc/` sowie die Build-, Dokumentations- und Branding-Patchänderungen in
denselben Tessera-Commit. Die Arbeitskopien unter `build/`, Caches und
generierte Minecraft-Quellen bleiben dagegen außerhalb der Versionsverwaltung.

`paperRef` dokumentiert nur noch die Herkunft. Eine andere Hash-Zeichenfolge
allein lädt keine neue Basis herunter. Root- und Sinopia-`mcVersion` müssen
übereinstimmen; andernfalls bricht die Vorbereitung mit einer Diagnose ab.

## Sinopia bearbeiten

Normale API-/Serverquellen und Buildkonfigurationen direkt unter `sinopia/`
ändern. Diese Änderungen gehören dann zum selben Tessera-Commit wie die dazu
passenden Folia-/Tessera-Patches.

Für Änderungen an Minecraft:

```powershell
.\gradlew.bat applySinopiaPatches
```

Dieser Befehl erstellt einen eigenen generierten Arbeitsbereich. Bei bereits
vorhandenem, passendem Arbeitsbereich bleiben Änderungen erhalten. Die
Patch-Anwendung selbst nur auf einem sauberen Minecraft-Arbeitsstand starten;
sie ist kein Ersatz für das Sichern eigener Änderungen.

Bearbeitbare Minecraft-Dateien liegen anschließend unter
`build/sinopia-workspace/paper-server/src/minecraft/java`.
Papers bestehender Feature-/Dateipatch-Workflow gilt dort weiterhin: Änderungen
an bestehende Patches anheften beziehungsweise als eigene Git-Feature-Commits
anlegen. Die Anleitung steht in `sinopia/CONTRIBUTING.md`.

Zum Anheften einfacher Änderungen an bestehende Minecraft-Dateipatches steht
der Root-Befehl `.\gradlew.bat fixupSinopiaSourcePatches` bereit. Dieser führt
Papers `fixupSourcePatches` ausdrücklich im Sinopia-Arbeitsbereich aus, nicht
in Tesseras Minecraft-Patchschicht. Neue Feature-Commits benötigen diesen
Fixup-Schritt nicht.

Danach:

```powershell
# Minecraft-Änderungen im Sinopia-Arbeitsbereich zu Patches zurückführen
.\gradlew.bat rebuildSinopiaPatches

# Erst nach erfolgreichem Rebuild: Patches in die versionierte Basis übernehmen
.\gradlew.bat captureSinopiaPatches
```

Der Capture-Schritt exportiert ausschließlich `paper-server/patches/` und
`build-data/`. Normale Java-Dateien aus einem falschen Arbeitsbereich werden
nicht versehentlich übernommen. Generierter Minecraft-Quellcode wird niemals
exportiert. Ein Capture schreibt einen lokalen Kontroll-Commit nur im
generierten Arbeitsbereich, nicht in Tesseras Hauptrepository.

Wenn sich `sinopia/` seit der Erstellung bzw. letzten Übernahme verändert hat,
wird die Übernahme verweigert. Die Änderungen müssen dann bewusst zusammengeführt
werden. Ein Arbeitsbereich auf einer anderen Basis wird ebenfalls nicht
automatisch zurückgesetzt: nach Sicherung/Übernahme beiseitelegen und neu
vorbereiten. Mehrere gleichzeitig laufende Snapshot-/Capture-Vorgänge werden
durch eine lokale Sperre abgefangen.

Patch-Anwendung und Rebuild für denselben Sinopia-Arbeitsbereich nicht aus
mehreren separaten Gradle-Prozessen gleichzeitig starten. Bei gemeinsamem
Aufruf in einem Prozess erzwingen die Root-Aufgaben die Reihenfolge
Anwendung -> Fixup (falls gewählt) -> Rebuild -> Übernahme.

## Tessera-Patches bearbeiten

Die vorhandenen API-, Implementierungs- und Minecraft-Patchserien bleiben
getrennt bestehen. Ihre bisherigen Rebuild-Aufgaben gelten weiterhin; Sinopia-
Minecraft-Änderungen gehören nicht in einen Tessera-Gameplay-Patch.

Bei Basisänderungen erst die Patch-Anwendbarkeit prüfen und dann die betroffenen
Tessera-Patches aktualisieren. Keine manuelle Bearbeitung der generierten
`build.gradle.kts` als alleinige Änderung: Änderungen dort müssen weiterhin in
die entsprechenden `.patch`-Dateien zurückgeführt werden.

## Künftige Upstream-Updates

1. Gewünschten Paper-Commit ausdrücklich auswählen und den Unterschied zum
   Herkunftsstand in `sinopia/BASELINE.md` prüfen.
2. Änderungen in `sinopia/` mit den dortigen Sinopia-Anpassungen zusammenführen;
   nicht den gesamten Ordner blind ersetzen.
3. Herkunftsangaben sowie Minecraft-/Mache-/Werkzeugversionen konsistent pflegen.
4. Sinopia-Patches und dann sämtliche Tessera-Patchschichten prüfen.
5. Vollständig bauen und Gameplay-, Regions- und Datenmigrationsregressionen
   auf separaten Testwelten ausführen.

Papers historische `oldPaperCommit`-Updatehilfe braucht eine passende Git-
Historie. Der lokale Snapshot besitzt diese Upstream-Historie nicht. Die
entsprechende Updatehilfe ist deshalb in der importierten Basis deaktiviert.
Das ist ausdrücklich KEINE Erklärung, dass die Minecraft-Portierung fertig ist.

## 26.3-Status

Die aktuelle Sinopia-Basis stammt aus Paper 26.3-rc-2. Die 34 ursprünglich
zurückgestellten Feature-Patches wurden portiert und aktiviert, einschließlich
Moonrise, Starlight und DataConverter. Sinopia-Patch 0035 ergänzt die Vanilla-
Datenmigration für 26.3. Die Folia-/Tessera-Serie enthält jetzt 32 Minecraft-
und 23 Implementierungspatches; die jeweils letzten Patches sichern weitere
26.3-Anpassungen bzw. deren Regions-Regressionsprüfungen.

Prüfergebnisse und verbleibende Laufzeitabnahme:
[PORTIERUNG-26.3-rc-2.md](PORTIERUNG-26.3-rc-2.md). Diese Vorabversion ist nicht
allein durch einen erfolgreichen Build als produktionsreif einzustufen.

Siehe auch `MIGRATION-26.3-rc-2.md` für den historischen Migrationsversuch.
Der vollständige 26.2-Vergleichsbuild der neuen Infrastruktur war erfolgreich;
Details und Testzahlen stehen in [der Integrationsprüfung](SINOPIA-INTEGRATION-VALIDATION.md).
