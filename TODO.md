# TODO – AntennaPod Fork

Stand 2026-11-11. Offen = `[ ]`, erledigt = `[x]` (mit Release), abgelehnt/verworfen = `[-]`.
Entscheidungen des Nutzers stehen kursiv dabei. Hintergrund siehe `CLAUDE.md`.

## Offen

1. [ ] **Rückmeldung: Obtainium-Update mit neuem versionCode.** Hat Obtainium v25 ohne
   Umweg installiert? Kontrolle in der App: Einstellungen → Über → `-fork.25`. Falls
   nicht, Ursache weiter suchen (Fehlermeldung in Obtainium).
2. [ ] **Rückmeldung: Tagessperre des Sperrbildschirm-Widgets (v25).** Die
   Fehl-Starts kamen laut Protokoll vom Good-Lock-Widget, und zwar *tagsüber*. Mit v25
   prüft der Dienst den Zeitraum beim Tippen. Zu bestätigen: Im Protokoll „Wer hat Play
   gedrückt?“ tauchen tagsüber nur noch „…ignoriert“-Einträge auf. Falls nachts doch
   Taschen-Drücke stören: Idee „Doppeltippen zum Auslösen“ (erster Tipp markiert, zweiter
   innerhalb von 3 s löst aus). *Nicht gebaut, da der Nutzer „tagsüber“ antwortete.*
3. [ ] **Kopfhörer-Play nach Ablauf des Sleep-Timers** funktioniert im Hintergrund nicht,
   bei offener App schon (gemeldet um v13–v16). Diagnosefragen an den Nutzer sind
   unbeantwortet:
   - Akku-Einstellung „Nicht eingeschränkt“?
   - Bluetooth oder Kabel?
   - Ist die Benachrichtigung nach dem Timer noch da?
   - Tritt es mit dem originalen AntennaPod auch auf?

   Nachfragen, ob es noch auftritt.
4. [ ] **Rückmeldung: Werbesprung in anderen Folgen.** Erreicht das gemerkte Outro auch
   dort Score ≥ 0,8? (Manchmal ist der Jingle anders abgemischt.)
5. [ ] **Rückmeldung: Widget-Button „Werbung überspringen“ auf dem Good-Lock-Sperrbildschirm.**
   Erscheint der Such-Dialog ohne Entsperren (`setShowWhenLocked`)? Ungetestet.
6. [ ] **Upstream-`checks.yml` auf Fork-PRs.** Beim ersten PR (Übergabe-Doku) beobachten,
   ob Lint/Unit/Emulator-Jobs mit dem Fork-Code grün sind. Bei Rot: dokumentieren und
   mit dem Nutzer klären, ob die Jobs für den Fork angepasst oder ignoriert werden.

## Erledigt

- [x] Eigene App-ID `de.danoeh.antennapod.fork`, signierte Releases, Obtainium (v1 ff.)
- [x] Button „10 min zurück“ im Player, Widget und in der Mediensteuerung, Icon „Replay 10“
- [x] „10 min zurück“ startet auch die Wiedergabe und lädt bei Bedarf die letzte Episode
- [x] Widget-Buttons funktionieren auch bei pausierter Wiedergabe (foreground-Service-Intents)
- [x] Widget-Option „Nur Buttons“ (ohne Cover/Titel/Fortschritt); zwei Widgets mit
      unterschiedlichen Einstellungen möglich
- [x] GPL-konformer Fork-Hinweis statt upstreams Warnung; Release-Notizen mit Quellcode-Link
- [x] Upstream-Releases montags automatisch übernehmen (`fork-sync-upstream.yml`)
- [x] Widget-Option „Nur im Schlummer-Zeitraum bedienbar“ (v16+), seit v25 zusätzlich
      beim Tippen geprüft
- [x] Sicherung des v16-Stands als Branch `stabil-2026-10-21`
- [x] Werbung überspringen per gemerktem Werbe-Outro (v20); *Schwelle 0,8, pro Podcast,
      nur heruntergeladen, 10 min voraus*
- [x] Player-Button „Nächste Episode“ durch „Werbung überspringen“ ersetzt (v21)
- [x] Widget: Button „Werbung überspringen“, Option „Play/Pause ausblenden“, kein
      Abdunkeln mehr (v22)
- [x] Widget-Option „Buttons untereinander“ (vertikal) (v23), *vom Nutzer bestätigt*
- [x] Protokoll „Wer hat Play gedrückt?“ (v24), *Nutzer hat damit das Widget als Quelle
      der Fehl-Starts gefunden*
- [x] `versionCode` pro Build hochzählen (ab v25)
- [x] Release-Tag zeigt auf den gebauten Commit (`--target`)
- [x] Beta-Kanal gebaut, getestet, danach *auf Wunsch* Branch und Releases gelöscht
      (Mechanismus bleibt im Workflow)

## Abgelehnt / verworfen

- [-] Eigener Sleep-Timer beim „10 min zurück“-Button, Widget-Button „Sleep-Timer“, Option
      „Erneutes Play startet den Timer neu“. *Wieder entfernt, upstreams
      Auto-Schlummerfunktion reicht.*
- [-] Widget-Elemente außerhalb des Schlummer-Zeitraums durchsichtig/ausblenden bzw.
      abdunkeln. *Führte zu „widget with no data“ in Good Lock bzw. wirkte nicht.*
- [-] Schwelle 0,45 bzw. 0,85 für den Werbesprung. *Nutzer hat 0,8 gewählt.*
- [-] Bluetooth/Kopfhörer beim Play automatisch verbinden oder Bluetooth-Dialog zeigen.
      *„Wir lassen alles so wie es ist“; Android erlaubt Apps das ohnehin nicht. Empfehlung
      an den Nutzer: Samsung „Modi und Routinen“ mit Zeit-Routine.*
