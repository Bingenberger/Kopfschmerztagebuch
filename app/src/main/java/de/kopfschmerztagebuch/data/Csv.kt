package de.kopfschmerztagebuch.data

/**
 * CSV im selben Format wie die frühere Web-Version: Semikolon-getrennt, alle Felder in
 * Anführungszeichen, UTF-8 mit BOM (damit Excel die Umlaute richtig anzeigt).
 */
object Csv {
    val KOPF = listOf(
        "Datum", "Schmerzfrei", "Stärke (1–10)", "Beginn", "Art des Schmerzes", "Ort", "Dauer",
        "Auslöser", "Medikament", "Bildschirmzeit", "Trinkmenge", "Notiz", "Uhrzeit des Eintrags", "Nachgetragen",
    )

    fun exportieren(eintraege: Map<String, Eintrag>): String {
        val zeilen = mutableListOf(KOPF)
        eintraege.keys.sorted().forEach { t ->
            val e = eintraege.getValue(t)
            zeilen += if (e.frei) {
                listOf(t, "ja", "", "", "", "", "", "", "", e.bildschirm, e.trinken, e.notiz, e.zeit, if (e.nachgetragen) "ja" else "")
            } else {
                listOf(
                    t, "nein", e.staerke.toString(), e.beginn, e.art.joinToString("; "), e.ort.joinToString("; "),
                    e.dauer, e.ausloeser.joinToString("; "), e.medikament, e.bildschirm, e.trinken, e.notiz, e.zeit,
                    if (e.nachgetragen) "ja" else "",
                )
            }
        }
        return "﻿" + zeilen.joinToString("\n") { z -> z.joinToString(";") { "\"" + it.replace("\"", "\"\"") + "\"" } }
    }

    /** Liest eine CSV aus der App oder aus der Web-Version. Zeilen ohne gültiges Datum werden übersprungen. */
    fun importieren(text: String): Map<String, Eintrag> {
        val ergebnis = linkedMapOf<String, Eintrag>()
        for (z in zerlegen(text.removePrefix("﻿"))) {
            val t = z.getOrNull(0)?.trim() ?: continue
            if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(t)) continue
            fun f(i: Int) = z.getOrNull(i)?.trim().orEmpty()
            fun liste(i: Int) = f(i).split(";").map { it.trim() }.filter { it.isNotEmpty() }
            val frei = f(1).equals("ja", ignoreCase = true)
            ergebnis[t] = if (frei) {
                Eintrag(frei = true, bildschirm = f(9), trinken = f(10), notiz = f(11), zeit = f(12), nachgetragen = f(13) == "ja")
            } else {
                Eintrag(
                    frei = false, staerke = f(2).toIntOrNull()?.coerceIn(1, 10) ?: 5, beginn = f(3), art = liste(4),
                    ort = liste(5), dauer = f(6), ausloeser = liste(7), medikament = f(8), bildschirm = f(9),
                    trinken = f(10), notiz = f(11), zeit = f(12), nachgetragen = f(13) == "ja",
                )
            }
        }
        return ergebnis
    }

    /** Zerlegt CSV mit Semikolon oder Komma als Trenner; Anführungszeichen und Zeilenumbrüche in Feldern werden beachtet. */
    fun zerlegen(text: String): List<List<String>> {
        val erste = text.lineSequence().firstOrNull().orEmpty()
        val trenner = if (erste.count { it == ';' } >= erste.count { it == ',' }) ';' else ','
        val zeilen = mutableListOf<List<String>>()
        var zeile = mutableListOf<String>()
        val feld = StringBuilder()
        var inQuote = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuote) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { feld.append('"'); i++ } else inQuote = false
                } else feld.append(c)
            } else when (c) {
                '"' -> inQuote = true
                trenner -> { zeile += feld.toString(); feld.clear() }
                '\r' -> {}
                '\n' -> { zeile += feld.toString(); feld.clear(); zeilen += zeile; zeile = mutableListOf() }
                else -> feld.append(c)
            }
            i++
        }
        if (feld.isNotEmpty() || zeile.isNotEmpty()) { zeile += feld.toString(); zeilen += zeile }
        return zeilen
    }
}
