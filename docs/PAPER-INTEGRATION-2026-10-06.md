# Paper-Integration – Tessera 26.3-016-beta

Stand: 06.10.2026. Selektive Integration in Sinopia und Folia/Tessera, kein
vollständiger Upstream-Rebase. Minecraft/API 26.3, Java 25, Gradle 9.8.0,
Paperweight beta24 und Leafpile 1.2.4 bleiben erhalten.

## Übernommene Änderungen

Vergleich: `c27ca36bf1d9d705e260f1cfb866c97de1ac537d` bis
`4728a906edb501c3749dfcc437a419094a95a2c7`. Die ursprüngliche Importreferenz
`paperRef` bleibt Provenienz, nicht eine Behauptung eines vollständigen Rebase.

| Paper-Commit | Änderung und lokale Einordnung |
| --- | --- |
| [4728a90](https://github.com/PaperMC/Paper/commit/4728a906edb501c3749dfcc437a419094a95a2c7) | Bundle-Codecs markieren Obfuscation-Abhängigkeit; Netzwerk-Cache darf keine Shelf-Speicherdaten verfälschen. Bereits verlorene Inhalte werden nicht rekonstruiert. |
| [be053f7](https://github.com/PaperMC/Paper/commit/be053f7afd287d72093141ce9153623910a26a3c) | Concurrent CraftRegistry-Cache, atomare Wrapper-Erstellung und sichtbarer Holder-Lock. Keine allgemeine Async-Freigabe für Registry-Objekte. |
| [c7dd430](https://github.com/PaperMC/Paper/commit/c7dd4303da1171e69ce4dcb2b182fc8886e3f861) | Befehlswurzel vor asynchronem Aufbau kopieren. Antwort bleibt auf Folias Spieler-EntityScheduler. |
| [ddb45eb](https://github.com/PaperMC/Paper/commit/ddb45eb4956defa0de2b16d11cfc72094b7f09ff) | Hopper: nach erfolglosem Transfer den restaurierten Stack und die richtige Doppelcontainer-Hälfte markieren. Regionslokale Event-/Update-Flags erhalten. |
| [6e88e46](https://github.com/PaperMC/Paper/commit/6e88e469febfec696f118d2df1451fcad4a04a87) | Post-Effects-Pakete übernehmen immutable Kopie der Spieler-Effektliste für den späteren Netzwerk-Encoder. |
| [780818f](https://github.com/PaperMC/Paper/commit/780818ff420cf4eb126b7b7d1406f89b263a200e) | Eimer-Ergebnis nur nach erfolgreichem Platzieren. Tessera ersetzt zusätzlich den globalen Zwischenwert durch verschachtelungsfeste Aufruf-Scope-Isolation. |
| [a9d0382](https://github.com/PaperMC/Paper/commit/a9d038271a459bb16e232c2480ad548eebdc922c) | Erforderliche Plugin-Datapacks beim Start über IDs statt vorzeitig konvertierte Titel vergleichen. |
| [1dbde61](https://github.com/PaperMC/Paper/commit/1dbde6108fe7469c068f0e27659dacda053f2f57) | Testblock-Pakete vor Decode/Encode auf Game-Master-Zulässigkeit prüfen; Handlerprüfungen bleiben bestehen. |
| [977da0d](https://github.com/PaperMC/Paper/commit/977da0dbb537181520022d4a9b2093e4aed09216) | Deaktivierter Spam-Schwellwert gilt auch für kombinierte Increment-/Check-Abfrage. |
| [af5c198](https://github.com/PaperMC/Paper/commit/af5c198defd782684bbef535daadcf3dfc6d3aae) | Verbotene Adventure-Blockabbrüche lösen kein BlockBreakEvent mehr aus. |
| [54024d3](https://github.com/PaperMC/Paper/commit/54024d30670995c589d8e8213bd54905340b90fc) | MC-311924: BlockAttachedEntity-/ArmorStand-Tod, DamageSource und Sculk-Zuordnung korrigieren. |
| [60c8e56](https://github.com/PaperMC/Paper/commit/60c8e563c21eb5642bea28056718a614a94abeeb) | MC-311788: ItemFrame-Positionen präzise/absolut senden. |
| [371c96a](https://github.com/PaperMC/Paper/commit/371c96aa6898feab5cf155b5d4fba3c6e2b2d8a7) | Wiederverwendeter Chatrenderer hält keine alte Nachricht; gemeinsamer Standardrenderer verhindert unnötige Formatumschaltung. |
| [ff3655a](https://github.com/PaperMC/Paper/commit/ff3655a7842b8830c0bedd5ef4e35522aa9a1914), [1d254ec](https://github.com/PaperMC/Paper/commit/1d254ec4938896c3332cf45600bb3eacef37fd30) | API-Spawns nehmen natürliche Passagiere mit; bereits gültige Passagiere verhindern den Wurzel-Spawn nicht. Als zusammengehörige Korrektur übernommen. |
| [1c92a6c](https://github.com/PaperMC/Paper/commit/1c92a6c3a42deda2fbefdec1a0d90d696313ff3b) | Expliziter DragonBattle-API-Respawn mit leerer Kristallliste; normale Vanilla-Vierkristallprüfung bleibt erhalten. |
| [288d43e](https://github.com/PaperMC/Paper/commit/288d43e1081627a89d7b01f6ad49d1461e04bd38) | BlockType#getInstrument ergänzt, Adapter und Entwicklerreferenz angepasst. |
| [6bc56ab](https://github.com/PaperMC/Paper/commit/6bc56abc92078e5064a945859401d4737262f02e) | BasicCommand#execute deklariert CommandSyntaxException; keine Änderung des JVM-Descriptors. Direkte Java-Aufrufer müssen beim Neukompilieren die Exception behandeln. |
| [2de3a93](https://github.com/PaperMC/Paper/commit/2de3a930cca7807143a95254989dc5962ef544a5) | Block-Snapshot-Auswahl als expliziter Parameter durch alle Factory-/Konstruktorpfade. Vorher bereits Folia-ThreadLocal-gesichert; kein neu belegter globaler Race im bisherigen Tessera. |
| [8832a8d](https://github.com/PaperMC/Paper/commit/8832a8dd7bfb8049ed7fcdfe19ca20862920a564) | WorldConfiguration chunks.min-chunk-unload-count=50 und min-chunk-unload-fraction=0.05 anbinden. Standardverhalten und regionslokale Unload-/Pregen-Tickets erhalten. |
| [1a7b626](https://github.com/PaperMC/Paper/commit/1a7b626576d41a6ddb0e3610ec0a1e3e59352093) | JLine-FFM 4.4.6 für Java 25, JNI-Backend entfernt; keine Gradle-/Minecraft-Migration. |

## Bewusst nicht doppelt übernommen

`65c408a`: Folia prüft bereits `waitingForSwitchToConfig` vor dem Entfernen
eines Spielers. Vier Paper-spezifische CI-/CODEOWNERS-Commits (`abfdaed`,
`769bc8c`, `021713a`, `3d71259`) werden nicht auf Tesseras eigenen Build-/Release-
Workflow übertragen. Tesseras Buildzeit- und Branding-Metadaten bleiben erhalten.

## Patch- und API-Zuordnung

Sinopia-Feature 0048 enthält die allgemeinen Minecraft-Korrekturen. Normale
Sinopia-API-/Adapterquellen sind direkt versioniert. Folias alter Snapshot-
ThreadLocal-Umbau in Server-Patch 0001 entfällt. Bestehende Regions-, Restore-,
Portal- und Pregeneration-Funktionen werden nicht durch eine Paper-Hauptthread-
Implementierung ersetzt. Minecraft-Patch 0055 erhält den regionslokalen
Schadenszeitstempel für Rüstungsständer und prüft bei Sculk den tatsächlichen
Spielerbesitzer. Server-Patch 0044 validiert Passagiergruppen vor dem Spawn und
Arena-/Kristallbesitzer vor dem API-Drachen-Respawn. Fehlgeschlagener Respawn
hinterlässt keine Freigabe für eine leere Kristallliste.
MCC, MVE und Horizons werden nicht bearbeitet.

Die geerbten API-Verträge sind in [Befehle](api/commands.md),
[Kompatibilität](api/compatibility.md) und
[Entities](api/entity-and-tree-compatibility.md) aktualisiert.

## Prüfnachweise

Die tatsächlichen Build-, Server- und Clientergebnisse sowie Artefakt-Prüfsumme
stehen im [Buildbericht 016](BUILD-26.3-016.md).
