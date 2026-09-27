package de.kopfschmerztagebuch.data

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Schreibt einen Bericht für die Ärztin/den Arzt als PDF (A4 quer). */
object PdfBericht {
    private const val B = 842
    private const val H = 595
    private const val RAND = 36f
    private val DATUM = DateTimeFormatter.ofPattern("EE dd.MM.yy", Locale.GERMAN)
    private val DATUM_LANG = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)

    private val SPALTEN = listOf(
        "Datum" to 56f, "Stärke" to 32f, "Beginn / Dauer" to 76f, "Art / Ort" to 92f, "Begleitsymptome" to 96f,
        "Auslöser" to 84f, "Medikament" to 96f, "Alltag" to 66f, "Schlaf / Bildschirm / Trinken" to 86f, "Notiz" to 86f,
    )

    fun schreiben(eintraege: Map<String, Eintrag>, heute: LocalDate, out: OutputStream) {
        val doc = PdfDocument()
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; color = Color.rgb(11, 60, 73) }
        val fett = Paint(text).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val titel = Paint(fett).apply { textSize = 18f }
        val linie = Paint().apply { color = Color.rgb(201, 228, 236); strokeWidth = 0.6f }
        val kopfGrund = Paint().apply { color = Color.rgb(234, 246, 249) }
        val zeilenH = 11f

        var seitenNr = 0
        lateinit var seite: PdfDocument.Page
        var y = 0f

        fun tabellenKopf() {
            val c = seite.canvas
            c.drawRect(RAND, y - 10f, B - RAND, y + 5f, kopfGrund)
            var x = RAND + 3f
            SPALTEN.forEach { (name, w) -> c.drawText(name, x, y, fett); x += w }
            y += 14f
        }

        fun neueSeite(mitKopf: Boolean) {
            if (seitenNr > 0) doc.finishPage(seite)
            seitenNr++
            seite = doc.startPage(PdfDocument.PageInfo.Builder(B, H, seitenNr).create())
            y = RAND + 10f
            seite.canvas.drawText("Seite $seitenNr", B - RAND - 40f, H - 18f, text)
            if (mitKopf) tabellenKopf()
        }

        // Deckblatt-Teil mit Zusammenfassung
        neueSeite(mitKopf = false)
        val c0 = seite.canvas
        c0.drawText("Kopfschmerz-Tagebuch", RAND, y + 8f, titel)
        y += 26f
        val tage = eintraege.keys.sorted()
        val zeitraum = if (tage.isEmpty()) "keine Einträge" else
            "Einträge vom ${LocalDate.parse(tage.first()).format(DATUM_LANG)} bis ${LocalDate.parse(tage.last()).format(DATUM_LANG)} · erstellt am ${heute.format(DATUM_LANG)}"
        c0.drawText(zeitraum, RAND, y, text)
        y += 20f
        for (zeitraumTage in listOf(30, 90)) {
            val a = Auswertung.berechnen(eintraege, heute, zeitraumTage)
            val teile = mutableListOf(
                "Letzte $zeitraumTage Tage: ${a.eingetragen} Tage eingetragen",
                "${a.mitSchmerzen} mit Kopfschmerzen",
                "${a.schmerzfrei} schmerzfrei",
            )
            a.durchschnitt?.let { teile += "Ø Stärke ${"%.1f".format(Locale.GERMAN, it)}" }
            a.staerkster?.let { teile += "stärkster Wert $it" }
            if (a.mitSchmerzen > 0) teile += "Medikament an ${a.mitMedikament} Tagen"
            if (a.eingeschraenkt > 0) teile += "Alltag eingeschränkt an ${a.eingeschraenkt} Tagen"
            if (a.haeufigsteBegleit.isNotEmpty()) teile += "häufigste Begleitsymptome: " + a.haeufigsteBegleit.joinToString { "${it.first} (${it.second}×)" }
            if (a.haeufigsteAusloeser.isNotEmpty()) teile += "häufigste Auslöser: " + a.haeufigsteAusloeser.joinToString { "${it.first} (${it.second}×)" }
            umbrechen(teile.joinToString(" · "), B - 2 * RAND, text).forEach { zeile ->
                c0.drawText(zeile, RAND, y, text)
                y += 12f
            }
            y += 4f
        }
        y += 14f
        tabellenKopf()

        for (t in tage) {
            val e = eintraege.getValue(t)
            val zellen = listOf(
                LocalDate.parse(t).format(DATUM) + if (e.nachgetragen) "*" else "",
                if (e.frei) "frei" else e.staerke.toString(),
                if (e.frei) "" else listOf(e.beginn.takeIf { it.isNotEmpty() }?.let { "ab $it Uhr" }, e.dauer).filterNotNull().filter { it.isNotEmpty() }.joinToString(", "),
                if (e.frei) "" else (e.art + e.ort).joinToString(", "),
                e.begleit.joinToString(", "),
                e.ausloeser.joinToString(", "),
                listOfNotNull(
                    e.medikamentText.ifEmpty { null },
                    e.medikamentZeit.ifEmpty { null }?.let { "um $it" },
                    e.wirkung.ifEmpty { null }?.let { "($it)" },
                ).joinToString(" "),
                e.alltag.takeIf { it != "gar nicht" }.orEmpty(),
                listOf(e.schlaf.ifEmpty { null }?.let { "Schlaf $it" }, e.bildschirm, e.trinken).filterNotNull().filter { it.isNotEmpty() }.joinToString(" · "),
                e.notiz,
            )
            val umbrochen = zellen.mapIndexed { i, z -> umbrechen(z, SPALTEN[i].second - 6f, text) }
            val hoehe = umbrochen.maxOf { it.size }.coerceAtLeast(1) * zeilenH + 4f
            if (y + hoehe > H - RAND) neueSeite(mitKopf = true)
            val c = seite.canvas
            var x = RAND + 3f
            umbrochen.forEachIndexed { i, zeilen ->
                val p = if (i == 1 && !e.frei) fett else text
                zeilen.forEachIndexed { n, s -> c.drawText(s, x, y + n * zeilenH, p) }
                x += SPALTEN[i].second
            }
            y += hoehe
            c.drawLine(RAND, y - zeilenH + 1f, B - RAND, y - zeilenH + 1f, linie)
        }
        if (y + 20f > H - RAND) neueSeite(mitKopf = false)
        seite.canvas.drawText("Stärke: 1 = kaum spürbar … 10 = schlimmster Schmerz · * = nachgetragen", RAND, y + 8f, text)
        doc.finishPage(seite)
        doc.writeTo(out)
        doc.close()
    }

    private fun umbrechen(s: String, breite: Float, p: Paint): List<String> {
        if (s.isEmpty()) return emptyList()
        val zeilen = mutableListOf<String>()
        var akt = ""
        for (wort in s.split(" ")) {
            val probe = if (akt.isEmpty()) wort else "$akt $wort"
            if (p.measureText(probe) <= breite) akt = probe
            else {
                if (akt.isNotEmpty()) zeilen += akt
                var rest = wort
                while (p.measureText(rest) > breite && rest.length > 1) {
                    var n = rest.length
                    while (n > 1 && p.measureText(rest, 0, n) > breite) n--
                    zeilen += rest.substring(0, n); rest = rest.substring(n)
                }
                akt = rest
            }
        }
        if (akt.isNotEmpty()) zeilen += akt
        return zeilen
    }
}
