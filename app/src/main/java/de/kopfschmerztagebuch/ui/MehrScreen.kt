package de.kopfschmerztagebuch.ui

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kopfschmerztagebuch.erinnerung.Erinnerung
import kotlinx.coroutines.launch
import java.time.LocalTime

@Composable
fun MehrScreen(vm: AppViewModel, meldung: (String) -> Unit) {
    val daten by vm.daten.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var gewaehlteZeit by remember { mutableStateOf(daten.erinnerung ?: "18:00") }
    var frage by remember { mutableStateOf<Frage?>(null) }

    val erlaubnis = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { vm.erinnerungSetzen(gewaehlteZeit); meldung("Erinnerung täglich um $gewaehlteZeit Uhr.") }
        else meldung("Benachrichtigungen wurden nicht erlaubt. Du kannst sie in den Android-Einstellungen der App einschalten.")
    }
    fun einschalten() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Erinnerung.darfBenachrichtigen(context)) {
            erlaubnis.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.erinnerungSetzen(gewaehlteZeit)
            meldung("Erinnerung täglich um $gewaehlteZeit Uhr.")
        }
    }

    val sicherungSpeichern = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { vm.exportNach("json", uri) }
                .onSuccess { meldung("Sicherung gespeichert.") }.onFailure { meldung("Speichern fehlgeschlagen.") }
        }
    }
    val sicherungLaden = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) frage = Frage(
            "Sicherung wiederherstellen?",
            "Alle Einträge und der Spielstand auf diesem Gerät werden durch die Sicherung ersetzt.",
            "Wiederherstellen",
        ) { scope.launch { meldung(runCatching { vm.importieren(uri, ersetzen = true) }.getOrElse { "Die Datei konnte nicht gelesen werden." }) } }
    }
    val csvLaden = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { meldung(runCatching { vm.importieren(uri, ersetzen = false) }.getOrElse { "Die Datei konnte nicht gelesen werden." }) }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Karte(titel = "Tägliche Erinnerung") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (daten.erinnerung != null) "Eingeschaltet" else "Ausgeschaltet", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (daten.erinnerung != null) "täglich um ${daten.erinnerung} Uhr – nur wenn der Eintrag noch fehlt" else "Keine Erinnerung",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = daten.erinnerung != null, onCheckedChange = { an -> if (an) einschalten() else vm.erinnerungSetzen(null) })
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                val t = LocalTime.parse(gewaehlteZeit)
                TimePickerDialog(context, { _, h, m ->
                    gewaehlteZeit = "%02d:%02d".format(h, m)
                    if (daten.erinnerung != null) vm.erinnerungSetzen(gewaehlteZeit)
                }, t.hour, t.minute, true).show()
            }) { Text("Uhrzeit: $gewaehlteZeit Uhr") }
            Text(
                "Die Erinnerung kommt auch, wenn die App geschlossen ist.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
            )
        }

        Karte(titel = "Datensicherung") {
            Text(
                "Alle Einträge bleiben nur auf diesem Gerät gespeichert – die App hat nicht einmal Zugriff aufs Internet. " +
                    "Speichere ab und zu eine Sicherung (z. B. vor einem Handywechsel).",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LeiserKnopf("Sicherung speichern") { sicherungSpeichern.launch(vm.dateiname("json")) }
            LeiserKnopf("Sicherung wiederherstellen") { sicherungLaden.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
        }

        Karte(titel = "Aus der Web-Version übernehmen") {
            Text(
                "In der bisherigen Browser-Version unter „Verlauf“ auf „Als CSV für den Arzt exportieren“ tippen und die Datei hier einlesen. " +
                    "Tage, die es hier schon gibt, bleiben unverändert.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LeiserKnopf("CSV-Datei einlesen") { csvLaden.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream", "*/*")) }
        }

        Karte(titel = "Daten löschen") {
            LeiserKnopf("Alle Einträge löschen") {
                frage = Frage("Alle Einträge löschen?", "Das lässt sich nicht rückgängig machen. Der Spielstand bleibt erhalten.", "Löschen") {
                    vm.allesLoeschen(); meldung("Alle Einträge gelöscht.")
                }
            }
        }
    }

    frage?.let { q ->
        AlertDialog(
            onDismissRequest = { frage = null },
            title = { Text(q.titel) },
            text = { Text(q.text) },
            confirmButton = { TextButton(onClick = { frage = null; q.ok() }) { Text(q.knopf, color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { frage = null }) { Text("Abbrechen") } },
        )
    }
}

private class Frage(val titel: String, val text: String, val knopf: String, val ok: () -> Unit)
