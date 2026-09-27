# Build 010: finale native Nachweise

Die aktuellen Nachweise gehören zur ausführbaren JAR aus Quellcommit
`a767a0ab30a65ad7faa7f693a544506bd7c694c9`, Vertrag `1`.

- `build.json`: Build, Testzahlen, Patch-Wiederanwendung und Datei-Prüfsummen.
- `native-transaction.json`: vollständiger öffentlicher Restore mit zwei verbundenen Clients, ohne Reconnect.
- `native-races.json`: absichtlicher Disconnect, gesperrter Login, echter ACK-Timeout und Rollback.
- `native-recovery.json`: fünf kontrollierte Shutdown-/Neustartfälle mit Client-Ereignissen und erneuter Transaktion.

`historical-native-components.json` bleibt ausschließlich als historischer Nachweis
des früheren, unvollständigen Komponentenbuilds mit Vertrag `0` erhalten. Seine
Prüfsumme gehört **nicht** zur finalen JAR. Er ist kein Teil ihrer Abnahme.

Der vollständige MCC-/MVE-/TAB-/LuckPerms-Integrationslauf ist weiterhin separat
auszuführen; siehe [Restore-Status](../../mcc-player-restore-status.md).
