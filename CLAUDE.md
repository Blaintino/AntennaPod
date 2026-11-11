@AGENTS.md

# Fork-Hinweise (Blaintino/AntennaPod) – haben Vorrang vor AGENTS.md

`AGENTS.md` oben ist die unveränderte Datei von upstream (AntennaPod). Sie gilt für
die Code-Arbeit weiter (minimaler Diff, keine Kommentare in neuem Code, Imports statt
voller Paketnamen). **Wo dieser Abschnitt etwas anderes sagt, gilt dieser Abschnitt.**
Das betrifft vor allem Texte, Build-Befehle, Branches, PRs und Issues (siehe unten).
Für die Nutzersicht der Änderungen siehe `README-FORK.md`, für offene Punkte `TODO.md`.

Doku, Commit-Messages und Gespräche mit dem Nutzer (Frederik Langner) auf **Deutsch**.

## Was die App ist

- Persönlicher, inoffizieller Fork von AntennaPod für das Samsung Galaxy S26 Ultra des
  Nutzers. Kein Play Store.
- **App-ID** `de.danoeh.antennapod.fork`, App-Name „AntennaPod Fork“. Sie läuft neben dem
  originalen AntennaPod und hat eigene Daten und einen eigenen Provider
  (`de.danoeh.antennapod.fork.provider`).
- Debug-Builds: App-ID `de.danoeh.antennapod.fork.debug`, Name „AntennaPod Fork Debug“.
  Debug-APKs aus einer Cloud-Session sind mit dem Debug-Key des Containers signiert. Sie
  lassen sich nur neu installieren, nicht als Update über eine andere Debug-APK.
- **Installation:** GitHub Releases + Obtainium. Repo-URL `https://github.com/Blaintino/AntennaPod`,
  Filter „Filter release titles by regular expression“ `^antennapod-fork-v`.
- **Release-Präfix:** `antennapod-fork-v<Build-Nr.>` (Tag = Titel). Die APK heißt
  `AntennaPod-Fork-<n>.apk`.
- **Beta-Kanal** (Mechanismus vorhanden, derzeit ungenutzt): Push auf Branch `beta` baut
  App-ID `de.danoeh.antennapod.fork.beta` („AntennaPod Fork Beta“) als Release
  `antennapod-fork-beta-v<n>`, nie als „Latest“ markiert. Obtainium-Filter
  `^antennapod-fork-beta-v`. Der Nutzer hat den Beta-Branch und die Beta-Releases nach dem
  ersten Einsatz löschen lassen. Nur auf Wunsch wieder anlegen.
- **Versionen:** `versionName` = upstream (z. B. `3.12.3`) + `-fork.<Build-Nr.>`.
  `versionCode` = upstream-Wert + Build-Nr. (seit v25, siehe Fallen). Die Build-Nr. ist
  `github.run_number` von `fork-release.yml` (Env `FORK_BUILD_NUMBER`).
- Lizenz GPL-3.0. Der GPL-Hinweis (§ 5 a/§ 6) steht in `README-FORK.md` und in der App
  unter Einstellungen (eigener Fork-Hinweis statt upstreams Paketnamen-Warnung).

## Aufbau des Codes (nur Fork-Teile)

Alle Fork-Commits beginnen mit „Fork:“. Basis ist upstream `develop` bei `9c7ffa1`.
Den Gesamtumfang zeigt `git diff 9c7ffa1 main --stat`.

- **Wiedergabedienst** `playback/service/.../Media3PlaybackService.java`:
  - Media3-Session-Befehl `long_rewind_play` (10 min zurück + Play).
  - In `onMediaButtonEvent`:
    - Widget-Session-Befehle über das Extra `session_command`.
    - Prüfung des Schlummer-Zeitraums für Widgets mit Extra `sleep_window_only`.
    - Herkunft des Drucks merken.
  - `play()`/`setPlayWhenReady()` schreiben ins Play-Protokoll `PlayStartLog.java`
    (SharedPreferences `PlayStartLog`, die letzten 40 Einträge).
  - `internal/MediaLibrarySessionCallback.java`: Button in der Mediensteuerung.
- **Intents für Widget und Buttons** `ui/app-start-intent/.../MediaButtonStarter.java`:
  - Media-Button-PendingIntents (`getForegroundService` für Play/Pause und `long_rewind_play`).
  - Für `sleep_window_only` eigene Request-Codes: Basis 3000 + Command bzw. 1002.
  - `SkipAdOutroActivityStarter.java` startet die Werbesprung-Activity vom Widget aus.
- **Widget** `ui/widget/`:
  - `WidgetUpdater` erzeugt RemoteViews **pro Widget-ID** (`createViews`).
  - Layouts: `player_widget.xml`, `player_widget_vertical.xml` (vertikale Variante).
  - Konfiguration in `WidgetConfigActivity`, Pref-Keys in `PlayerWidget`:
    - `widget_buttons_only`, `widget_long_rewind`, `widget_skip_ad`, `widget_hide_play`,
      `widget_vertical`, `widget_sleep_window_only`.
  - `WidgetUpdaterWorker.enqueueAtWindowBoundary` zeichnet das Widget an den Grenzen des
    Schlummer-Zeitraums neu.
- **Werbung überspringen** `app/src/main/java/de/danoeh/antennapod/ui/screen/playback/outro/`:
  - `OutroAudioReader`: MediaExtractor + MediaCodec → Mono 8 kHz.
  - `OutroFeatureExtractor`: 24 log-Bänder 150–3800 Hz, Frame 256, Hop 128.
  - `OutroMatcher`: Pearson-Korrelation, `DEFAULT_THRESHOLD = 0.8f`.
  - `OutroStore`: eine Vorlage pro Feed als 16-bit-PCM unter `files/outro/feed_<feedId>.pcm`.
  - `OutroSkipper`: Dialog „Werbe-Outro merken“ und Suche bis 10 min voraus mit Toast samt Score.
  - `SkipAdOutroActivity`: durchsichtig, auch über dem Sperrbildschirm, für den Widget-Button.
  - Test: `app/src/test/.../outro/OutroMatcherTest.java`.
- **Player-UI** `app/.../screen/playback/audio/AudioPlayerFragment.java` + `res/layout/audioplayer_fragment.xml`:
  - Button „10 min zurück“ links neben Geschwindigkeit.
  - Der „Nächste Episode“-Button `butSkip` ist durch „Werbung überspringen“ ersetzt
    (Icon `ic_skip_ad_outro`).
  - Menü (`res/menu/mediaplayer.xml`): „Wer hat Play gedrückt?“, „Werbe-Outro merken“.
- **Einstellungen:** `MainPreferencesFragment` (Fork-Hinweis, ausgeblendete
  Forum-/Mitwirken-/Fehler-melden-Einträge), `UserInterfacePreferencesFragment` +
  `ui/preferences/.../arrays.xml` + `UserPreferences` (10-min-Button in der Mediensteuerung).
- **Texte:** nur in `ui/i18n/src/main/res/values/strings_fork.xml` **und**
  `values-de/strings_fork.xml`, immer beide pflegen. Das weicht bewusst von AGENTS.md ab:
  Fork-Texte bleiben getrennt, damit Übersetzungs-Updates von upstream beim Merge nicht
  kollidieren. upstreams `strings.xml` nie anfassen.
- **CI:**
  - `.github/workflows/fork-release.yml`: bei Push auf `main`/`beta` Unit-Tests
    `:playback:service`, signierte Release-APK, GitHub Release.
  - `fork-sync-upstream.yml`: montags 05:17 UTC wird das neueste upstream-Release
    gemergt, danach der Build angestoßen.
  - Die übrigen Workflows sind von upstream. `checks.yml` läuft auch auf PRs in diesem Fork.

## Bauen und testen

AGENTS.md nennt `./gradlew :app:assembleDebug` und `installPlayDebug`. **In diesem Fork
stattdessen den Flavor `free` verwenden** (so baut auch die CI). In Cloud-Sessions gibt es
weder Gerät noch Emulator, also kein `adb`.

```bash
./gradlew --console=plain --max-workers=2 :app:assembleFreeDebug checkstyle
./gradlew --console=plain :app:testFreeDebugUnitTest --tests 'de.danoeh.antennapod.ui.screen.playback.outro.*'
./gradlew --console=plain :playback:service:testFreeDebugUnitTest
# APK: app/build/outputs/apk/free/debug/app-free-debug.apk
```

- Layout-XML (`*/res/layout/*.xml`) muss dem upstream-Formatter entsprechen (prüft `checks.yml`):
  `java -jar android-xml-formatter.jar <datei>`. Den Download-Link enthält `.github/workflows/checks.yml`.
- JDK 21 + Android SDK (`local.properties` mit `sdk.dir`).

## Ablauf: Branch → PR → Merge → Release

1. **Nie direkt auf `main` arbeiten.** Für jede Änderung einen Branch anlegen
   (z. B. `claude/<thema>`). Nach AGENTS.md nie auf `develop`/`master` committen;
   `develop` ist hier ohnehin nur der alte upstream-Stand.
2. Commits:
   - Deutsch, Präfix `Fork: …`, Autor `Frederik Langner <Frederik_Langner@mac.com>`.
   - Am Ende die Co-Authored-By-/Session-Zeilen, die die jeweilige Session vorgibt.
3. **Vor dem Push:** Build + Checkstyle + passende Tests (siehe oben). Neue Texte EN + DE
   in `strings_fork.xml`. `README-FORK.md` (Nutzersicht) und `TODO.md` mitpflegen.
4. PR gegen `main`:
   - Die upstream-Vorlage `.github/pull_request_template.md` und die PR-Regeln in AGENTS.md
     (Issue-Nummer, Checkliste) sind für AntennaPod gedacht und gelten hier nicht.
   - Kurze deutsche Beschreibung reicht.
   - Auf dem PR laufen upstreams `checks.yml`-Jobs (Lint, Unit-, Emulator-Tests). Ob die
     mit Fork-Code grün sind, ist noch nicht erprobt, siehe TODO.
5. **Merge nur nach Freigabe durch den Nutzer.** Der Merge auf `main` löst
   `fork-release.yml` aus. Rund 5 Minuten später gibt es das Release
   `antennapod-fork-v<n>` als „Latest“, und Obtainium bietet das Update an.
6. Nach jedem Release prüfen, ob es grün ist und als „Latest“ eine APK hat. Dem Nutzer
   sagen, dass Obtainium das Update anbieten sollte. Kontrolle in der App: Einstellungen →
   Über → Version endet auf `-fork.<n>`.
7. Nur Doku oder Vorbereitung ohne eigenes Release: Commit-Message mit `[skip ci]`.
   Dann gibt es keinen Build; die Änderung kommt mit dem nächsten Release mit.
8. Zum Testen vor `main` den Beta-Kanal nur auf Wunsch nutzen: Branch `beta` pushen
   (siehe oben). Sonst liefert die Session eine Debug-APK als Datei.

## Feste Entscheidungen des Nutzers – nicht ohne Rückfrage ändern

- „10 min zurück + Play“ startet **keinen eigenen Sleep-Timer**. Das übernimmt upstreams
  „Schlummerfunktion automatisch aktivieren“ (Zeitraum im Schlummer-Dialog).
- Bewusst wieder entfernt:
  - eigener 10-min-Timer,
  - Widget-Button „Sleep-Timer an/aus“ und „Timer + Play“,
  - Option „Erneutes Play startet den Timer neu“.
- **Widget: keine durchsichtigen oder ausgeblendeten Elemente, kein Abdunkeln.** Good Lock
  zeigt dann „widget with no data“, und auf dem Sperrbildschirm wirkt es ohnehin nicht.
- Widget-Option „Nur im Schlummer-Zeitraum bedienbar“ bleibt **pro Widget** als Häkchen.
  Außerhalb des Zeitraums öffnet ein Tippen nur die App bzw. wird ignoriert; **Pausieren
  bleibt erlaubt**.
- Widget-Optionen, gewünscht und vorhanden:
  - „Nur Buttons“,
  - „10 min zurück + Play“,
  - „Werbung überspringen“,
  - „Play/Pause ausblenden“ (wirkt nur, wenn ein anderer Button gewählt ist),
  - „Buttons untereinander“.
- Werbung überspringen:
  - Vorlage **pro Podcast** (nicht pro Episode).
  - Nur heruntergeladene Episoden.
  - Suchfenster 10 min.
  - Schwelle **0,8**, vom Nutzer gewählt. Kalibriert: das echte Outro lag über 0,9, alles
    andere unter 0,8.
  - Der Player-Button ersetzt „Nächste Episode“. Zur nächsten Episode kommt man weiter über
    Benachrichtigung, Sperrbildschirm oder Kopfhörer.
  - Der Score-Toast bleibt.
- Upstream-Updates: nur per **Merge**, nie Rebase. Automatisch montags.
- Ausgeblendet bleiben Forum, „Mitwirken“ und „Fehler melden“ (sie führen zum
  AntennaPod-Team). „Über“ und „Dokumentation“ bleiben sichtbar.
- **Bluetooth-Kopfhörer automatisch verbinden** oder Bluetooth einschalten: abgelehnt
  („wir lassen alles so wie es ist“).
- Sicherung des Stands, den der Nutzer „schon sehr mag“: Branch `stabil-2026-10-21` (= v16,
  `9123aee`). **Nicht löschen.**

## Gelernte Fallen

- **Git-Proxy in Cloud-Sessions:**
  - Tags pushen und Remote-Branches löschen (`git push --delete`) schlägt fehl.
  - Workaround für Branches: anlegen über das GitHub-MCP-Tool `create_branch`.
  - Workaround fürs Löschen: einmaligen Workflow auf einem Wegwerf-Branch pushen, der per
    `gh api -X DELETE …/git/refs/heads/<branch>` aufräumt und sich selbst mitlöscht. Die
    Branches `cleanup-beta`/`cleanup-feature` wurden so benutzt.
  - Releases löschen geht nur so oder in der GitHub-Weboberfläche.
- **`gh release create` braucht `--target "$GITHUB_SHA"`**, sonst zeigt der Tag auf den
  Default-Branch statt auf den gebauten Commit (einmal bei beta-v17 passiert).
- **`versionCode` muss steigen.** Er war bei allen Fork-Builds gleich; Obtainium hat v24
  deshalb nicht installiert, obwohl es v24 als installiert anzeigte. Seit `1de9709` gilt:
  upstream + Build-Nr.
- **Widget-Buttons bei pausierter Wiedergabe:**
  - Nur Media-Button-Intents mit `PendingIntent.getForegroundService` erlauben dem Dienst,
    aus dem Hintergrund in den Vordergrund zu wechseln.
  - Ein Broadcast-Receiver, der den Dienst bindet, ist abgestürzt.
- **PendingIntent-Extras gehören nicht zur Identität** (`filterEquals`). Varianten mit
  anderen Extras brauchen einen eigenen Request-Code, sonst überschreibt
  `FLAG_UPDATE_CURRENT` die andere.
- **RemoteViews nicht über Widget-IDs hinweg teilen.** Früher setzte eine Instanz den
  Zustand für alle; jetzt baut `createViews` pro ID.
- **Good Lock zeichnet Sperrbildschirm-Widgets nicht zuverlässig neu.** Deshalb wird der
  Zeitraum beim Tippen im Dienst bzw. in der Activity geprüft (`sleep_window_only`), nicht
  nur beim Zeichnen.
- `playback/service` hat **keine** `androidx.work`-Abhängigkeit, `WidgetUpdaterWorker` ist
  dort nicht nutzbar.
- **Checkstyle-Stolpersteine:**
  - `VariableDeclarationUsageDistance`: Variablen `final` machen oder verschieben.
  - `SingleLineJavadoc` mit `{@code}` → mehrzeilig schreiben.
  - Lokale Namen wie `wRe` verstoßen gegen `LocalVariableName`.
- `BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE` ist `true`. Code für den alten `PlaybackService`
  ist nicht relevant.
- Maven Central lieferte in einer Session HTTP 429. Gelöst wurde das mit einem
  Gradle-Init-Skript, das einen Mirror einträgt. Das Skript lag nur im damaligen Container
  und ist weg; bei Bedarf neu schreiben.
- Die Verlaufsanzeige in Obtainium kann veraltet sein. Bei Zweifeln die Version in der App
  prüfen; notfalls die APK direkt von der Release-Seite installieren (gleiche Signatur).
- Wenn eine Plugin- oder App-Änderung „nicht wirkt“: erst prüfen, ob die neue Version
  wirklich installiert ist.

## Übergabe-Notizen (Stand 2026-11-11)

- **Letzter Release:** `antennapod-fork-v25` (2026-11-02, Commit `022a899`). Erstes Release
  mit hochgezähltem `versionCode` (3120395 + 25).
- **Branches:**
  - `main` = v25 + diese Doku (nach dem Merge dieses PRs).
  - `stabil-2026-10-21` = Sicherung v16.
  - `develop` = alter upstream-Stand, nicht benutzen.
  - Kein `beta`, keine Feature-Branches offen.
- **Upstream-Sync:** zuletzt am 2026-11-09 gelaufen, kein neues upstream-Release
  (Basis weiter 3.12.3).
- **Halbfertig:** nichts im Code. Alles Gebaute ist released.
- **Offene Fragen an den Nutzer** (Details in `TODO.md`):
  1. Hat Obtainium v25 ohne Umweg installiert (versionCode-Fix)?
  2. Greift die Tagessperre des Sperrbildschirm-Widgets seit v25? Im Protokoll
     „Wer hat Play gedrückt?“ sollten dann „…ignoriert“-Einträge statt Starts stehen.
  3. Kopfhörer-Play nach Ablauf des Sleep-Timers (App im Hintergrund) – tritt das noch auf?
     Die Diagnosefragen dazu sind unbeantwortet.
  4. Erkennt der Werbesprung das Outro auch in anderen Folgen zuverlässig (Score ≥ 0,8)?
  5. Funktioniert der Widget-Button „Werbung überspringen“ auf dem Good-Lock-Sperrbildschirm
     ohne Entsperren?
- Für dieses Projekt gibt es keine Artifacts, Mockups oder sonstigen externen Links.
