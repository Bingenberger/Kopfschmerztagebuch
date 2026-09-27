# Kopfschmerz-Tagebuch für Android

Native Android-App (Kotlin, Jetpack Compose) aus der bisherigen Browser-Version. Alles bleibt auf dem Gerät: Die App hat **keine Internet-Berechtigung**, es gibt keinen Server und keine Cloud-Sicherung.

## Funktionen

**Heute** – Tageseintrag wie gewohnt: Stärke 1–10, Ort, Beginn, Art, Dauer, Auslöser, Medikament, Notiz sowie für jeden Tag Bildschirmzeit und Trinkmenge (Pflicht). „Heute keine Kopfschmerzen 🎉“ für schmerzfreie Tage. Gestern und Vorgestern lassen sich nachtragen, der heutige Eintrag lässt sich ändern (die Felder sind dann schon ausgefüllt).

**Verlauf** – Kennzahlen der letzten 30 Tage (Tage mit Kopfschmerzen, schmerzfreie Tage, Ø Stärke, häufigste Auslöser), Säulendiagramm der Stärke, Liste aller Einträge. Antippen zeigt Details, dort kann man den Eintrag bearbeiten oder löschen.

**Export für den Arzt** – PDF-Bericht (Zusammenfassung 30/90 Tage + Tabelle aller Einträge) oder CSV-Tabelle (gleiches Format wie die Web-Version, öffnet sich in Excel). Jeweils *Teilen …* (Mail, Messenger, Drive …) oder *Speichern* in einen Ordner nach Wahl.

**Mehr**
- Tägliche Erinnerung als echte Android-Benachrichtigung – kommt auch bei geschlossener App und nach einem Neustart, aber nur, wenn der Eintrag noch fehlt.
- Datensicherung als JSON-Datei speichern und wiederherstellen (inkl. Spielstand).
- CSV aus der Web-Version einlesen: dort unter „Verlauf“ exportieren, hier einlesen. Vorhandene Tage bleiben unverändert.

## Sprungturm (Belohnungsspiel)

Wie bisher erst nach dem Tageseintrag spielbar, mit Tageslimit. Neu in der App:

- **Absprung mit Timing:** Kraft-Balken – wer oben tippt, springt höher und hat mehr Zeit für Salti.
- **Schrauben:** Im Flug zur Seite wischen dreht eine Schraube (nur gestreckt). Halten = Hocke/Salto wie bisher.
- **Kampfrichter:** Fünf Noten, höchste und niedrigste fallen weg; Punkte = Wertung × Schwierigkeit × Turmhöhe. Abzug, wenn man beim Eintauchen noch gehockt ist oder die Schraube nicht fertig ist.
- **Klippenspringen:** 15 und 20 m vom Felsen ins Meer, mit Möwen und Segelboot.
- **Tageszeit:** Morgenrot, Tag, Abendsonne oder Sternenhimmel – je nach Uhrzeit.
- **Tagesaufgabe** mit Bonuspunkten (jeden Tag eine andere).
- **17 Abzeichen** und **7 Outfits** (Badehose, Kappe, Schwimmbrille, Umhang …) zum Freischalten.
- **Tagebuch-Serie wird belohnt:** Je 3 Tage Eintrag am Stück gibt es einen Bonussprung (höchstens 5), dazu Abzeichen für 7 und 30 Tage.
- **Sound und Vibration** (synthetisch erzeugt, einzeln abschaltbar), Konfetti bei neuen Abzeichen und Freischaltungen.

## Installieren

Bei jedem Push baut GitHub Actions (`.github/workflows/android.yml`) eine Debug-APK. Sie steht in der Übersicht des Workflow-Laufs unter *Artifacts* zum Herunterladen bereit (ZIP entpacken) und lässt sich nach Erlauben von „Unbekannte Apps installieren“ direkt auf dem Handy installieren. Voraussetzung: Android 8.0 oder neuer.

## Bauen

Voraussetzung: Android Studio (Ladybug oder neuer) bzw. JDK 17 mit Android-SDK 35.

```bash
./gradlew testDebugUnitTest   # Tests
./gradlew assembleDebug       # APK: app/build/outputs/apk/debug/app-debug.apk
```

## Aufbau

| Pfad | Inhalt |
|---|---|
| `data/` | Datenmodell, Speicherung (eine JSON-Datei im privaten App-Speicher), CSV, PDF-Bericht, Auswertung |
| `erinnerung/` | Tägliche Benachrichtigung (AlarmManager), Neuplanung nach Neustart |
| `spiel/` | Physik und Wertung, Abzeichen/Outfits/Tagesaufgabe, Zeichnen, Klang |
| `ui/` | Compose-Oberfläche: Heute, Verlauf, Sprungturm, Mehr |
