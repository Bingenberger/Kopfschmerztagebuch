package de.kopfschmerztagebuch.spiel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.PI
import kotlin.random.Random

class BewertungTest {
    @Test
    fun perfekterDoppelsaltoKopfueber() {
        val e = Bewertung.bewerten(1, winkel = 5 * PI, drehung = 5 * PI, schraube = 0.0, gehockt = false, absprung = 1f, serieVorher = 0, zufall = Random(1))
        assertEquals(2, e.salti)
        assertTrue(e.perfekt)
        assertTrue(e.kopfueber)
        assertTrue(e.noten.all { it >= 9f })
        assertTrue("doppel" in Bewertung.abzeichenFuer(e))
    }

    @Test
    fun bauchklatscherUndUnfertigeSchraube() {
        val e = Bewertung.bewerten(0, winkel = PI / 2, drehung = PI / 2, schraube = PI, gehockt = false, absprung = 0.5f, serieVorher = 3)
        assertTrue(e.klatscher)
        assertTrue(e.schraubeUnfertig)
        assertEquals(0, e.serie)
        assertFalse(e.perfekt)
    }

    @Test
    fun perfektSerieGibtBonus() {
        val ohne = Bewertung.bewerten(0, 0.0, 0.0, 0.0, false, 1f, 0, Random(5))
        val mit = Bewertung.bewerten(0, 0.0, 0.0, 0.0, false, 1f, 1, Random(5))
        assertEquals(2, mit.serie)
        assertEquals(Math.round(ohne.punkte * 1.5f), mit.punkte)
    }

    @Test
    fun tagesaufgabeBleibtAmTagGleich() {
        val d = LocalDate.parse("2026-09-27")
        assertEquals(tagesaufgabe(d, 3).text, tagesaufgabe(d, 3).text)
        assertEquals(10, spruengeProTag(2))
        assertEquals(12, spruengeProTag(6))
        assertEquals(15, spruengeProTag(40))
    }

    @Test
    fun simulierterSprungLandetImWasser() {
        val s = SprungSpiel()
        s.layout(360f, 500f)
        s.anlaufStarten()
        repeat(30) { s.schritt(0.016) }
        s.abspringen()
        s.haelt = true
        var e: SprungErgebnis? = null
        var n = 0
        while (e == null && n++ < 1000) e = s.schritt(0.016)
        assertTrue(e != null)
        assertTrue(e!!.salti >= 1)
        repeat(100) { s.schritt(0.016) }
        assertEquals(Phase.FERTIG, s.phase)
    }
}
