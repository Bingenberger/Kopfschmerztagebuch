package de.kopfschmerztagebuch.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kopfschmerztagebuch.data.Auswahl
import de.kopfschmerztagebuch.data.tagesSerie
import de.kopfschmerztagebuch.spiel.bonusSpruenge
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val KURZ = DateTimeFormatter.ofPattern("EE, dd.MM.", Locale.GERMAN)

fun tagLabel(iso: String, heute: LocalDate): String {
    val d = LocalDate.parse(iso)
    val name = when (d) {
        heute -> "Heute"
        heute.minusDays(1) -> "Gestern"
        heute.minusDays(2) -> "Vorgestern"
        else -> return d.format(KURZ)
    }
    return "$name (${d.format(KURZ)})"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeuteScreen(vm: AppViewModel, meldung: (String) -> Unit) {
    val daten by vm.daten.collectAsStateWithLifecycle()
    val heute by vm.heute.collectAsStateWithLifecycle()
    val f by vm.formular.collectAsStateWithLifecycle()
    val offen by vm.formularOffen.collectAsStateWithLifecycle()
    val heuteIso = heute.toString()
    val eintragHeute = daten.eintraege[heuteIso]
    val zeigeFormular = offen || eintragHeute == null
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var pflichtY by remember { mutableFloatStateOf(0f) }
    var pflichtFehler by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val erinnerung = daten.erinnerung
        if (erinnerung != null && eintragHeute == null && LocalTime.now().toString().take(5) >= erinnerung) {
            Banner("Du hast heute noch keinen Eintrag gemacht. Trag kurz ein, wie es Dir geht!")
        }

        val fehlend = listOf(heute.minusDays(1), heute.minusDays(2)).map { it.toString() }.filter { it !in daten.eintraege }
        if (fehlend.isNotEmpty() && !(offen && f.datum != heuteIso)) {
            Karte(titel = "Vergessene Tage nachtragen") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fehlend.forEach { t ->
                        AssistChip(onClick = { vm.nachtragen(t); scope.launch { scroll.animateScrollTo(0) } }, label = { Text("✏️ " + tagLabel(t, heute)) })
                    }
                }
            }
        }

        if (!zeigeFormular) {
            val serie = tagesSerie(daten.eintraege, heute)
            Karte {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (eintragHeute!!.frei) "Super – heute keine Kopfschmerzen!" else "Eintrag gespeichert.",
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    )
                    Text(
                        if (eintragHeute.frei) "Der Sprungturm ist offen." else "Gute Besserung – und jetzt ab zum Sprungturm!",
                        textAlign = TextAlign.Center,
                    )
                    if (serie >= 2) {
                        Spacer(Modifier.height(8.dp))
                        val bonus = bonusSpruenge(serie)
                        Text(
                            "🔥 $serie Tage am Stück eingetragen" + if (bonus > 0) " – $bonus Bonussprünge!" else "",
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        )
                    }
                    if (eintragHeute.mitMedikament && (eintragHeute.wirkung.isEmpty() || eintragHeute.wirkung == Auswahl.WIRKUNG.last())) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "💊 Hat ${eintragHeute.medikamentText} geholfen?" + (eintragHeute.medikamentZeit.takeIf { it.isNotEmpty() }?.let { " (genommen um $it Uhr)" } ?: ""),
                            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(6.dp))
                        Chips(Auswahl.WIRKUNG.dropLast(1), { it == eintragHeute.wirkung }) { w -> vm.wirkungSetzen(heuteIso, w) }
                    }
                    Spacer(Modifier.height(16.dp))
                    HauptKnopf("Zum Sprungturm 🏊") { vm.tabWechseln(Tab.SPIEL) }
                    LeiserKnopf("Eintrag ändern") { vm.bearbeitenHeute() }
                }
            }
            return@Column
        }

        if (f.datum != heuteIso || f.bearbeiten) {
            Banner(
                (if (f.bearbeiten) "Du änderst den Eintrag für " else "Du trägst gerade nach für ") + tagLabel(f.datum, heute) + ".",
                aktion = "Abbrechen",
            ) { vm.formularAbbrechen(); pflichtFehler = false }
        }

        val context = LocalContext.current
        Karte(
            titel = if (f.datum == heuteIso) "Hattest Du heute Kopfschmerzen?" else "Hattest Du an diesem Tag Kopfschmerzen?",
            fehler = pflichtFehler && f.schmerzen == null,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                JaNeinKnopf("Ja 😣", f.schmerzen == true, Farben.Koralle, Modifier.weight(1f)) {
                    vm.formularAendern { it.copy(schmerzen = true) }
                }
                JaNeinKnopf("Nein 🎉", f.schmerzen == false, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f)) {
                    vm.formularAendern { it.copy(schmerzen = false) }
                }
            }
        }

        if (f.schmerzen == true) {
            Karte(titel = "Wie stark sind die Kopfschmerzen?") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(Auswahl.GESICHT[f.staerke], fontSize = 44.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(f.staerke.toString(), fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = Farben.staerke(f.staerke))
                }
                Text(
                    Auswahl.STAERKE_TEXT[f.staerke], color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                )
                Slider(
                    value = f.staerke.toFloat(), onValueChange = { v -> vm.formularAendern { it.copy(staerke = v.toInt()) } },
                    valueRange = 1f..10f, steps = 8,
                    colors = SliderDefaults.colors(thumbColor = Farben.staerke(f.staerke), activeTrackColor = Farben.staerke(f.staerke)),
                )
            }
            Karte(titel = "Wo tut es weh?") {
                Chips(Auswahl.ORTE, { it in f.ort }) { w -> vm.formularAendern { it.copy(ort = it.ort umschalten w) } }
            }
            Karte(titel = "Wie fühlt sich der Schmerz an?") {
                Chips(Auswahl.ARTEN, { it in f.art }) { w -> vm.formularAendern { it.copy(art = it.art umschalten w) } }
            }
            Karte(titel = "Wann haben sie angefangen – und wie lange dauern sie?") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { uhrzeitWaehlen(context, f.beginn) { z -> vm.formularAendern { it.copy(beginn = z) } } }) {
                        Text(if (f.beginn.isEmpty()) "Beginn: Uhrzeit wählen" else "Beginn: ${f.beginn} Uhr")
                    }
                    if (f.beginn.isNotEmpty()) TextButton(onClick = { vm.formularAendern { it.copy(beginn = "") } }) { Text("leeren") }
                }
                Spacer(Modifier.height(8.dp))
                Chips(Auswahl.DAUER, { it == f.dauer }) { w -> vm.formularAendern { it.copy(dauer = if (it.dauer == w) "" else w) } }
            }
            Karte(titel = "Was war sonst noch?") {
                Text("Alles antippen, was dazu passt", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                Chips(Auswahl.BEGLEIT, { it in f.begleit }) { w -> vm.formularAendern { it.copy(begleit = it.begleit umschalten w) } }
            }
            Karte(titel = "Was könnte der Auslöser sein?") {
                Chips(Auswahl.AUSLOESER, { it in f.ausloeser }) { w -> vm.formularAendern { it.copy(ausloeser = it.ausloeser umschalten w) } }
            }
            MedikamentKarte(f, daten.meineMedikamente, vm)
            Karte(titel = "Wie sehr hat es Dich gebremst?") {
                Chips(Auswahl.ALLTAG, { it == f.alltag }) { w -> vm.formularAendern { it.copy(alltag = if (it.alltag == w) "" else w) } }
            }
        }

        if (f.schmerzen != null) {
            Column(
                Modifier.onGloballyPositioned { pflichtY = it.positionInParent().y },
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Karte(titel = "Für jeden Tag: Bildschirmzeit", hervorgehoben = true, fehler = pflichtFehler && f.bildschirm.isEmpty()) {
                    Chips(Auswahl.BILDSCHIRM, { it == f.bildschirm }) { w -> vm.formularAendern { it.copy(bildschirm = w) } }
                }
                Karte(titel = "Für jeden Tag: Wie viel hast Du getrunken?", hervorgehoben = true, fehler = pflichtFehler && f.trinken.isEmpty()) {
                    Chips(Auswahl.TRINKEN, { it == f.trinken }) { w -> vm.formularAendern { it.copy(trinken = w) } }
                }
                Karte(titel = "Wie lange hast Du letzte Nacht geschlafen?") {
                    Chips(Auswahl.SCHLAF, { it == f.schlaf }) { w -> vm.formularAendern { it.copy(schlaf = if (it.schlaf == w) "" else w) } }
                }
            }
            Karte(titel = "Sonst noch etwas?") {
                OutlinedTextField(
                    value = f.notiz, onValueChange = { v -> vm.formularAendern { it.copy(notiz = v) } },
                    placeholder = { Text("Notiz (freiwillig)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            HauptKnopf("Eintrag speichern") {
                val fehler = vm.speichern()
                if (fehler != null) {
                    pflichtFehler = true
                    meldung(fehler)
                    scope.launch { scroll.animateScrollTo((pflichtY - 40).toInt().coerceAtLeast(0)) }
                } else {
                    pflichtFehler = false
                    scope.launch { scroll.scrollTo(0) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedikamentKarte(f: Formular, meine: List<String>, vm: AppViewModel) {
    val context = LocalContext.current
    Karte(titel = "Medikament genommen?") {
        // Gewählte Medikamente, die (nicht mehr) in der Liste stehen, trotzdem anzeigen
        val alle = meine + f.medikamente.filter { it !in meine }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuswahlChip("keins", !f.mitMedikament && !f.medikamentAnders) {
                vm.formularAendern { it.copy(medikamente = emptySet(), medikamentAnders = false, medikament = "", medikamentZeit = "", wirkung = "") }
            }
            alle.forEach { m ->
                AuswahlChip("💊 $m", m in f.medikamente) {
                    vm.formularAendern {
                        val neu = it.medikamente umschalten m
                        it.copy(medikamente = neu, medikamentZeit = it.medikamentZeit.ifEmpty { jetzt() })
                    }
                }
            }
            AuswahlChip("anderes …", f.medikamentAnders) {
                vm.formularAendern { it.copy(medikamentAnders = !it.medikamentAnders, medikamentZeit = it.medikamentZeit.ifEmpty { jetzt() }) }
            }
        }
        if (f.medikamentAnders) {
            OutlinedTextField(
                value = f.medikament, onValueChange = { v -> vm.formularAendern { it.copy(medikament = v) } },
                placeholder = { Text("Name und Menge, z. B. Nasenspray") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )
        }
        if (f.mitMedikament) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { uhrzeitWaehlen(context, f.medikamentZeit) { z -> vm.formularAendern { it.copy(medikamentZeit = z) } } }) {
                Text(if (f.medikamentZeit.isEmpty()) "Wann genommen? Uhrzeit wählen" else "Genommen um ${f.medikamentZeit} Uhr")
            }
            Spacer(Modifier.height(8.dp))
            Text("Hat es geholfen?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Chips(Auswahl.WIRKUNG, { it == f.wirkung }) { w -> vm.formularAendern { it.copy(wirkung = if (it.wirkung == w) "" else w) } }
        }
    }
}

@Composable
private fun AuswahlChip(text: String, an: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = an, onClick = onClick,
        label = { Text(text, fontWeight = if (an) FontWeight.SemiBold else FontWeight.Normal) },
        shape = RoundedCornerShape(50),
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}

@Composable
private fun JaNeinKnopf(text: String, gewaehlt: Boolean, farbe: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    if (gewaehlt) {
        Button(
            onClick = onClick, modifier = modifier.height(56.dp), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = farbe, contentColor = androidx.compose.ui.graphics.Color.White),
        ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
    } else {
        OutlinedButton(
            onClick = onClick, modifier = modifier.height(56.dp), shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, farbe),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = farbe),
        ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
    }
}

private fun jetzt() = LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

private fun uhrzeitWaehlen(context: android.content.Context, aktuell: String, gewaehlt: (String) -> Unit) {
    val t = runCatching { LocalTime.parse(aktuell) }.getOrElse { LocalTime.now() }
    TimePickerDialog(context, { _, h, m -> gewaehlt("%02d:%02d".format(h, m)) }, t.hour, t.minute, true).show()
}

private infix fun Set<String>.umschalten(w: String) = if (w in this) this - w else this + w

@Composable
fun HauptKnopf(text: String, modifier: Modifier = Modifier, aktiv: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = aktiv,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

@Composable
fun LeiserKnopf(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(top = 10.dp).height(48.dp),
        shape = RoundedCornerShape(12.dp),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun Banner(text: String, aktion: String? = null, onAktion: () -> Unit = {}) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, modifier = Modifier.weight(1f), fontSize = 14.sp)
            if (aktion != null) TextButton(onClick = onAktion, modifier = Modifier.widthIn(min = 0.dp)) { Text(aktion) }
        }
    }
}
