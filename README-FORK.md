# AntennaPod Fork

Persönlicher, **inoffizieller** Fork von [AntennaPod/AntennaPod](https://github.com/AntennaPod/AntennaPod)
mit ein paar kleinen Änderungen. Kein offizielles AntennaPod-Release: Fehler in
diesem Fork bitte nicht beim AntennaPod-Projekt melden. Gebaut wird eine signierte APK per GitHub
Actions, installiert und aktualisiert über GitHub Releases + [Obtainium](https://obtainium.imranr.dev/)
(gleiches Prinzip wie `android-vorlage` im Monorepo `Claude_Code_Projekte`).

Branch-Modell: `main` = upstream `develop` + die Fork-Änderungen. Upstream-Stände
werden per Merge übernommen (nie Rebase), damit die Fork-Commits sichtbar bleiben.

## Änderungen gegenüber upstream

1. **„10 min zurück + Play“-Button** – springt 10 Minuten zurück und startet
   die Wiedergabe (falls pausiert). Ist gerade keine Episode geladen (App
   länger nicht benutzt), wird zuerst die zuletzt gehörte Episode geladen – wie
   beim normalen Play-Button. Einen eigenen Sleep-Timer startet der Button
   bewusst **nicht**: das übernimmt AntennaPods eigene Option „Schlummerfunktion
   automatisch aktivieren, wenn auf Play gedrückt wird“ (Schlummer-Dialog,
   Zeitraum einstellbar), die bei diesem Play genauso greift wie bei jedem
   anderen. Den Button gibt es an drei Stellen:
   - im Player links neben dem Geschwindigkeits-Button,
   - im Widget (optional, in der Widget-Konfiguration ankreuzbar),
   - in der Mediensteuerung (Benachrichtigung + Sperrbildschirm), auswählbar
     unter Einstellungen → Benutzeroberfläche → Benachrichtigungs-Buttons (dort
     sind wie upstream genau zwei Zusatz-Buttons wählbar).
   Icon überall: Material „Replay 10“.
2. **Widget-Option „Nur Buttons“** – blendet Cover, Titel und Fortschritt aus,
   das Widget zeigt nur noch Play/Pause und die angekreuzten Buttons. Die
   Option **„Werbung überspringen“** (Button wie im Player, siehe 3.; öffnet
   kurz den Such-Dialog, auch auf dem Sperrbildschirm) und
   **„Play/Pause-Button ausblenden“**; mit **„Buttons untereinander
   anordnen“** stehen die Buttons vertikal (eigenes Layout
   `player_widget_vertical.xml`): geht nur, wenn mindestens ein
   anderer Button gewählt ist (sonst wäre das Widget leer). Die
   Einstellungen gelten pro Widget – das Widget kann also z. B. einmal normal
   und einmal als reine Button-Leiste auf dem Homescreen liegen.
   Außerdem die Widget-Option **„Nur im Schlummer-Zeitraum bedienbar“**: nutzt
   den Zeitraum der Schlummer-Automatik (Schlummer-Dialog, z. B. 22–6 Uhr).
   Außerhalb davon öffnet ein Tippen auf die Buttons dieses Widgets nur die
   App, statt die Wiedergabe zu steuern (die Buttons sehen dabei unverändert
   aus – Abdunkeln/Ausblenden funktionierte auf dem Sperrbildschirm nicht) – gedacht für ein
   Widget auf dem Sperrbildschirm (Good Lock), das tagsüber in der Hosentasche
   nichts auslösen soll. Das Widget wird zu Beginn und Ende des Zeitraums
   automatisch aktualisiert (WorkManager).
3. **Werbung überspringen** – im
   Player-Menü „Werbe-Outro merken“ direkt nach dem Ende eines Werbe-Outros
   antippen, Länge (0,3–5 s) und Ende (±1,5 s) so einstellen, dass beim
   Probehören nur die Erkennungsmelodie zu hören ist, speichern (eine Vorlage
   pro Podcast, als PCM unter `files/outro/`). Der Button rechts neben „Vorspulen“
   (ersetzt upstreams „Nächste Episode“-Button, Icon: Bogenpfeil) sucht dann ab der aktuellen Position bis
   zu 10 Minuten voraus nach dieser Melodie und springt direkt dahinter. Nur für
   heruntergeladene Episoden. Technisch: Dekodieren per `MediaCodec`, Mono
   8 kHz, 24 logarithmische Frequenzbänder pro 16-ms-Schritt, Pearson-Korrelation
   gegen die Vorlage (lautstärkeunabhängig), Schwelle 0,8 (am echten Podcast kalibriert: richtiges Outro über 0,9, alles andere unter 0,8). Der Toast zeigt
   den Score zur Kalibrierung.
4. **Eigene App-ID `de.danoeh.antennapod.fork`** und App-Name „AntennaPod Fork“,
   damit der Fork **neben** dem originalen AntennaPod installiert werden kann
   (eigene Daten, eigener Provider). Abos/Fortschritt übernimmst du über
   Original → Einstellungen → Import/Export → Datenbank exportieren, dann im
   Fork Datenbank importieren.

Technisch: ein neuer Media3-Session-Befehl `long_rewind_play` in
`MediaLibrarySessionCallback`/`Media3PlaybackService`. Der Widget-Button löst ihn
über dasselbe Media-Button-Intent wie der Play-Button aus
(`PendingIntent.getForegroundService`, Extra `session_command`, eigener
Request-Code), abgefangen in `onMediaButtonEvent` von `Media3PlaybackService`.
Nur so darf der Dienst bei pausierter Wiedergabe aus dem Hintergrund wieder in
den Vordergrund wechseln. Neue Texte liegen in eigenen
`ui/i18n/.../strings_fork.xml` (Englisch + Deutsch), damit Übersetzungs-Updates
von upstream beim Merge nicht mit den Fork-Texten kollidieren.

Wieder entfernt (weil AntennaPods eingebaute Auto-Schlummerfunktion das schon
abdeckt): ein eigener 10-Minuten-Timer beim „10 min zurück“-Button, ein
Sleep-Timer-Umschalt-Button im Widget und die Option „Erneutes Abspielen
startet den Timer neu“.

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
4. **Beta (optional):** Jeder Push auf den Branch `beta` baut eine eigene App
   „AntennaPod Fork Beta“ (App-ID `de.danoeh.antennapod.fork.beta`, eigene Daten,
   läuft neben der stabilen App) und veröffentlicht sie als Release
   `antennapod-fork-beta-v<n>` (nie als „Latest“ markiert). In Obtainium als
   zweite App mit derselben Repo-URL hinzufügen, Filter `^antennapod-fork-beta-v`.
   Neue Funktionen kommen erst auf `beta` und nach dem Testen per Merge auf `main`.

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

## Lizenz

AntennaPod steht unter der **GNU General Public License v3.0** (siehe
[`LICENSE`](LICENSE)); das gilt unverändert auch für diesen Fork. Copyright am
ursprünglichen Code liegt bei den [AntennaPod-Mitwirkenden](CONTRIBUTORS.md).

Hinweis nach GPL-3.0 § 5 a: Dieses Werk wurde von Frederik Langner verändert,
erstmals am 2026-10-06; Art und Umfang der Änderungen stehen oben unter
„Änderungen gegenüber upstream“, die einzelnen Commits sind mit „Fork:“
markiert. Die Änderungen stehen ebenfalls unter GPL-3.0.

In der App selbst (Einstellungen, unten) steht statt der Warnung, die
AntennaPod bei fremden Paketnamen anzeigt, ein eigener Hinweis: inoffizielle,
veränderte Version, GPL-3.0 ohne Gewährleistung, kein Support durch das
AntennaPod-Team, Link auf dieses Repo (GPL-3.0 § 5 a/§ 5 d). Forum, „Mitwirken“
und „Fehler melden“ – die direkt zum AntennaPod-Team führen – bleiben
ausgeblendet; „Über“ (Lizenzen, Credits) und „Dokumentation“ bleiben sichtbar.

Zu jeder APK unter *Releases* verweist die Release-Beschreibung auf den genauen
Quellcode-Stand (Commit), aus dem sie gebaut wurde (GPL-3.0 § 6).
