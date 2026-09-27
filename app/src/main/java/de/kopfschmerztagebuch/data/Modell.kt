package de.kopfschmerztagebuch.data

import kotlinx.serialization.Serializable

/** Ein Tageseintrag. Schlüssel in [Daten.eintraege] ist das Datum im Format JJJJ-MM-TT. */
@Serializable
data class Eintrag(
    val frei: Boolean,
    val staerke: Int = 0,
    val beginn: String = "",
    val art: List<String> = emptyList(),
    val ort: List<String> = emptyList(),
    val dauer: String = "",
    val ausloeser: List<String> = emptyList(),
    /** Aus „Meine Medikamente“ gewählt (Name, ggf. mit Dosis). */
    val medikamente: List<String> = emptyList(),
    /** Frei eingetragenes Medikament (auch aus älteren Versionen/CSV-Import). */
    val medikament: String = "",
    /** Uhrzeit der Einnahme (HH:MM). */
    val medikamentZeit: String = "",
    /** Hat das Medikament geholfen? Siehe [Auswahl.WIRKUNG]. */
    val wirkung: String = "",
    val begleit: List<String> = emptyList(),
    /** Einschränkung im Alltag, siehe [Auswahl.ALLTAG]. */
    val alltag: String = "",
    val notiz: String = "",
    val bildschirm: String = "",
    val trinken: String = "",
    val schlaf: String = "",
    /** Uhrzeit, zu der der Eintrag gespeichert wurde (HH:MM). */
    val zeit: String = "",
    val nachgetragen: Boolean = false,
) {
    /** Alle Medikamente als Text, z. B. „Ibuprofen, Nasenspray“. */
    val medikamentText: String get() = (medikamente + medikament).filter { it.isNotBlank() }.joinToString(", ")
    val mitMedikament: Boolean get() = !frei && medikamentText.isNotEmpty()
}

@Serializable
data class SpielStand(
    val gesamt: Int = 0,
    val rekord: Int = 0,
    val rekordDatum: String = "",
    /** Datum, auf das sich [spruengeHeute] bezieht. */
    val sprungDatum: String = "",
    val spruengeHeute: Int = 0,
    val abzeichen: Set<String> = emptySet(),
    val outfit: Int = 0,
    val ton: Boolean = true,
    val vibration: Boolean = true,
    /** Datum, an dem die Tagesaufgabe zuletzt geschafft wurde. */
    val aufgabeDatum: String = "",
    val sprungZahl: Int = 0,
)

@Serializable
data class Daten(
    val version: Int = 1,
    val eintraege: Map<String, Eintrag> = emptyMap(),
    val spiel: SpielStand = SpielStand(),
    /** Uhrzeit der täglichen Erinnerung (HH:MM) oder null, wenn ausgeschaltet. */
    val erinnerung: String? = null,
    /** Vorauswahl beim Eintragen; die Eltern können sie unter „Mehr“ anpassen (z. B. mit Dosis). */
    val meineMedikamente: List<String> = listOf("Ibuprofen", "Paracetamol"),
)

object Auswahl {
    val ORTE = listOf("Stirn", "Schläfen", "Hinterkopf", "links", "rechts", "ganzer Kopf")
    val AUSLOESER = listOf("Stress", "wenig Schlaf", "viel Bildschirm", "Wetter", "Sport", "wenig getrunken", "Essen ausgelassen", "weiß nicht")
    val ARTEN = listOf("pochend", "drückend", "stechend", "ziehend", "pulsierend")
    val DAUER = listOf("unter 30 Minuten", "30–60 Minuten", "1–2 Stunden", "2–4 Stunden", "länger als 4 Stunden", "den ganzen Tag", "hält noch an")

    /** Begleitsymptome – wichtig, um Migräne von Spannungskopfschmerz zu unterscheiden. */
    val BEGLEIT = listOf(
        "Übelkeit", "Erbrechen", "Licht stört", "Lärm stört", "Gerüche stören", "Sehstörung (Flimmern, Zacken)",
        "Schwindel", "Bauchweh", "Bewegung macht es schlimmer",
    )
    val ALLTAG = listOf("gar nicht", "etwas – Pause gebraucht", "stark – musste mich hinlegen", "Schule/Termin verpasst")
    val WIRKUNG = listOf("gut geholfen", "etwas geholfen", "nicht geholfen", "weiß noch nicht")
    val SCHLAF = listOf("unter 7 Stunden", "7–8 Stunden", "8–9 Stunden", "9–10 Stunden", "mehr als 10 Stunden")

    /** Gesichter zur Stärke 1–10, angelehnt an die Gesichter-Skala für Kinder. */
    val GESICHT = listOf("", "🙂", "🙂", "😐", "😐", "😕", "😕", "😣", "😣", "😫", "😭")
    val BILDSCHIRM = listOf("unter 1 Stunde", "1–2 Stunden", "2–3 Stunden", "3–4 Stunden", "4–5 Stunden", "mehr als 5 Stunden")
    val TRINKEN = listOf("unter 0,5 Liter", "0,5–1 Liter", "1–1,5 Liter", "1,5–2 Liter", "mehr als 2 Liter")
    val STAERKE_TEXT = listOf(
        "", "kaum spürbar", "sehr leicht", "leicht", "störend", "mittel",
        "deutlich", "stark", "sehr stark", "kaum auszuhalten", "schlimmster Schmerz",
    )
}
