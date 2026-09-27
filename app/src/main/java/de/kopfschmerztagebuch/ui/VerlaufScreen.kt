package de.kopfschmerztagebuch.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kopfschmerztagebuch.data.Auswertung
import de.kopfschmerztagebuch.data.Eintrag
import de.kopfschmerztagebuch.data.SCHMERZMITTEL_GRENZE
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val KURZ = DateTimeFormatter.ofPattern("EE, dd.MM.", Locale.GERMAN)

fun details(e: Eintrag): String = if (e.frei) {
    listOfNotNull("schmerzfrei", e.bildschirm.ifEmpty { null }?.let { "Bildschirm: $it" }, e.trinken.ifEmpty { null }?.let { "getrunken: $it" })
        .joinToString(" · ")
} else {
    listOf(
        e.beginn.ifEmpty { null }?.let { "ab $it Uhr" }, e.dauer, e.art.joinToString("/"), e.ort.joinToString(", "),
        e.begleit.joinToString(", "), e.medikamentText.ifEmpty { null }?.let { "💊 $it" },
    )
        .filter { !it.isNullOrEmpty() }.joinToString(" · ")
} + if (e.nachgetragen) " · nachgetragen" else ""

@Composable
fun VerlaufScreen(vm: AppViewModel, meldung: (String) -> Unit) {
    val daten by vm.daten.collectAsStateWithLifecycle()
    val heute by vm.heute.collectAsStateWithLifecycle()
    var auswahl by remember { mutableStateOf<String?>(null) }
    var alleZeigen by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val a = Auswertung.berechnen(daten.eintraege, heute, 30)
        Karte(titel = "Letzte 30 Tage") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Kennzahl("${a.mitSchmerzen}", "mit Kopf-\nschmerzen", Modifier.weight(1f))
                Kennzahl("${a.schmerzfrei}", "schmerzfrei", Modifier.weight(1f))
                Kennzahl(a.durchschnitt?.let { "%.1f".format(Locale.GERMAN, it) } ?: "–", "Ø Stärke", Modifier.weight(1f))
                Kennzahl("${a.mitMedikament}", "Schmerz-\nmittel-Tage", Modifier.weight(1f))
            }
            if (a.mitMedikament >= SCHMERZMITTEL_GRENZE) {
                Spacer(Modifier.height(10.dp))
                Banner(
                    "An ${a.mitMedikament} der letzten 30 Tage gab es Schmerzmittel. Das bitte mit der Kinderärztin oder dem Kinderarzt " +
                        "besprechen: Werden Schmerzmittel an mehr als etwa 10 Tagen im Monat genommen, können sie selbst Kopfschmerzen auslösen.",
                )
            }
            Spacer(Modifier.height(14.dp))
            StaerkeDiagramm(daten.eintraege, heute)
            if (a.haeufigsteBegleit.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Häufigste Begleitsymptome: " + a.haeufigsteBegleit.joinToString { "${it.first} (${it.second}×)" },
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (a.eingeschraenkt > 0) {
                Text(
                    "Alltag eingeschränkt an ${a.eingeschraenkt} Tagen",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (a.haeufigsteAusloeser.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Häufigste Auslöser: " + a.haeufigsteAusloeser.joinToString { "${it.first} (${it.second}×)" },
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Karte(titel = "Einträge") {
            val tage = daten.eintraege.keys.sortedDescending()
            if (tage.isEmpty()) {
                Text("Noch keine Einträge.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
            }
            val sichtbar = if (alleZeigen) tage else tage.take(30)
            sichtbar.forEachIndexed { i, t ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                EintragZeile(t, daten.eintraege.getValue(t)) { auswahl = t }
            }
            if (tage.size > sichtbar.size) {
                TextButton(onClick = { alleZeigen = true }) { Text("Alle ${tage.size} Einträge zeigen") }
            }
        }

        ExportKarte(vm, meldung)
    }

    auswahl?.let { t ->
        val e = daten.eintraege[t]
        if (e == null) { auswahl = null; return@let }
        var loeschenFragen by remember(t) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { auswahl = null },
            title = { Text(LocalDate.parse(t).format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN))) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (loeschenFragen) {
                        Text("Diesen Eintrag wirklich löschen?")
                    } else if (e.frei) {
                        Zeile("Kopfschmerzen", "keine 🎉")
                    } else {
                        Zeile("Stärke", "${e.staerke} – ${de.kopfschmerztagebuch.data.Auswahl.STAERKE_TEXT[e.staerke]}")
                        Zeile("Beginn", e.beginn.ifEmpty { "–" }.let { if (it == "–") it else "$it Uhr" })
                        Zeile("Dauer", e.dauer.ifEmpty { "–" })
                        Zeile("Art", e.art.joinToString(", ").ifEmpty { "–" })
                        Zeile("Ort", e.ort.joinToString(", ").ifEmpty { "–" })
                        Zeile("Begleitsymptome", e.begleit.joinToString(", ").ifEmpty { "–" })
                        Zeile("Auslöser", e.ausloeser.joinToString(", ").ifEmpty { "–" })
                        Zeile(
                            "Medikament",
                            e.medikamentText.ifEmpty { "–" } +
                                (e.medikamentZeit.takeIf { it.isNotEmpty() }?.let { ", um $it Uhr" } ?: "") +
                                (e.wirkung.takeIf { it.isNotEmpty() }?.let { " – $it" } ?: ""),
                        )
                        Zeile("Alltag", e.alltag.ifEmpty { "–" })
                    }
                    if (!loeschenFragen) {
                        Zeile("Bildschirmzeit", e.bildschirm.ifEmpty { "–" })
                        Zeile("Getrunken", e.trinken.ifEmpty { "–" })
                        if (e.schlaf.isNotEmpty()) Zeile("Schlaf", e.schlaf)
                        if (e.notiz.isNotEmpty()) Zeile("Notiz", e.notiz)
                        Zeile("Eingetragen", (e.zeit.ifEmpty { "–" }) + if (e.nachgetragen) " (nachgetragen)" else "")
                    }
                }
            },
            confirmButton = {
                if (loeschenFragen) TextButton(onClick = { vm.eintragLoeschen(t); auswahl = null }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                else TextButton(onClick = { auswahl = null; vm.nachtragen(t) }) { Text("Bearbeiten") }
            },
            dismissButton = {
                if (loeschenFragen) TextButton(onClick = { loeschenFragen = false }) { Text("Abbrechen") }
                else Row {
                    TextButton(onClick = { loeschenFragen = true }) { Text("Löschen") }
                    TextButton(onClick = { auswahl = null }) { Text("Schließen") }
                }
            },
        )
    }
}

@Composable
private fun Zeile(name: String, wert: String) {
    Row {
        Text(name, modifier = Modifier.width(110.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(wert, fontSize = 14.sp)
    }
}

@Composable
private fun Kennzahl(wert: String, name: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(wert, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 14.sp)
    }
}

@Composable
private fun EintragZeile(t: String, e: Eintrag, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (e.frei) Farben.Frei else Farben.staerke(e.staerke)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (e.frei) "✓" else e.staerke.toString(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(LocalDate.parse(t).format(KURZ), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(details(e).ifEmpty { "–" }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Säulen für die Schmerzstärke der letzten 30 Tage (eine Farbe, Höhe = Stärke).
 * Schmerzfreie Tage als grüner Punkt auf der Grundlinie, fehlende Tage als kurzer grauer Strich.
 * Antippen zeigt den Wert des Tages.
 */
@Composable
private fun StaerkeDiagramm(eintraege: Map<String, Eintrag>, heute: LocalDate) {
    val tage = (29 downTo 0).map { heute.minusDays(it.toLong()) }
    var gewaehlt by remember { mutableStateOf<Int?>(null) }
    val balken = MaterialTheme.colorScheme.primary
    val gitter = MaterialTheme.colorScheme.outline
    val leer = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val markierung = MaterialTheme.colorScheme.onSurface
    val frei = MaterialTheme.colorScheme.tertiary

    val info = gewaehlt?.let { i ->
        val d = tage[i]
        val e = eintraege[d.toString()]
        d.format(KURZ) + ": " + when {
            e == null -> "kein Eintrag"
            e.frei -> "schmerzfrei"
            else -> "Stärke ${e.staerke}"
        }
    } ?: "Tippe auf einen Tag für Details."
    Text(info, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    Row {
        Column(Modifier.height(120.dp).padding(end = 4.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("10", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("5", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("", fontSize = 10.sp)
        }
        Canvas(
            Modifier.weight(1f).height(120.dp).pointerInput(Unit) {
                detectTapGestures { p ->
                    val i = (p.x / (size.width / 30f)).toInt().coerceIn(0, 29)
                    gewaehlt = if (gewaehlt == i) null else i
                }
            },
        ) {
            val basis = size.height - 8.dp.toPx()
            val spalte = size.width / 30f
            val breite = (spalte - 2.dp.toPx()).coerceAtLeast(2f)
            val proPunkt = (basis - 6.dp.toPx()) / 10f
            listOf(5, 10).forEach { v ->
                drawLine(gitter, Offset(0f, basis - v * proPunkt), Offset(size.width, basis - v * proPunkt), strokeWidth = 1f)
            }
            drawLine(gitter, Offset(0f, basis), Offset(size.width, basis), strokeWidth = 1.5f)
            val r = 4.dp.toPx().coerceAtMost(breite / 2)
            tage.forEachIndexed { i, d ->
                val x = i * spalte + (spalte - breite) / 2
                val e = eintraege[d.toString()]
                when {
                    e == null -> drawLine(leer, Offset(x + breite / 2, basis + 3.dp.toPx()), Offset(x + breite / 2, basis + 6.dp.toPx()), strokeWidth = 2.dp.toPx())
                    e.frei -> drawCircle(frei, radius = (breite / 2).coerceAtMost(3.5.dp.toPx()), center = Offset(x + breite / 2, basis - 4.dp.toPx()))
                    else -> {
                        val h = e.staerke * proPunkt
                        // oben abgerundet, unten gerade auf der Grundlinie
                        val p = Path().apply {
                            moveTo(x, basis)
                            lineTo(x, basis - h + r)
                            quadraticTo(x, basis - h, x + r, basis - h)
                            lineTo(x + breite - r, basis - h)
                            quadraticTo(x + breite, basis - h, x + breite, basis - h + r)
                            lineTo(x + breite, basis)
                            close()
                        }
                        drawPath(p, balken)
                    }
                }
                if (gewaehlt == i) {
                    drawRoundRect(markierung.copy(alpha = 0.12f), Offset(i * spalte, 0f), Size(spalte, basis), CornerRadius(4.dp.toPx()))
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("vor 30 Tagen", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 18.dp))
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(frei))
        Text(" schmerzfrei   ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("heute", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExportKarte(vm: AppViewModel, meldung: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val csvSpeichern = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { runCatching { vm.exportNach("csv", uri) }.onSuccess { meldung("CSV gespeichert.") }.onFailure { meldung("Speichern fehlgeschlagen.") } }
    }
    val pdfSpeichern = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch { runCatching { vm.exportNach("pdf", uri) }.onSuccess { meldung("PDF gespeichert.") }.onFailure { meldung("Speichern fehlgeschlagen.") } }
    }
    fun teilen(art: String, mime: String) = scope.launch {
        val f = vm.exportDatei(art)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".dateien", f)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Kopfschmerz-Tagebuch")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Tagebuch teilen"))
    }

    Karte(titel = "Für den Arzt exportieren") {
        Text(
            "PDF-Bericht mit Zusammenfassung und allen Einträgen, oder CSV-Tabelle (öffnet sich in Excel).",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text("PDF-Bericht", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            OutlinedButton(onClick = { teilen("pdf", "application/pdf") }, modifier = Modifier.weight(1f)) { Text("Teilen …") }
            OutlinedButton(onClick = { pdfSpeichern.launch(vm.dateiname("pdf")) }, modifier = Modifier.weight(1f)) { Text("Speichern") }
        }
        Spacer(Modifier.height(8.dp))
        Text("CSV-Tabelle", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            OutlinedButton(onClick = { teilen("csv", "text/csv") }, modifier = Modifier.weight(1f)) { Text("Teilen …") }
            OutlinedButton(onClick = { csvSpeichern.launch(vm.dateiname("csv")) }, modifier = Modifier.weight(1f)) { Text("Speichern") }
        }
    }
}
