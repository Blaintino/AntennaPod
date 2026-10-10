# AntennaPod Fork

Persönlicher Fork von [AntennaPod/AntennaPod](https://github.com/AntennaPod/AntennaPod)
mit ein paar kleinen Änderungen. Gebaut wird eine signierte APK per GitHub
Actions, installiert und aktualisiert über GitHub Releases + [Obtainium](https://obtainium.imranr.dev/)
(gleiches Prinzip wie `android-vorlage` im Monorepo `Claude_Code_Projekte`).

Branch-Modell: `main` = upstream `develop` + die Fork-Änderungen. Upstream-Stände
werden per Merge übernommen (nie Rebase), damit die Fork-Commits sichtbar bleiben.

## Änderungen gegenüber upstream

1. **„10 min zurück + Schlummerfunktion“-Button im Player** (links neben dem
   Geschwindigkeits-Button): springt 10 Minuten zurück, startet die Wiedergabe
   (falls pausiert) und gleichzeitig eine 10-Minuten-Schlummerfunktion (immer als Zeit-Timer, unabhängig davon, ob
   im Schlummer-Dialog „Episoden“ eingestellt ist). Ein bereits laufender Timer
   wird dabei durch den neuen 10-Minuten-Timer ersetzt.
2. **Widget: zwei neue optionale Buttons** (beim Hinzufügen des Widgets in der
   Widget-Konfiguration ankreuzbar):
   - **10 min zurück + Schlummerfunktion** – wie der Player-Button oben.
   - **Schlummerfunktion** – schaltet um: Läuft kein Timer, startet er die
     Wiedergabe und einen Timer mit der zuletzt im Schlummer-Dialog gewählten
     Dauer; läuft einer, wird er nur ausgeschaltet (Wiedergabe bleibt, wie sie ist).
   Ist gerade keine Episode geladen (App länger nicht benutzt), laden beide
   Buttons zuerst die zuletzt gehörte Episode – wie der normale Play-Button.
   Außerdem die Option **„Nur Buttons“**: blendet Cover, Titel und Fortschritt
   aus, das Widget zeigt nur noch Play/Pause und die angekreuzten Buttons. Die
   Einstellungen gelten pro Widget – das Widget kann also z. B. einmal normal
   und einmal als reine Button-Leiste auf dem Homescreen liegen.
3. **Mediensteuerung (Benachrichtigung + Sperrbildschirm):** zusätzlicher
   Button „10 min zurück + Schlummerfunktion“, auswählbar unter
   Einstellungen → Benutzeroberfläche → Benachrichtigungs-Buttons (dort sind
   wie upstream genau zwei Zusatz-Buttons wählbar, Zurück-/Vorspulen sind immer
   dabei). Icon überall (Player, Widget, Benachrichtigung): Material „Replay 10“.
4. **Eigene App-ID `de.danoeh.antennapod.fork`** und App-Name „AntennaPod Fork“,
   damit der Fork **neben** dem originalen AntennaPod installiert werden kann
   (eigene Daten, eigener Provider). Abos/Fortschritt übernimmst du über
   Original → Einstellungen → Import/Export → Datenbank exportieren, dann im
   Fork Datenbank importieren.

Technisch: zwei neue Media3-Session-Befehle (`long_rewind_sleep`,
`toggle_sleep_timer`) in `MediaLibrarySessionCallback`/`Media3PlaybackService`.
Die Widget-Buttons lösen sie über den nicht exportierten
`playback/service/.../SessionCommandReceiver` aus (die normalen Widget-Buttons
gehen über Media-Button-Intents, die keine eigenen Befehle transportieren
können). Neue Texte liegen in eigenen `ui/i18n/.../strings_fork.xml`
(Englisch + Deutsch), damit Übersetzungs-Updates von upstream beim Merge nicht
mit den Fork-Texten kollidieren.

## Einmalige Einrichtung

1. **Actions aktivieren:** Im Fork-Repo auf GitHub → Tab *Actions* → „I understand
   my workflows, go ahead and enable them“. (GitHub schaltet Actions in Forks
   standardmäßig ab.)
2. **Signatur-Secrets hinterlegen** (*Settings → Secrets and variables → Actions*),
   gleiche Namen wie bei `android-vorlage` – du kannst denselben Keystore nehmen:
   `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
   `ANDROID_KEY_PASSWORD`. Ohne Secrets baut der Workflow nur eine Debug-APK
   (als Artefakt, kein Release), über die keine Updates möglich sind.
3. **Obtainium:** App hinzufügen mit der URL dieses Repos; unter
   „Filter release titles by regular expression“ `^antennapod-fork-v` eintragen.

## Upstream-Updates übernehmen

Läuft **automatisch jeden Montag**: Der Workflow „Upstream übernehmen“ prüft,
ob es ein neues offizielles AntennaPod-Release gibt (keine Pre-Releases). Wenn
ja und es noch nicht in `main` steckt, merged er es in `main` und stößt danach
den APK-Build an – Obtainium meldet dann das Update. Gibt es nichts Neues,
passiert nichts.

Manuell: *Actions → „Upstream übernehmen“ → Run workflow* (Standard `latest` =
neuestes Release, alternativ ein bestimmter Tag wie `3.13.0` oder `develop` für
den ungetesteten Entwicklungsstand).

Der Workflow bricht ohne Push ab (roter Lauf, GitHub schickt eine E-Mail), wenn

- es einen Merge-Konflikt gibt, oder
- upstream Dateien unter `.github/workflows/` geändert hat (das GitHub-Token
  von Actions darf keine Workflow-Dateien pushen).

In beiden Fällen den Merge lokal oder in einer Claude-Code-Session machen:

```bash
git remote add upstream https://github.com/AntennaPod/AntennaPod.git  # einmalig
git fetch --tags upstream
git checkout main && git merge 3.13.0   # bzw. der Tag aus dem fehlgeschlagenen Lauf
git push origin main
```

Hinweis: GitHub pausiert zeitgesteuerte Workflows in öffentlichen Repos nach
60 Tagen ohne Aktivität im Repo (mit E-Mail-Hinweis). Dann unter *Actions →
„Upstream übernehmen“* auf „Enable workflow“ klicken.

## Lokal bauen

JDK 21 + Android SDK (`local.properties` mit `sdk.dir=…`), dann
`./gradlew :app:assembleFreeDebug`. Die APK liegt unter
`app/build/outputs/apk/free/debug/`.
