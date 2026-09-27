package de.kopfschmerztagebuch.spiel

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val TIEFE = Color(0xFF0B3C49)
private val HAUT = Color(0xFFF2B879)

enum class Tageszeit { MORGEN, TAG, ABEND, NACHT }

fun tageszeit(stunde: Int) = when (stunde) {
    in 5..7 -> Tageszeit.MORGEN
    in 8..17 -> Tageszeit.TAG
    in 18..20 -> Tageszeit.ABEND
    else -> Tageszeit.NACHT
}

/** Zeichenhilfen mit wiederverwendeten Paint-Objekten für Text. */
class Zeichner {
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val sterne = List(40) { Offset(Random.nextFloat(), Random.nextFloat() * 0.55f) }

    private fun DrawScope.schreiben(s: String, x: Float, y: Float, groesse: Float, farbe: Color, fett: Boolean = true, links: Boolean = false) {
        text.textSize = groesse
        text.color = farbe.toArgb()
        text.typeface = Typeface.create(Typeface.DEFAULT, if (fett) Typeface.BOLD else Typeface.NORMAL)
        text.textAlign = if (links) Paint.Align.LEFT else Paint.Align.CENTER
        drawContext.canvas.nativeCanvas.drawText(s, x, y, text)
    }

    fun DrawScope.malen(sp: SprungSpiel, outfit: Outfit, zeit: Tageszeit, gesamt: Int) {
        val w = size.width
        val h = size.height
        val f = sp.figur
        val dx = if (sp.schuettel > 0) (Random.nextFloat() - 0.5f) * 8 * sp.schuettel * f else 0f
        val dy = if (sp.schuettel > 0) (Random.nextFloat() - 0.5f) * 8 * sp.schuettel * f else 0f
        translate(dx, dy) {
            himmel(sp, zeit)
            if (sp.turm.klippe) klippe(sp, zeit) else freibad(sp, zeit)

            // Springer (unter Wasser halb durchsichtig)
            springer(sp, outfit, if (sp.phase == Phase.WASSER || (sp.phase == Phase.FERTIG && sp.ergebnis != null)) 0.45f else 1f)

            sp.spritzer.forEach { drawCircle(Color(0xFF7ADAF0).copy(alpha = it.leben.coerceIn(0f, 1f)), 3f * f, Offset(it.x, it.y)) }
            sp.blasen.forEach { drawCircle(Color.White.copy(alpha = it.leben.coerceIn(0f, 1f)), 2.5f * f, Offset(it.x, it.y), style = Stroke(1.2f * f)) }

            if (sp.phase == Phase.ANLAUF) kraftBalken(sp)

            sp.popups.forEach {
                schreiben(it.text, it.x, it.y, w * (if (it.fest) 0.04f else 0.06f), TIEFE.copy(alpha = it.leben.coerceIn(0f, 1f)) .let { c -> if (zeit == Tageszeit.NACHT && !it.fest) Color.White.copy(alpha = c.alpha) else c })
            }

            val textFarbe = if (zeit == Tageszeit.NACHT) Color.White else TIEFE
            when (sp.phase) {
                Phase.BEREIT -> {
                    val wo = if (sp.turm.klippe) "Felsen" else "Turm"
                    schreiben("Bereit auf dem ${sp.turm.name}-$wo …", w / 2, h * 0.58f, w * 0.042f, textFarbe)
                    schreiben("Gesamt: $gesamt Punkte", w / 2, h * 0.63f, w * 0.036f, textFarbe, fett = false)
                }
                Phase.ANLAUF -> schreiben("Tippe, wenn der Balken oben ist!", w / 2, h * 0.58f, w * 0.042f, textFarbe)
                else -> {}
            }
            val e = sp.ergebnis
            if (e != null && sp.phase == Phase.FERTIG) ergebnisTafel(sp, e)
        }
        sp.konfetti.forEach {
            withTransform({ rotate(it.y * 3 % 360, Offset(it.x, it.y)) }) {
                drawRect(Color(it.farbe).copy(alpha = min(1f, it.leben)), Offset(it.x, it.y), Size(it.groesse, it.groesse * 0.5f))
            }
        }
    }

    private fun DrawScope.himmel(sp: SprungSpiel, zeit: Tageszeit) {
        val w = size.width
        val h = size.height
        val f = sp.figur
        val farben = when (zeit) {
            Tageszeit.TAG -> listOf(Color(0xFF8ED4E8), Color(0xFFD6F0F7), Color(0xFFEAF6F9))
            Tageszeit.MORGEN -> listOf(Color(0xFF9CC7E8), Color(0xFFFCD9C0), Color(0xFFFFF1E0))
            Tageszeit.ABEND -> listOf(Color(0xFF5B6FB5), Color(0xFFF4A67A), Color(0xFFFDE2C4))
            Tageszeit.NACHT -> listOf(Color(0xFF0B1A3A), Color(0xFF1E3A6B), Color(0xFF2B4C7E))
        }
        drawRect(Brush.verticalGradient(0f to farben[0], 0.7f to farben[1], 1f to farben[2]), Offset(-10f, -10f), Size(w + 20, h + 20))
        when (zeit) {
            Tageszeit.TAG -> drawCircle(Color(0xFFFFD966), 16 * f, Offset(w * 0.86f, h * 0.1f))
            Tageszeit.MORGEN, Tageszeit.ABEND -> {
                drawCircle(Color(0x55FFB067), 30 * f, Offset(w * 0.8f, h * 0.5f))
                drawCircle(Color(0xFFFF9A4D), 18 * f, Offset(w * 0.8f, h * 0.5f))
            }
            Tageszeit.NACHT -> {
                sterne.forEachIndexed { i, s ->
                    val funkeln = 0.5f + 0.5f * sin(sp.zeit.toFloat() * 2 + i)
                    drawCircle(Color.White.copy(alpha = 0.4f + 0.5f * funkeln), (1f + (i % 3) * 0.5f) * f, Offset(s.x * w, s.y * h))
                }
                drawCircle(Color(0xFFF5F3CE), 14 * f, Offset(w * 0.84f, h * 0.1f))
                drawCircle(farben[0], 12 * f, Offset(w * 0.84f + 6 * f, h * 0.1f - 3 * f))
            }
        }
        val wolke = if (zeit == Tageszeit.NACHT) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.85f)
        sp.wolken.forEach { wo ->
            drawCircle(wolke, 12 * wo.g * f, Offset(wo.x, wo.y))
            drawCircle(wolke, 10 * wo.g * f, Offset(wo.x + 14 * wo.g * f, wo.y - 5 * wo.g * f))
            drawCircle(wolke, 11 * wo.g * f, Offset(wo.x + 26 * wo.g * f, wo.y))
        }
    }

    private fun DrawScope.freibad(sp: SprungSpiel, zeit: Tageszeit) {
        val w = size.width
        val h = size.height
        val f = sp.figur
        val t = sp.turm
        val nacht = zeit == Tageszeit.NACHT
        // Wiese und Bäume im Hintergrund
        val baum = if (nacht) Color(0xFF16402F) else Color(0xFF4F9A5B)
        for (i in 0 until 6) {
            val x = sp.mX(5.2) + i * 34 * f
            if (x < w + 20 * f) {
                drawRect(Color(0xFF6B4F3A), Offset(x - 2 * f, sp.wasserY - 22 * f), Size(4 * f, 20 * f))
                drawCircle(baum, (13 + (i % 2) * 4) * f, Offset(x, sp.wasserY - (28 + (i % 2) * 5) * f))
            }
        }
        drawRect(if (nacht) Color(0xFF2F4F4A) else Color(0xFF9CCB8F), Offset(0f, sp.wasserY - 2 * f), Size(w, h - sp.wasserY + 2 * f))
        drawRect(if (nacht) Color(0xFF55707A) else Color(0xFFE3EEF1), Offset(sp.mX(1.2), sp.wasserY - 6 * f), Size(sp.mX(4.8) - sp.mX(1.2), h))

        // Turm mit Plattformen
        val tx = sp.mX(0.0)
        drawRect(Color(0xFF5B7C8A), Offset(tx - 9 * f, sp.mY(t.h.toDouble())), Size(18 * f, sp.wasserY - sp.mY(t.h.toDouble()) + 8 * f))
        var y = sp.mY(t.h.toDouble()) + 16 * f
        while (y < sp.wasserY) {
            drawLine(Color(0xFF3E5A66), Offset(tx - 9 * f, y), Offset(tx + 9 * f, y), 2f * f)
            y += 20 * f
        }
        TUERME.filter { !it.klippe && it.h <= t.h }.forEach { p ->
            drawRect(if (p.h == t.h) Color(0xFFFF6B4A) else Color(0xFF8FAAB5), Offset(tx - 9 * f, sp.mY(p.h.toDouble())), Size(52 * f, 6 * f))
            schreiben(p.name, tx + 46 * f, sp.mY(p.h.toDouble()) - 4 * f, 11 * f, if (nacht) Color.White else TIEFE, links = true)
        }

        // Becken
        val links = sp.mX(1.4)
        val rechts = sp.mX(4.6)
        drawRoundRect(
            Brush.verticalGradient(listOf(if (nacht) Color(0xFF1BA3C6) else Color(0xFF0F7FA0), Color(0xFF0B5E78)), sp.wasserY, h),
            Offset(links, sp.wasserY - 4 * f), Size(rechts - links, h - sp.wasserY - 6 * f), CornerRadius(10 * f),
        )
        if (nacht) drawCircle(Color(0x33FFFFFF), 30 * f, Offset((links + rechts) / 2, h - 18 * f))
        welle(links + 4 * f, rechts - 4 * f, sp, Color(0xFF7ADAF0))
    }

    private fun DrawScope.klippe(sp: SprungSpiel, zeit: Tageszeit) {
        val w = size.width
        val h = size.height
        val f = sp.figur
        val nacht = zeit == Tageszeit.NACHT
        // Meer bis zum Horizont
        val horizont = h * 0.64f
        drawRect(
            Brush.verticalGradient(listOf(if (nacht) Color(0xFF123B5C) else Color(0xFF5BB8D4), if (nacht) Color(0xFF0B2E4A) else Color(0xFF0F7FA0)), horizont, h),
            Offset(0f, horizont), Size(w, h - horizont),
        )
        // Segelboot am Horizont
        val bootX = ((sp.zeit * 6 * f) % (w + 60 * f)).toFloat() - 30 * f
        drawPath(Path().apply { moveTo(bootX - 10 * f, horizont + 2 * f); lineTo(bootX + 10 * f, horizont + 2 * f); lineTo(bootX + 7 * f, horizont + 6 * f); lineTo(bootX - 7 * f, horizont + 6 * f); close() }, Color(0xFF7A4B2A))
        drawPath(Path().apply { moveTo(bootX, horizont + 1 * f); lineTo(bootX, horizont - 16 * f); lineTo(bootX + 9 * f, horizont + 1 * f); close() }, Color.White.copy(alpha = if (nacht) 0.5f else 1f))
        // Möwen
        sp.moewen.forEach { m ->
            val flug = sin(m.phase) * 3 * f
            drawPath(Path().apply {
                moveTo(m.x - 7 * f, m.y - flug); quadraticTo(m.x - 3 * f, m.y - 4 * f, m.x, m.y)
                quadraticTo(m.x + 3 * f, m.y - 4 * f, m.x + 7 * f, m.y - flug)
            }, if (nacht) Color(0x88FFFFFF) else Color(0xFF3E5A66), style = Stroke(1.6f * f, cap = StrokeCap.Round))
        }
        // Brandung am Fels
        welle(sp.mX(0.8), w, sp, Color(0xFFB8ECF7))
        // Fels
        val top = sp.mY(sp.turm.h.toDouble())
        val kante = sp.mX(0.75)
        val fels = Path().apply {
            moveTo(-10f, h)
            lineTo(-10f, top)
            lineTo(kante, top)
            lineTo(kante + 4 * f, top + (sp.wasserY - top) * 0.15f)
            lineTo(kante - 6 * f, top + (sp.wasserY - top) * 0.35f)
            lineTo(kante + 10 * f, top + (sp.wasserY - top) * 0.6f)
            lineTo(kante + 2 * f, top + (sp.wasserY - top) * 0.8f)
            lineTo(kante + 22 * f, sp.wasserY + 8 * f)
            lineTo(kante + 22 * f, h)
            close()
        }
        drawPath(fels, Brush.horizontalGradient(listOf(Color(0xFF6E5C4E), if (nacht) Color(0xFF3C332C) else Color(0xFF8C7866)), 0f, kante + 20 * f))
        for (i in 1..5) {
            val yy = top + (sp.wasserY - top) * i / 6f
            drawLine(Color(0x33000000), Offset(kante * 0.2f * i % kante, yy), Offset(kante * 0.2f * i % kante + 18 * f, yy + 8 * f), 1.5f * f)
        }
        drawRect(if (nacht) Color(0xFF2F4F3A) else Color(0xFF6DAA5C), Offset(-10f, top - 3 * f), Size(kante + 10f, 5 * f))
        schreiben(sp.turm.name, kante - 20 * f, top + 16 * f, 11 * f, Color.White)
    }

    private fun DrawScope.welle(von: Float, bis: Float, sp: SprungSpiel, farbe: Color) {
        val f = sp.figur
        val p = Path().apply {
            moveTo(von, sp.wasserY + 4 * f)
            var x = von
            while (x <= bis) {
                lineTo(x, sp.wasserY + sin(x * 0.15f / f + sp.zeit.toFloat() * 4f) * 1.8f * f)
                x += 6 * f
            }
            lineTo(bis, sp.wasserY + 6 * f)
            close()
        }
        drawPath(p, farbe)
    }

    private fun DrawScope.springer(sp: SprungSpiel, o: Outfit, alpha: Float) {
        val f = sp.figur
        val px = sp.mX(sp.x)
        var py = min(sp.mY(sp.y), sp.wasserY + 26 * f)
        if (sp.phase == Phase.ANLAUF) py += sp.anlaufWert * 4 * f
        val hocke = sp.gehockt
        val strich = TIEFE.copy(alpha = alpha)
        val hose = Color(o.hose).copy(alpha = alpha)
        val sw = 4f * f
        withTransform({
            translate(px, py)
            rotate((sp.winkel * 180 / PI).toFloat(), Offset.Zero)
            scale(f, f, Offset.Zero)
            if (!hocke) scale(max(0.3f, abs(cos(sp.schraube.toFloat()))), 1f, Offset.Zero)
        }) {
            val s = sw / f
            o.umhang?.let { u ->
                val flattern = sin(sp.zeit.toFloat() * 14) * 2f
                val ende = if (hocke) 8f else 18f
                drawPath(Path().apply { moveTo(-3f, -5f); lineTo(3f, -5f); lineTo(6f + flattern, ende); lineTo(-6f + flattern, ende); close() }, Color(u).copy(alpha = alpha))
            }
            if (hocke) {
                drawPath(Path().apply { moveTo(0f, -5f); quadraticTo(6f, 2f, 2f, 9f) }, strich, style = Stroke(s, cap = StrokeCap.Round))
                drawPath(Path().apply { moveTo(2f, 9f); lineTo(-4f, 4f); lineTo(-2f, -2f) }, strich, style = Stroke(s, cap = StrokeCap.Round))
                drawLine(strich, Offset(0f, -3f), Offset(-5f, 3f), s, StrokeCap.Round)
                kopf(o, -1f, -9f, alpha)
                drawRect(hose, Offset(-2f, 6f), Size(6f, 4f))
            } else {
                drawLine(strich, Offset(0f, -6f), Offset(0f, 12f), s, StrokeCap.Round)
                drawLine(strich, Offset(0f, -4f), Offset(-4f, -14f), s, StrokeCap.Round)
                drawLine(strich, Offset(0f, -4f), Offset(4f, -14f), s, StrokeCap.Round)
                drawLine(strich, Offset(0f, 12f), Offset(-3f, 21f), s, StrokeCap.Round)
                drawLine(strich, Offset(0f, 12f), Offset(3f, 21f), s, StrokeCap.Round)
                kopf(o, 0f, -11f, alpha)
                drawRect(hose, Offset(-3f, 9f), Size(6f, 4f))
            }
        }
    }

    private fun DrawScope.kopf(o: Outfit, x: Float, y: Float, alpha: Float) {
        drawCircle(HAUT.copy(alpha = alpha), 5.5f, Offset(x, y))
        o.kappe?.let { drawArc(Color(it).copy(alpha = alpha), 180f, 180f, true, Offset(x - 5.8f, y - 5.8f), Size(11.6f, 11.6f)) }
        if (o.brille) {
            drawLine(Color(0xFF1F2937).copy(alpha = alpha), Offset(x - 5.5f, y - 0.5f), Offset(x + 5.5f, y - 0.5f), 1.2f)
            drawCircle(Color(0xFF38BDF8).copy(alpha = alpha), 1.8f, Offset(x + 2.2f, y - 0.5f))
        }
    }

    private fun DrawScope.kraftBalken(sp: SprungSpiel) {
        val f = sp.figur
        val w = size.width
        val h = size.height
        val x = w - 34 * f
        val oben = h * 0.2f
        val hoehe = h * 0.26f
        drawRoundRect(Color.White.copy(alpha = 0.85f), Offset(x - 4 * f, oben - 4 * f), Size(22 * f, hoehe + 8 * f), CornerRadius(8 * f))
        drawRect(Color(0xFF2E9E6B).copy(alpha = 0.35f), Offset(x, oben), Size(14 * f, hoehe * 0.1f))
        val wert = sp.anlaufWert
        val farbe = if (wert >= 0.93f) Color(0xFF2E9E6B) else if (wert >= 0.7f) Color(0xFFE8A93C) else Color(0xFFFF6B4A)
        drawRoundRect(farbe, Offset(x, oben + hoehe * (1 - wert)), Size(14 * f, hoehe * wert), CornerRadius(4 * f))
        schreiben("Kraft", x + 7 * f, oben + hoehe + 20 * f, 11 * f, TIEFE)
    }

    private fun DrawScope.ergebnisTafel(sp: SprungSpiel, e: SprungErgebnis) {
        val w = size.width
        val h = size.height
        val f = sp.figur
        val zeilen = sp.belohnungen.take(4)
        val oben = h * 0.16f
        val hoehe = (128 + zeilen.size * 18) * f
        drawRoundRect(Color.White.copy(alpha = 0.94f), Offset(w * 0.05f, oben), Size(w * 0.9f, hoehe), CornerRadius(14 * f))
        var y = oben + 26 * f
        schreiben(e.text, w / 2, y, w * 0.048f, TIEFE)
        // Kampfrichter: höchste und niedrigste Note fallen weg
        y += 14 * f
        val sortiert = e.noten.withIndex().sortedBy { it.value }
        val weg = setOf(sortiert.first().index, sortiert.last().index)
        val kw = 40 * f
        val start = w / 2 - (5 * kw + 4 * 6 * f) / 2
        e.noten.forEachIndexed { i, n ->
            val x = start + i * (kw + 6 * f)
            drawRoundRect(if (i in weg) Color(0xFFE6EEF1) else Color(0xFF0E7490), Offset(x, y), Size(kw, 26 * f), CornerRadius(6 * f))
            schreiben(zahl(n), x + kw / 2, y + 18 * f, 14 * f, if (i in weg) Color(0xFF8FAAB5) else Color.White)
        }
        y += 48 * f
        val teile = mutableListOf("${e.salti} Salt${if (e.salti == 1) "o" else "i"}")
        if (e.schrauben > 0) teile += "${e.schrauben} Schraube${if (e.schrauben == 1) "" else "n"}"
        teile += "Schwierigkeit ${zahl((e.schwierigkeit * 10).toInt() / 10f)}"
        schreiben(teile.joinToString(" · "), w / 2, y, w * 0.036f, TIEFE, fett = false)
        y += 28 * f
        schreiben("+${e.punkte} Punkte", w / 2, y, w * 0.06f, Color(0xFFFF6B4A))
        zeilen.forEach {
            y += 18 * f
            schreiben(it, w / 2, y, w * 0.036f, Color(0xFF0E7490))
        }
    }
}
