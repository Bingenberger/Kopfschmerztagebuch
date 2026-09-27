package de.kopfschmerztagebuch.spiel

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

enum class Phase { BEREIT, ANLAUF, FLUG, WASSER, FERTIG }

enum class Ereignis { ANLAUF, ABSPRUNG, SALTO, SCHRAUBE, SPLASH, KLATSCHER, PERFEKT, BELOHNUNG }

class Teilchen(var x: Float, var y: Float, var vx: Float, var vy: Float, var leben: Float, val farbe: Long = 0, val groesse: Float = 3f)
class Popup(val text: String, var x: Float, var y: Float, var leben: Float, val fest: Boolean = false)
class Wolke(var x: Float, val y: Float, val g: Float, val v: Float)
class Moewe(var x: Float, val y: Float, val v: Float, var phase: Float)

/**
 * Physik und Zustand des Turmspringens. Längen in Metern, Zeit in Sekunden.
 * Die Pixel-Umrechnung ([mX], [mY]) hängt an der Größe der Zeichenfläche, siehe [layout].
 */
class SprungSpiel {
    var turmIdx = 0
        private set
    val turm get() = TUERME[turmIdx]
    var phase = Phase.BEREIT
        private set

    // Springer
    var x = 0.45
    var y = 0.0
    var vx = 0.0
    var vy = 0.0
    var winkel = 0.0
    var drehung = 0.0
    var schraube = 0.0
    private var schraubeZiel = 0.0
    private var saltiGezaehlt = 0
    private var schraubenGezaehlt = 0
    private var tiefe = 0.0
    var haelt = false
    var anlaufZeit = 0.0
        private set
    var absprung = 0f
        private set
    val anlaufWert: Float get() = ((1 - cos(anlaufZeit * 2 * PI / 1.1)) / 2).toFloat()
    val gehockt get() = haelt && phase == Phase.FLUG

    var ergebnis: SprungErgebnis? = null
        private set
    var belohnungen: List<String> = emptyList()
    var serie = 0
        private set

    // Bühne (Pixel)
    var breite = 0f; private set
    var hoehe = 0f; private set
    var wasserY = 0f; private set
    var ppm = 40f; private set
    /** Größe der Spielfigur relativ zum Referenzmaß von 360 px Breite. */
    var figur = 1f; private set

    // Effekte
    val spritzer = mutableListOf<Teilchen>()
    val blasen = mutableListOf<Teilchen>()
    val konfetti = mutableListOf<Teilchen>()
    val popups = mutableListOf<Popup>()
    val wolken = mutableListOf<Wolke>()
    val moewen = mutableListOf<Moewe>()
    var schuettel = 0f
    var zeit = 0.0
        private set
    val ereignisse = ArrayDeque<Ereignis>()

    fun mX(m: Double) = (breite * 0.14 + m * ppm).toFloat()
    fun mY(m: Double) = (wasserY - m * ppm).toFloat()

    /**
     * Passt die Pixel-Umrechnung an die Größe der Zeichenfläche an. Der Spielzustand bleibt dabei
     * unangetastet – die Fläche kann sich jederzeit ändern (Hinweistext, Tastatur, Drehung).
     */
    fun layout(b: Float, h: Float) {
        if (b == breite && h == hoehe) return
        val erstesMal = breite == 0f
        breite = b; hoehe = h
        figur = b / 360f
        wasserY = h * 0.86f
        massstabSetzen()
        if (erstesMal) {
            repeat(4) { wolken += Wolke(Random.nextFloat() * b, h * (0.05f + Random.nextFloat() * 0.28f), 0.7f + Random.nextFloat() * 0.8f, 6f + Random.nextFloat() * 9f) }
            repeat(3) { moewen += Moewe(Random.nextFloat() * b, h * (0.12f + Random.nextFloat() * 0.25f), 18f + Random.nextFloat() * 20f, Random.nextFloat() * 6f) }
        }
        if (phase == Phase.BEREIT || phase == Phase.ANLAUF) y = standHoehe()
    }

    private fun massstabSetzen() {
        if (hoehe > 0) ppm = (wasserY - hoehe * 0.08f) / (turm.h + 2.2f)
    }

    /** Höhe des Körpermittelpunkts, wenn der Springer auf der Plattform steht. */
    private fun standHoehe() = turm.h + 21.0 * figur / ppm

    fun turmWaehlen(i: Int) {
        if (phase == Phase.FLUG || phase == Phase.WASSER || i == turmIdx) return
        turmIdx = i
        zuruecksetzen()
    }

    fun zuruecksetzen() {
        massstabSetzen()
        x = 0.45; vx = 0.0; vy = 0.0
        y = standHoehe()
        winkel = 0.0; drehung = 0.0; schraube = 0.0; schraubeZiel = 0.0
        saltiGezaehlt = 0; schraubenGezaehlt = 0; tiefe = 0.0; anlaufZeit = 0.0
        spritzer.clear()
        ergebnis = null
        belohnungen = emptyList()
        phase = Phase.BEREIT
    }

    fun anlaufStarten() {
        if (phase != Phase.BEREIT) return
        phase = Phase.ANLAUF
        anlaufZeit = 0.0
        ereignisse += Ereignis.ANLAUF
    }

    fun abspringen() {
        if (phase != Phase.ANLAUF) return
        absprung = anlaufWert
        phase = Phase.FLUG
        haelt = false
        vy = 2.2 + 2.0 * absprung
        val tFlug = (vy + sqrt(vy * vy + 2 * 9.81 * turm.h)) / 9.81
        val ziel = if (turm.klippe) 3.4 + Random.nextDouble() * 1.6 else 2.2 + Random.nextDouble() * 1.2
        vx = (ziel - x) / tFlug
        ereignisse += Ereignis.ABSPRUNG
        val bewertung = when {
            absprung >= 0.93f -> "Top-Absprung!"
            absprung >= 0.7f -> "Guter Absprung"
            else -> "Schwacher Absprung"
        }
        popups += Popup(bewertung, mX(x) + 60 * figur, mY(turm.h.toDouble()) - 20 * figur, 1.1f)
    }

    fun schraubeAusloesen() {
        if (phase != Phase.FLUG) return
        schraubeZiel += 2 * PI
    }

    /** Liefert das Ergebnis, sobald der Springer eintaucht – sonst null. */
    fun schritt(dt: Double): SprungErgebnis? {
        zeit += dt
        var eingetaucht: SprungErgebnis? = null
        when (phase) {
            Phase.ANLAUF -> anlaufZeit += dt
            Phase.FLUG -> {
                vy -= 9.81 * dt
                x += vx * dt; y += vy * dt
                val dreh = (if (haelt) 9 + turmIdx * 1.2 else 0.6) * dt
                winkel += dreh; drehung += dreh
                if (!haelt && schraube < schraubeZiel) schraube = min(schraubeZiel, schraube + 14 * dt)
                val salti = floor(drehung / (2 * PI)).toInt()
                if (salti > saltiGezaehlt) { saltiGezaehlt = salti; ereignisse += Ereignis.SALTO }
                val schr = floor(schraube / (2 * PI) + 1e-6).toInt()
                if (schr > schraubenGezaehlt) { schraubenGezaehlt = schr; ereignisse += Ereignis.SCHRAUBE }
                if (y <= 0) eingetaucht = eintauchen()
            }
            Phase.WASSER -> {
                tiefe += dt
                y -= 2.2 * dt
                if (tiefe > 0.7) phase = Phase.FERTIG
            }
            else -> {}
        }
        effekteBewegen(dt.toFloat())
        return eingetaucht
    }

    private fun eintauchen(): SprungErgebnis {
        phase = Phase.WASSER
        tiefe = 0.0
        val e = Bewertung.bewerten(turmIdx, winkel, drehung, schraube, haelt, absprung, serie)
        serie = e.serie
        ergebnis = e
        ereignisse += if (e.klatscher) Ereignis.KLATSCHER else Ereignis.SPLASH
        if (e.perfekt) ereignisse += Ereignis.PERFEKT
        if (e.klatscher) schuettel = 0.5f
        val px = mX(x)
        popups += Popup("+${e.punkte}", px, wasserY - 30 * figur, 1.4f)
        val n = 8 + e.abweichungGrad / 4 + turmIdx * 2
        repeat(n) {
            spritzer += Teilchen(
                px, wasserY, (Random.nextFloat() - 0.5f) * (2 + e.abweichungGrad / 10f) * 60 * figur,
                -(2 + Random.nextFloat() * (2 + e.abweichungGrad / 12f + turmIdx * 0.3f)) * 60 * figur, 1f,
            )
        }
        repeat(10) {
            blasen += Teilchen(px + (Random.nextFloat() - 0.5f) * 20 * figur, wasserY + (10 + Random.nextFloat() * 30) * figur, 0f, -(24 + Random.nextFloat() * 42) * figur, 1f)
        }
        return e
    }

    /** Nach dem Eintauchen: Belohnungen (neue Abzeichen, Freischaltungen, Tagesaufgabe) anzeigen. */
    fun belohnen(texte: List<String>) {
        belohnungen = texte
        if (texte.isEmpty()) return
        ereignisse += Ereignis.BELOHNUNG
        val farben = listOf(0xFFFF6B4A, 0xFFFFD966, 0xFF2E9E6B, 0xFF2563EB, 0xFF7C3AED, 0xFFFFFFFF)
        repeat(90) {
            konfetti += Teilchen(
                Random.nextFloat() * breite, -Random.nextFloat() * hoehe * 0.3f,
                (Random.nextFloat() - 0.5f) * 80 * figur, (60 + Random.nextFloat() * 120) * figur,
                1f + Random.nextFloat(), farben.random(), (4 + Random.nextFloat() * 4) * figur,
            )
        }
    }

    fun hinweis(text: String) {
        popups += Popup(text, breite / 2, hoehe * 0.55f, 1.6f, fest = true)
    }

    private fun effekteBewegen(dt: Float) {
        if (schuettel > 0) schuettel -= dt
        val f = dt * 60f
        spritzer.forEach { it.x += it.vx * dt; it.y += it.vy * dt; it.vy += 0.18f * 60 * 60 * figur * dt; it.leben -= 0.02f * f }
        spritzer.removeAll { it.leben <= 0 }
        blasen.forEach { it.y += it.vy * dt; it.leben -= 0.012f * f }
        blasen.removeAll { it.leben <= 0 || it.y < wasserY }
        konfetti.forEach { it.x += it.vx * dt + kotlin.math.sin(zeit.toFloat() * 4 + it.y * 0.05f) * 0.6f; it.y += it.vy * dt; it.leben -= 0.25f * dt }
        konfetti.removeAll { it.leben <= 0 || it.y > hoehe }
        popups.forEach { it.leben -= 0.016f * f; if (!it.fest) it.y -= 0.7f * f * figur }
        popups.removeAll { it.leben <= 0 }
        wolken.forEach { it.x += it.v * dt * figur; if (it.x > breite + 50 * figur) it.x = -50 * figur }
        moewen.forEach { it.x += it.v * dt * figur; it.phase += dt * 8; if (it.x > breite + 30 * figur) it.x = -30 * figur }
    }
}
