package de.kopfschmerztagebuch.data

import java.time.LocalDate

/** Kennzahlen für einen Zeitraum, der mit [bis] endet und [tage] Tage umfasst. */
data class Auswertung(
    val tage: Int,
    val eingetragen: Int,
    val mitSchmerzen: Int,
    val schmerzfrei: Int,
    val durchschnitt: Double?,
    val staerkster: Int?,
    /** Tage mit Schmerzmittel – ab etwa 10 Tagen im Monat mit der Ärztin/dem Arzt besprechen. */
    val mitMedikament: Int,
    /** Tage, an denen die Kopfschmerzen den Alltag deutlich eingeschränkt haben. */
    val eingeschraenkt: Int,
    val haeufigsteBegleit: List<Pair<String, Int>>,
    val haeufigsteAusloeser: List<Pair<String, Int>>,
) {
    companion object {
        fun berechnen(eintraege: Map<String, Eintrag>, bis: LocalDate, tage: Int = 30): Auswertung {
            val liste = (0 until tage).mapNotNull { eintraege[bis.minusDays(it.toLong()).toString()] }
            val schmerz = liste.filter { !it.frei }
            return Auswertung(
                tage = tage,
                eingetragen = liste.size,
                mitSchmerzen = schmerz.size,
                schmerzfrei = liste.count { it.frei },
                durchschnitt = schmerz.takeIf { it.isNotEmpty() }?.map { it.staerke }?.average(),
                staerkster = schmerz.maxOfOrNull { it.staerke },
                mitMedikament = schmerz.count { it.mitMedikament },
                eingeschraenkt = schmerz.count { it.alltag.isNotEmpty() && it.alltag != Auswahl.ALLTAG.first() },
                haeufigsteBegleit = schmerz.flatMap { it.begleit }.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }.take(3).map { it.key to it.value },
                haeufigsteAusloeser = schmerz.flatMap { it.ausloeser }.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }.take(3).map { it.key to it.value },
            )
        }
    }
}

/** Ab so vielen Schmerzmittel-Tagen in 30 Tagen weist die App darauf hin, das ärztlich zu besprechen. */
const val SCHMERZMITTEL_GRENZE = 10

/** Anzahl der Tage in Folge mit Eintrag, endend mit [heute] (oder gestern, falls heute noch fehlt). */
fun tagesSerie(eintraege: Map<String, Eintrag>, heute: LocalDate): Int {
    var tag = if (eintraege.containsKey(heute.toString())) heute else heute.minusDays(1)
    var n = 0
    while (eintraege.containsKey(tag.toString())) { n++; tag = tag.minusDays(1) }
    return n
}
