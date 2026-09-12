# Sinopia-Integration – Prüfstand vom 12.09.2026

> Diese Datei dokumentiert den Infrastruktur-Umbau vor der anschließenden
> Minecraft-Portierung. Die damaligen 26.3-Blocker sind kein aktueller Status.
> Den neueren Stand beschreibt [PORTIERUNG-26.3-rc-2.md](PORTIERUNG-26.3-rc-2.md).

## Umfang

Tesseras Paper-abgeleitete Basis ist als `sinopia/` in dasselbe Repository
integriert. Der Import stammt aus Paper-Commit
`38b0bfeb67855206ede9cb1df4f3354c4611c4c2` für Minecraft `26.3-rc-2`.
Tessera bleibt das finale Serverprodukt. Es wurde kein zweites Remote angelegt,
kein Submodule eingeführt und kein Root-Commit oder Push ausgeführt.

Die bestehenden Folia-/Tessera-Patchschichten bleiben erhalten. Angepasst wurden
die Build-Einbindung und die Kontextzeilen der vorhandenen Branding-Patches.
Der Umbau implementiert keine neue Gameplay-Funktion und ersetzt keine noch
ausstehenden Minecraft-Portierungen.

## Bereits verifiziert

| Prüfung | Ergebnis |
| --- | --- |
| Build-Unterstützung (`-p buildSrc check`) | 22 echte Git-Integrationstests erfolgreich |
| Lokaler Sinopia-Snapshot und Paperweight-Checkout | Erfolgreich; kein Paper-GitHub-Checkout erforderlich |
| API-/Build-/Checkstyle-Patchstufe | Erfolgreich, einschließlich aller 12 API-Feature-Patches |
| Tessera-API-Tests auf 26.3 | 529 Tests erfasst, 0 Fehler, 2 übersprungen |
| Implementierungs-Patchserie gegen Sinopia | Alle 22 Patches erfolgreich in isolierter Arbeitskopie angewendet |
| Sinopia-Minecraft-Patches | 929 Dateipatches und 3 Ressourcenpatches erfolgreich angewendet |
| Rebuild und Übernahme dieser Patches | Erfolgreich; Patchbestand nach dem Rundlauf unverändert |
| Kombinierter finaler Sinopia-Aufruf | Anwendung -> Rebuild -> Capture erfolgreich; 0 unbeabsichtigte Änderungen |
| Zentraler 26.2-Vergleichsbuild (`buildTessera`) | Vollständig erfolgreich, einschließlich Tests und ausführbarer Paperclip-JAR |
| Versionsquellen | Keine verschachtelten Git-Repositories oder generierten Minecraft-Quellen in `sinopia/` |

Die Git-Tests prüfen unter anderem deterministische Snapshots, den Erhalt
bearbeiteter Arbeitskopien, die Übernahme neuer/geänderter/gelöschter Patches,
einen wiederverwendbaren Arbeitsbereich nach der Übernahme und die Ablehnung
konkurrierender Basisänderungen bzw. falscher Patchschichten.

## Vergleich mit 26.2 und aktueller Server-Build

Der 26.2-Vergleich liegt ausschließlich unter `build/monorepo-26.2-check/`.
Er basiert auf Tesseras Root-Stand `0b66675` und Paper-Commit
`bb09b431d24f586da72ee7c4aa2e8cff2b32a478`, ergänzt um dieselbe lokale
Sinopia-Build-Einbindung und Branding-Kontexte. Er verändert den aktuellen
26.3-Versionsstand nicht.

Ein erster Lauf traf auf das Windows-Dateipfadlimit. Der erneute Lauf verwendet
`core.longpaths=true` ausschließlich als Prozesseinstellung; die globale
Git-Konfiguration bleibt unverändert.

Im 26.2-Vergleich sind sämtliche Patchschichten erfolgreich angewendet,
einschließlich der 31 Minecraft-Feature-Patches und der 22
Implementierungspatches. `buildTessera` hat anschließend die Kompilierung,
Standardtests, Prüfaufgaben und JAR-Erstellung erfolgreich abgeschlossen.
Die vorhandene Konfiguration schließt den Test-Tag `Slow` weiterhin aus.

| 26.2-Testmodul | Erfasste Tests | Übersprungen | Fehler/Fehlschläge |
| --- | ---: | ---: | ---: |
| API | 529 | 2 | 0 |
| Server | 9.276 | 23 | 0 |
| Checkstyle-Prüfmodul | 3 | 0 | 0 |
| Gesamt | 9.808 | 25 | 0 |

Testartefakt (kein Wechsel des aktuellen 26.3-Branches):
`build/monorepo-26.2-check/build/libs/tessera-server-26.2.build.018-stable.jar`
(65.700.721 Bytes).

SHA-256: `0546bd107a65888dfcdb847c7fd25771a6cda2f9397bde8cde1d2155293507b7`.
Der Launcher enthält `Main-Class: io.papermc.paperclip.Main`; die erzeugte
Server-JAR enthält `Brand-Name: Tessera` und `Brand-Id: mosaikdev:tessera`.
Es wurden keine manuellen Gameplay-Tests mit verbundenen Clients durchgeführt.

Der zentrale 26.3-Build erreicht die Folia-Minecraft-Feature-Patches, scheitert
aber beim ersten Patch `Region Threading Base` mit 62 Konfliktdateien. Darunter
sind 13 Modify/Delete-Konflikte durch noch fehlende Upstream-Features. Es wurde
keine 26.3-Server-JAR gebaut. Die vom Test gestartete Git-Patch-Anwendung wurde
nach Sicherung der Konfliktliste abgebrochen; die generierten Minecraft-Quellen
sind wieder konfliktfrei, aber ausdrücklich noch nicht vollständig portiert.

## Bekannte Versionsgrenze

Im importierten 26.3-Paper-Stand liegen weiterhin 34 Feature-Patches unter
`features_unapplied`, darunter Moonrise. Sie wurden weder gelöscht noch durch
Platzhalter ersetzt. Der Monorepo-Umbau ist keine Aussage, dass dieser
Minecraft-/Folia-Port vollständig oder produktionsreif ist.

Die Anleitung für künftige Änderungen und Builds steht in
[SINOPIA-WORKFLOW.md](SINOPIA-WORKFLOW.md).

## Lokale Nachweise

- `build/sinopia-build-support-test-final.log`
- `build/sinopia-prepare-api-retry.log`
- `build/sinopia-server-patch-check.log`
- `build/sinopia-26.3-base-apply.log`
- `build/sinopia-26.3-roundtrip.log`
- `build/sinopia-26.3-capture-final.log`
- `build/sinopia-26.2-build-longpaths.log`
- `build/sinopia-26.2-build-final.log`
- `build/sinopia-26.3-tessera-build.log`
- `build/sinopia-26.3-conflicts.txt`
- `build/sinopia-26.3-api-test.log`
- `build/sinopia-final-roundtrip-ordered.log`

Diese generierten Nachweise werden nicht als Quellcode eingecheckt. Der
unveränderte Rundlauf-Arbeitsbereich bleibt zur Nachprüfung unter
`build/sinopia-workspace-verified-roundtrip/` erhalten. Der zuvor vorhandene
Root-Diff wurde vor dem Umbau unter `build/monorepo-before-implementation/`
gesichert; bestehende lokale Migrationsänderungen wurden beibehalten.
