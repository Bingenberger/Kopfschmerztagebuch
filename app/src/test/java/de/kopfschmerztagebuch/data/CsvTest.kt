package de.kopfschmerztagebuch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvTest {
    private val beispiel = mapOf(
        "2026-09-20" to Eintrag(
            frei = false, staerke = 7, beginn = "14:30", art = listOf("pochend", "drückend"), ort = listOf("Stirn"),
            dauer = "1–2 Stunden", ausloeser = listOf("Stress", "wenig Schlaf"), medikament = "Ibuprofen 200 mg",
            notiz = "Mathe-Arbeit; \"schwer\"", bildschirm = "2–3 Stunden", trinken = "1–1,5 Liter", zeit = "19:02", nachgetragen = true,
        ),
        "2026-09-21" to Eintrag(frei = true, bildschirm = "unter 1 Stunde", trinken = "mehr als 2 Liter", zeit = "18:10"),
    )

    @Test
    fun exportUndImportErgebenDasselbe() {
        val csv = Csv.exportieren(beispiel)
        assertTrue(csv.startsWith("﻿\"Datum\";"))
        assertEquals(beispiel, Csv.importieren(csv))
    }

    @Test
    fun liestCsvDerWebVersion() {
        // Die Web-Version schrieb bei schmerzfreien Tagen nur 13 Spalten.
        val web = "﻿" + listOf(
            Csv.KOPF,
            listOf("2026-08-30", "ja", "", "", "", "", "", "", "", "1–2 Stunden", "0,5–1 Liter", "", "20:15"),
            listOf("2026-08-31", "nein", "4", "", "stechend", "links; rechts", "30–60 Minuten", "Wetter", "", "3–4 Stunden", "1,5–2 Liter", "", "21:00", ""),
        ).joinToString("\n") { z -> z.joinToString(";") { "\"$it\"" } }
        val e = Csv.importieren(web)
        assertEquals(2, e.size)
        assertEquals(Eintrag(frei = true, bildschirm = "1–2 Stunden", trinken = "0,5–1 Liter", zeit = "20:15"), e["2026-08-30"])
        assertEquals(listOf("links", "rechts"), e["2026-08-31"]!!.ort)
        assertEquals(4, e["2026-08-31"]!!.staerke)
    }

    @Test
    fun auswertungUndSerie() {
        val heute = LocalDate.parse("2026-09-21")
        val a = Auswertung.berechnen(beispiel, heute, 30)
        assertEquals(2, a.eingetragen)
        assertEquals(1, a.mitSchmerzen)
        assertEquals(7.0, a.durchschnitt!!, 0.001)
        assertEquals(2, tagesSerie(beispiel, heute))
        assertEquals(2, tagesSerie(beispiel, heute.plusDays(1)))
        assertEquals(0, tagesSerie(beispiel, heute.plusDays(2)))
    }
}
