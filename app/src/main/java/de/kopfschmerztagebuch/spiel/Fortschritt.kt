package de.kopfschmerztagebuch.spiel

import java.time.LocalDate
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

data class Turm(val h: Float, val punkteNoetig: Int, val klippe: Boolean = false) {
    val name: String get() = (if (h % 1f == 0f) h.toInt().toString() else h.toString().replace('.', ',')) + " m"
}

val TUERME = listOf(
    Turm(3f, 0), Turm(5f, 150), Turm(7.5f, 400), Turm(10f, 800),
    Turm(15f, 1500, klippe = true), Turm(20f, 2500, klippe = true),
)

data class Outfit(
    val name: String,
    val hose: Long,
    val punkteNoetig: Int,
    val kappe: Long? = null,
    val brille: Boolean = false,
    val umhang: Long? = null,
)

val OUTFITS = listOf(
    Outfit("Koralle", 0xFFFF6B4A, 0),
    Outfit("Ozeanblau", 0xFF2563EB, 300),
    Outfit("Limette mit Brille", 0xFF65A30D, 700, brille = true),
    Outfit("Profi mit Kappe", 0xFF7C3AED, 1200, kappe = 0xFFFFFFFF, brille = true),
    Outfit("Goldmedaille", 0xFFE0A100, 2000, kappe = 0xFFE0A100, brille = true),
    Outfit("Superheld", 0xFFDC2626, 4000, umhang = 0xFF2563EB),
    Outfit("Hai-Legende", 0xFF475569, 7000, kappe = 0xFF475569, brille = true, umhang = 0xFF0B3C49),
)

data class Abzeichen(val id: String, val emoji: String, val titel: String, val text: String)

val ABZEICHEN = listOf(
    Abzeichen("erster", "🌊", "Ins Wasser!", "Den ersten Sprung gemacht"),
    Abzeichen("salto", "🤸", "Salto", "Einen Salto gedreht"),
    Abzeichen("doppel", "🌀", "Doppelsalto", "Zwei Salti in einem Sprung"),
    Abzeichen("dreifach", "🔥", "Dreifachsalto", "Drei Salti in einem Sprung"),
    Abzeichen("vierfach", "🚀", "Vierfachsalto", "Vier Salti – wie bei Olympia!"),
    Abzeichen("schraube", "🔩", "Schraube", "Eine ganze Schraube gedreht"),
    Abzeichen("kombi", "🌪️", "Salto + Schraube", "Salto und Schraube in einem Sprung"),
    Abzeichen("perfekt", "🎯", "Perfekt!", "Perfekt senkrecht eingetaucht"),
    Abzeichen("serie3", "✨", "Perfekt-Serie", "Dreimal hintereinander perfekt"),
    Abzeichen("zehn", "🥇", "Glatte Zehn", "Alle Kampfrichter zeigen 10"),
    Abzeichen("absprung", "🦘", "Sprungkraft", "Einen perfekten Absprung erwischt"),
    Abzeichen("klatscher", "💥", "Autsch!", "Einen Bauchklatscher überlebt"),
    Abzeichen("turm10", "🏢", "Zehner", "Vom 10-m-Turm gesprungen"),
    Abzeichen("klippe", "🏔️", "Klippenspringer", "Vom 20-m-Felsen gesprungen"),
    Abzeichen("aufgabe", "📋", "Aufgabe erledigt", "Eine Tagesaufgabe geschafft"),
    Abzeichen("tagebuch7", "📅", "Eine Woche dabei", "7 Tage am Stück Tagebuch geführt"),
    Abzeichen("tagebuch30", "🏆", "Ein Monat dabei", "30 Tage am Stück Tagebuch geführt"),
)

data class SprungErgebnis(
    val turmIdx: Int,
    val salti: Int,
    val schrauben: Int,
    val abweichungGrad: Int,
    val kopfueber: Boolean,
    val gehockt: Boolean,
    val schraubeUnfertig: Boolean,
    val absprung: Float,
    val noten: List<Float>,
    val schwierigkeit: Float,
    val punkte: Int,
    val text: String,
    val perfekt: Boolean,
    val klatscher: Boolean,
    val serie: Int,
) {
    /** Summe der drei mittleren Noten (höchste und niedrigste fallen weg, wie im echten Wettkampf). */
    val wertung: Float get() = noten.sorted().subList(1, 4).sum()
}

object Bewertung {
    private const val VOLL = 2 * PI

    fun bewerten(
        turmIdx: Int, winkel: Double, drehung: Double, schraube: Double, gehockt: Boolean,
        absprung: Float, serieVorher: Int, zufall: Random = Random.Default,
    ): SprungErgebnis {
        val salti = floor(drehung / VOLL).toInt()
        val schrauben = floor(schraube / VOLL + 0.02).toInt()
        val schraubeRest = schraube - schrauben * VOLL
        val schraubeUnfertig = schraubeRest > 0.35
        var a = winkel % VOLL
        if (a < 0) a += VOLL
        val abKopf = abs(a - PI)
        val abFuss = min(a, VOLL - a)
        val kopfueber = abKopf <= abFuss
        val abwGrad = (min(abKopf, abFuss) * 180 / PI).roundToInt()

        var basis = 10f - abwGrad / 9f
        if (gehockt) basis -= 1.5f
        if (schraubeUnfertig) basis -= 2f
        basis = basis.coerceIn(0f, 10f)
        val noten = List(5) { ((basis + (zufall.nextFloat() - 0.5f) * 1.2f) * 2).roundToInt().coerceIn(0, 20) / 2f }

        val perfekt = abwGrad <= 12 && !gehockt && !schraubeUnfertig
        val klatscher = abwGrad > 60
        val serie = if (perfekt) serieVorher + 1 else 0
        val text = when {
            perfekt -> if (kopfueber) "PERFEKT kopfüber!" else "PERFEKT fußwärts!"
            klatscher -> "BAUCHKLATSCHER! 💥"
            gehockt -> "Vergessen zu strecken!"
            schraubeUnfertig -> "Schraube nicht fertig …"
            abwGrad <= 30 -> "Sauber eingetaucht!"
            else -> "Etwas schief …"
        }
        val dd = 0.6f + salti * 0.6f + schrauben * 0.4f
        val h = TUERME[turmIdx].h
        val sum = noten.sorted().subList(1, 4).sum()
        var punkte = (sum * dd * h / 3f * 2f).roundToInt()
        if (serie >= 2) punkte = (punkte * 1.5f).roundToInt()
        return SprungErgebnis(
            turmIdx, salti, schrauben, abwGrad, kopfueber, gehockt, schraubeUnfertig, absprung, noten, dd, punkte,
            if (serie >= 2) "$text PERFEKT-SERIE ×1,5!" else text, perfekt, klatscher, serie,
        )
    }

    /** Abzeichen, die dieser Sprung verdient (unabhängig davon, ob sie schon vorhanden sind). */
    fun abzeichenFuer(e: SprungErgebnis): Set<String> = buildSet {
        add("erster")
        if (e.salti >= 1) add("salto")
        if (e.salti >= 2) add("doppel")
        if (e.salti >= 3) add("dreifach")
        if (e.salti >= 4) add("vierfach")
        if (e.schrauben >= 1 && !e.schraubeUnfertig) add("schraube")
        if (e.schrauben >= 1 && e.salti >= 1 && !e.schraubeUnfertig) add("kombi")
        if (e.perfekt) add("perfekt")
        if (e.serie >= 3) add("serie3")
        if (e.noten.all { it >= 10f }) add("zehn")
        if (e.absprung >= 0.93f) add("absprung")
        if (e.klatscher) add("klatscher")
        if (TUERME[e.turmIdx].h >= 10f) add("turm10")
        if (TUERME[e.turmIdx].h >= 20f) add("klippe")
    }
}

fun zahl(f: Float): String = if (f % 1f == 0f) f.toInt().toString() else String.format(Locale.GERMAN, "%.1f", f)

data class Tagesaufgabe(val text: String, val bonus: Int, val geschafft: (SprungErgebnis) -> Boolean)

/** Jeden Tag eine andere Aufgabe – aus dem Datum abgeleitet, damit sie über den Tag gleich bleibt. */
fun tagesaufgabe(datum: LocalDate, freieTuerme: Int): Tagesaufgabe {
    val r = Random(datum.toEpochDay() * 7919)
    val hoechster = (freieTuerme - 1).coerceAtLeast(0)
    val zielPunkte = listOf(60, 100, 150, 220, 350, 500)[hoechster]
    return when (r.nextInt(8)) {
        0 -> Tagesaufgabe("Schaffe einen Doppelsalto", 100) { it.salti >= 2 }
        1 -> Tagesaufgabe("Tauche PERFEKT ein", 100) { it.perfekt }
        2 -> Tagesaufgabe("Dreh eine Schraube und tauche sauber ein", 100) { it.schrauben >= 1 && !it.schraubeUnfertig && it.abweichungGrad <= 30 }
        3 -> Tagesaufgabe("Salto und Schraube in einem Sprung", 120) { it.salti >= 1 && it.schrauben >= 1 && !it.schraubeUnfertig }
        4 -> Tagesaufgabe("Hol mindestens $zielPunkte Punkte mit einem Sprung", 100) { it.punkte >= zielPunkte }
        5 -> Tagesaufgabe("Spring vom ${TUERME[hoechster].name}-${if (TUERME[hoechster].klippe) "Felsen" else "Turm"} mit Salto", 100) {
            it.turmIdx == hoechster && it.salti >= 1 && !it.klatscher
        }
        6 -> Tagesaufgabe("Erwisch den perfekten Absprung (Balken ganz oben)", 80) { it.absprung >= 0.93f && !it.klatscher }
        else -> Tagesaufgabe("Kampfrichter-Wertung von mindestens 25", 100) { it.wertung >= 25f }
    }
}

/** Sprünge pro Tag: 10 plus Bonus für eine Tagebuch-Serie (je 3 Tage einer, höchstens 5). */
fun spruengeProTag(tagebuchSerie: Int): Int = 10 + bonusSpruenge(tagebuchSerie)
fun bonusSpruenge(tagebuchSerie: Int): Int = min(5, tagebuchSerie / 3)
