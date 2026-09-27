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

        Karte(titel = "Wie stark sind die Kopfschmerzen?") {
            Text(
                f.staerke.toString(), fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = Farben.staerke(f.staerke),
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
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
        Karte(titel = "Wann haben sie angefangen?") {
            val context = LocalContext.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    val t = runCatching { LocalTime.parse(f.beginn) }.getOrElse { LocalTime.now() }
                    TimePickerDialog(context, { _, h, m ->
                        vm.formularAendern { it.copy(beginn = "%02d:%02d".format(h, m)) }
                    }, t.hour, t.minute, true).show()
                }) { Text(if (f.beginn.isEmpty()) "Uhrzeit wählen" else "${f.beginn} Uhr") }
                if (f.beginn.isNotEmpty()) TextButton(onClick = { vm.formularAendern { it.copy(beginn = "") } }) { Text("leeren") }
            }
        }
        Karte(titel = "Wie fühlt sich der Schmerz an?") {
            Chips(Auswahl.ARTEN, { it in f.art }) { w -> vm.formularAendern { it.copy(art = it.art umschalten w) } }
        }
        Karte(titel = "Wie lange schon?") {
            Chips(Auswahl.DAUER, { it == f.dauer }) { w -> vm.formularAendern { it.copy(dauer = if (it.dauer == w) "" else w) } }
        }
        Karte(titel = "Was könnte der Auslöser sein?") {
            Chips(Auswahl.AUSLOESER, { it in f.ausloeser }) { w -> vm.formularAendern { it.copy(ausloeser = it.ausloeser umschalten w) } }
        }
        Karte(titel = "Medikament genommen?") {
            OutlinedTextField(
                value = f.medikament, onValueChange = { v -> vm.formularAendern { it.copy(medikament = v) } },
                placeholder = { Text("z. B. Ibuprofen 200 mg – oder leer lassen") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )
        }
        Karte(titel = "Sonst noch etwas?") {
            OutlinedTextField(
                value = f.notiz, onValueChange = { v -> vm.formularAendern { it.copy(notiz = v) } },
                placeholder = { Text("Notiz (freiwillig)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
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
        }

        fun speichern(frei: Boolean) {
            val fehler = vm.speichern(frei)
            if (fehler != null) {
                pflichtFehler = true
                meldung(fehler)
                scope.launch { scroll.animateScrollTo((pflichtY - 40).toInt().coerceAtLeast(0)) }
            } else {
                pflichtFehler = false
                scope.launch { scroll.scrollTo(0) }
            }
        }
        HauptKnopf("Eintrag speichern") { speichern(false) }
        OutlinedButton(
            onClick = { speichern(true) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary),
        ) {
            Text(if (f.datum == heuteIso) "Heute keine Kopfschmerzen 🎉" else "An diesem Tag keine Kopfschmerzen 🎉", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
    }
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
