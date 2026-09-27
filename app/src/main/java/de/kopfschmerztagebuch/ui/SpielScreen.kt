package de.kopfschmerztagebuch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.MobileOff
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kopfschmerztagebuch.spiel.ABZEICHEN
import de.kopfschmerztagebuch.spiel.Klang
import de.kopfschmerztagebuch.spiel.OUTFITS
import de.kopfschmerztagebuch.spiel.Phase
import de.kopfschmerztagebuch.spiel.TUERME
import de.kopfschmerztagebuch.spiel.Zeichner
import de.kopfschmerztagebuch.spiel.tageszeit
import kotlin.math.abs
import java.time.LocalTime

@Composable
fun SpielScreen(vm: AppViewModel) {
    val daten by vm.daten.collectAsStateWithLifecycle()
    val heute by vm.heute.collectAsStateWithLifecycle()
    if (daten.eintraege[heute.toString()] == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Karte {
                Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔒", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Der Sprungturm ist noch zu.", fontWeight = FontWeight.Bold)
                    Text("Mach erst Deinen Tageseintrag, dann darfst Du springen!", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    HauptKnopf("Zum Tageseintrag 📝") { vm.tabWechseln(Tab.HEUTE) }
                }
            }
        }
        return
    }

    val spiel = vm.spiel
    val stand = daten.spiel
    val context = LocalContext.current
    val klang = remember { Klang(context) }
    DisposableEffect(Unit) { onDispose { klang.freigeben(); spiel.haelt = false } }
    val zeichner = remember { Zeichner() }
    var frame by remember { mutableLongStateOf(0L) }
    var phase by remember { mutableStateOf(spiel.phase) }
    var sammlung by remember { mutableStateOf(false) }
    val aktuellerStand by rememberUpdatedState(stand)

    LaunchedEffect(Unit) {
        var zuletzt = 0L
        while (true) {
            withFrameNanos { t ->
                val dt = if (zuletzt == 0L) 0.016 else ((t - zuletzt) / 1e9).coerceIn(0.0, 0.033)
                zuletzt = t
                spiel.schritt(dt)?.let { e ->
                    vm.punkteHeute += e.punkte
                    spiel.belohnen(vm.sprungVerbuchen(e))
                }
                while (spiel.ereignisse.isNotEmpty()) {
                    klang.spielen(spiel.ereignisse.removeFirst(), aktuellerStand.ton, aktuellerStand.vibration)
                }
                phase = spiel.phase
                frame = t
            }
        }
    }

    val freie = TUERME.count { stand.gesamt >= it.punkteNoetig }
    if (spiel.turmIdx >= freie) spiel.turmWaehlen(0)
    val uebrig = vm.spruengeUebrig()

    fun anlaufOderHinweis() {
        if (vm.sprungZaehlen()) spiel.anlaufStarten()
        else spiel.hinweis("Heute keine Sprünge mehr – bis morgen! 🌙")
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Heute: ${vm.punkteHeute} Punkte", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    if (stand.rekord > 0) "Rekord: ${stand.rekord}" + if (stand.rekordDatum.isNotEmpty()) " (${stand.rekordDatum.substring(8, 10)}.${stand.rekordDatum.substring(5, 7)}.)" else ""
                    else "Rekord: –",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { sammlung = true }) { Icon(Icons.Outlined.EmojiEvents, "Abzeichen und Outfits") }
            IconButton(onClick = { vm.spielEinstellung(ton = !stand.ton) }) {
                Icon(if (stand.ton) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff, if (stand.ton) "Ton aus" else "Ton an")
            }
            IconButton(onClick = { vm.spielEinstellung(vibration = !stand.vibration) }) {
                Icon(if (stand.vibration) Icons.Outlined.Vibration else Icons.Outlined.MobileOff, if (stand.vibration) "Vibration aus" else "Vibration an")
            }
        }

        val aufgabe = vm.aufgabeHeute()
        Surface(
            color = if (vm.aufgabeGeschafft()) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (vm.aufgabeGeschafft()) "✅ Tagesaufgabe geschafft!" else "🎯 Tagesaufgabe: ${aufgabe.text} (+${aufgabe.bonus})",
                fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TUERME.forEachIndexed { i, t ->
                val frei = i < freie
                FilterChip(
                    selected = i == spiel.turmIdx,
                    onClick = {
                        if (frei) spiel.turmWaehlen(i)
                        else spiel.hinweis("Noch ${t.punkteNoetig - stand.gesamt} Punkte bis ${t.name}!")
                    },
                    label = { Text(if (frei) t.name + if (t.klippe) " 🏔️" else "" else "🔒 ${t.name}") },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    val schwelle = 36.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        when (spiel.phase) {
                            Phase.BEREIT -> anlaufOderHinweis()
                            Phase.ANLAUF -> spiel.abspringen()
                            Phase.FLUG -> spiel.haelt = true
                            else -> {}
                        }
                        var gewischt = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            val d = c.position - down.position
                            if (!gewischt && spiel.phase == Phase.FLUG && abs(d.x) > schwelle && abs(d.x) > abs(d.y)) {
                                gewischt = true
                                spiel.haelt = false
                                spiel.schraubeAusloesen()
                            }
                            c.consume()
                            if (!c.pressed) break
                        }
                        spiel.haelt = false
                    }
                },
        ) {
            frame // bei jedem Frame neu zeichnen
            spiel.layout(size.width, size.height)
            with(zeichner) { malen(spiel, OUTFITS[stand.outfit.coerceIn(OUTFITS.indices)], tageszeit(LocalTime.now().hour), stand.gesamt) }
        }

        Text(
            when (phase) {
                Phase.BEREIT -> "Tippe aufs Bild oder auf „Springen“. Im Flug: Finger halten = Salto, loslassen = strecken, zur Seite wischen = Schraube."
                Phase.ANLAUF -> "Jetzt tippen, wenn der Kraft-Balken ganz oben ist!"
                Phase.FLUG -> "Halten = Salto · Wischen = Schraube · Vor dem Wasser strecken!"
                Phase.WASSER -> "Platsch!"
                Phase.FERTIG -> "Höchste und niedrigste Note fallen weg – wie bei Olympia."
            },
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 15.sp,
            modifier = Modifier.padding(vertical = 6.dp).heightIn(min = 30.dp),
        )
        val bonus = vm.spruengeMax() - 10
        HauptKnopf(
            when {
                phase == Phase.ANLAUF -> "Absprung! 🦘"
                phase == Phase.FLUG || phase == Phase.WASSER -> "… im Flug!"
                uebrig <= 0 -> "Heute keine Sprünge mehr – bis morgen! 🌙"
                else -> (if (phase == Phase.FERTIG) "Nochmal springen 🤸" else "Springen! 🤸") + " (noch $uebrig${if (bonus > 0) ", davon $bonus Bonus" else ""})"
            },
            aktiv = phase == Phase.ANLAUF || ((phase == Phase.BEREIT || phase == Phase.FERTIG) && uebrig > 0),
        ) {
            when (phase) {
                Phase.BEREIT -> anlaufOderHinweis()
                Phase.ANLAUF -> spiel.abspringen()
                Phase.FERTIG -> { spiel.zuruecksetzen(); anlaufOderHinweis() }
                else -> {}
            }
        }
    }

    if (sammlung) SammlungDialog(vm) { sammlung = false }
}

@Composable
private fun SammlungDialog(vm: AppViewModel, schliessen: () -> Unit) {
    val daten by vm.daten.collectAsStateWithLifecycle()
    val stand = daten.spiel
    AlertDialog(
        onDismissRequest = schliessen,
        confirmButton = { TextButton(onClick = schliessen) { Text("Fertig") } },
        title = { Text("Deine Sammlung") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Gesamt: ${stand.gesamt} Punkte · ${stand.sprungZahl} Sprünge", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("Outfit", fontWeight = FontWeight.Bold)
                OUTFITS.forEachIndexed { i, o ->
                    val frei = stand.gesamt >= o.punkteNoetig
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .then(if (i == stand.outfit) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
                            .clickable(enabled = frei) { vm.spielEinstellung(outfit = i) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (frei) Color(o.hose) else Color.LightGray))
                        Spacer(Modifier.width(10.dp))
                        Text(if (frei) o.name else "🔒 ${o.name} – ab ${o.punkteNoetig} Punkten", color = if (frei) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Abzeichen (${stand.abzeichen.size}/${ABZEICHEN.size})", fontWeight = FontWeight.Bold)
                ABZEICHEN.forEach { a ->
                    val hat = a.id in stand.abzeichen
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (hat) a.emoji else "❔", fontSize = 22.sp, modifier = Modifier.width(36.dp))
                        Column {
                            Text(a.titel, fontWeight = FontWeight.SemiBold, color = if (hat) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(a.text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
    )
}
