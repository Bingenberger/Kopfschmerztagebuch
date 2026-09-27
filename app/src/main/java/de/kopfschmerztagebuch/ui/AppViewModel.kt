package de.kopfschmerztagebuch.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.kopfschmerztagebuch.data.Auswahl
import de.kopfschmerztagebuch.data.Csv
import de.kopfschmerztagebuch.data.Daten
import de.kopfschmerztagebuch.data.Eintrag
import de.kopfschmerztagebuch.data.JSON
import de.kopfschmerztagebuch.data.PdfBericht
import de.kopfschmerztagebuch.data.Speicher
import de.kopfschmerztagebuch.data.tagesSerie
import de.kopfschmerztagebuch.erinnerung.Erinnerung
import de.kopfschmerztagebuch.spiel.ABZEICHEN
import de.kopfschmerztagebuch.spiel.Bewertung
import de.kopfschmerztagebuch.spiel.OUTFITS
import de.kopfschmerztagebuch.spiel.SprungErgebnis
import de.kopfschmerztagebuch.spiel.SprungSpiel
import de.kopfschmerztagebuch.spiel.TUERME
import de.kopfschmerztagebuch.spiel.spruengeProTag
import de.kopfschmerztagebuch.spiel.tagesaufgabe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class Tab { HEUTE, VERLAUF, SPIEL, MEHR }

/** Eingaben im Formular – im ViewModel, damit sie beim Tab-Wechsel erhalten bleiben. */
data class Formular(
    val datum: String,
    /** Kopfschmerzen ja/nein – null, solange noch nicht beantwortet. */
    val schmerzen: Boolean? = null,
    val staerke: Int = 5,
    val ort: Set<String> = emptySet(),
    val beginn: String = "",
    val art: Set<String> = emptySet(),
    val dauer: String = "",
    val begleit: Set<String> = emptySet(),
    val ausloeser: Set<String> = emptySet(),
    /** Aus „Meine Medikamente“ gewählt. */
    val medikamente: Set<String> = emptySet(),
    /** Freitextfeld „anderes Medikament“ sichtbar. */
    val medikamentAnders: Boolean = false,
    val medikament: String = "",
    val medikamentZeit: String = "",
    val wirkung: String = "",
    val alltag: String = "",
    val notiz: String = "",
    val bildschirm: String = "",
    val trinken: String = "",
    val schlaf: String = "",
    /** true, wenn ein bestehender Eintrag bearbeitet wird. */
    val bearbeiten: Boolean = false,
) {
    val mitMedikament: Boolean get() = medikamente.isNotEmpty() || (medikamentAnders && medikament.isNotBlank())

    companion object {
        fun aus(datum: String, e: Eintrag) = Formular(
            datum = datum, schmerzen = !e.frei, staerke = if (e.frei) 5 else e.staerke, ort = e.ort.toSet(), beginn = e.beginn,
            art = e.art.toSet(), dauer = e.dauer, begleit = e.begleit.toSet(), ausloeser = e.ausloeser.toSet(),
            medikamente = e.medikamente.toSet(), medikamentAnders = e.medikament.isNotBlank(), medikament = e.medikament,
            medikamentZeit = e.medikamentZeit, wirkung = e.wirkung, alltag = e.alltag,
            notiz = e.notiz, bildschirm = e.bildschirm, trinken = e.trinken, schlaf = e.schlaf, bearbeiten = true,
        )
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val speicher = Speicher.von(app)
    val daten: StateFlow<Daten> = speicher.daten

    private val _heute = MutableStateFlow(LocalDate.now())
    val heute: StateFlow<LocalDate> = _heute.asStateFlow()

    private val _tab = MutableStateFlow(Tab.HEUTE)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _formular = MutableStateFlow(Formular(LocalDate.now().toString()))
    val formular: StateFlow<Formular> = _formular.asStateFlow()

    /** Formular sichtbar, obwohl für heute schon ein Eintrag existiert („Eintrag ändern“). */
    private val _formularOffen = MutableStateFlow(false)
    val formularOffen: StateFlow<Boolean> = _formularOffen.asStateFlow()

    val spiel = SprungSpiel()
    /** Punkte der aktuellen Spielrunde (seit App-Start). */
    var punkteHeute by mutableIntStateOf(0)

    fun datumPruefen() {
        val jetzt = LocalDate.now()
        if (jetzt != _heute.value) {
            val alt = _heute.value.toString()
            _heute.value = jetzt
            if (_formular.value.datum == alt && !_formular.value.bearbeiten) _formular.value = _formular.value.copy(datum = jetzt.toString())
            _formularOffen.value = false
        }
    }

    fun tabWechseln(t: Tab) { _tab.value = t }

    // ---------- Formular ----------

    fun formularAendern(f: (Formular) -> Formular) { _formular.value = f(_formular.value) }

    fun nachtragen(datum: String) {
        _formular.value = daten.value.eintraege[datum]?.let { Formular.aus(datum, it) } ?: Formular(datum)
        _formularOffen.value = true
        _tab.value = Tab.HEUTE
    }

    fun bearbeitenHeute() = nachtragen(_heute.value.toString())

    fun formularAbbrechen() {
        _formular.value = Formular(_heute.value.toString())
        _formularOffen.value = false
    }

    /** Speichert den Eintrag. Gibt eine Fehlermeldung zurück, wenn Pflichtfelder fehlen. */
    fun speichern(): String? {
        val f = _formular.value
        if (f.schmerzen == null) return "Hattest Du Kopfschmerzen? Bitte oben „Ja“ oder „Nein“ antippen."
        if (f.bildschirm.isEmpty() || f.trinken.isEmpty()) return "Bitte noch Bildschirmzeit und Trinkmenge auswählen – die gehören zu jedem Tag dazu."
        val heute = _heute.value.toString()
        val alt = daten.value.eintraege[f.datum]
        val e = if (!f.schmerzen) {
            Eintrag(frei = true, bildschirm = f.bildschirm, trinken = f.trinken, schlaf = f.schlaf, notiz = f.notiz.trim())
        } else {
            val mitMedikament = f.mitMedikament
            Eintrag(
                frei = false, staerke = f.staerke, beginn = f.beginn, art = Auswahlreihenfolge.art(f.art), ort = Auswahlreihenfolge.ort(f.ort),
                dauer = f.dauer, begleit = Auswahlreihenfolge.begleit(f.begleit), ausloeser = Auswahlreihenfolge.ausloeser(f.ausloeser),
                medikamente = daten.value.meineMedikamente.filter { it in f.medikamente } + f.medikamente.filter { it !in daten.value.meineMedikamente },
                medikament = if (f.medikamentAnders) f.medikament.trim() else "",
                medikamentZeit = if (mitMedikament) f.medikamentZeit else "",
                wirkung = if (mitMedikament) f.wirkung else "",
                alltag = f.alltag, notiz = f.notiz.trim(), bildschirm = f.bildschirm, trinken = f.trinken, schlaf = f.schlaf,
            )
        }.copy(
            zeit = alt?.zeit?.takeIf { f.bearbeiten } ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
            nachgetragen = f.datum != heute || (alt?.nachgetragen == true),
        )
        viewModelScope.launch {
            speicher.aendern { d -> d.copy(eintraege = d.eintraege + (f.datum to e)) }
            tagebuchAbzeichenPruefen()
            if (f.datum == heute) Erinnerung.entfernen(getApplication<Application>())
        }
        _formular.value = Formular(heute)
        _formularOffen.value = false
        return null
    }

    /** Wirkung des Medikaments nachträglich eintragen (z. B. zwei Stunden nach der Einnahme). */
    fun wirkungSetzen(datum: String, wirkung: String) = viewModelScope.launch {
        speicher.aendern { d ->
            val e = d.eintraege[datum] ?: return@aendern d
            d.copy(eintraege = d.eintraege + (datum to e.copy(wirkung = wirkung)))
        }
    }

    fun medikamentHinzufuegen(name: String) = viewModelScope.launch {
        val n = name.trim()
        if (n.isEmpty()) return@launch
        speicher.aendern { d -> if (n in d.meineMedikamente) d else d.copy(meineMedikamente = d.meineMedikamente + n) }
    }

    fun medikamentEntfernen(name: String) = viewModelScope.launch {
        speicher.aendern { d -> d.copy(meineMedikamente = d.meineMedikamente - name) }
    }

    fun eintragLoeschen(datum: String) = viewModelScope.launch {
        speicher.aendern { d -> d.copy(eintraege = d.eintraege - datum) }
    }

    private suspend fun tagebuchAbzeichenPruefen() {
        speicher.aendern { d ->
            val serie = tagesSerie(d.eintraege, _heute.value)
            val neu = buildSet {
                if (serie >= 7) add("tagebuch7")
                if (serie >= 30) add("tagebuch30")
            }
            if (d.spiel.abzeichen.containsAll(neu)) d else d.copy(spiel = d.spiel.copy(abzeichen = d.spiel.abzeichen + neu))
        }
    }

    // ---------- Spiel ----------

    fun tagebuchSerie() = tagesSerie(daten.value.eintraege, _heute.value)
    fun spruengeMax() = spruengeProTag(tagebuchSerie())
    fun spruengeUebrig(): Int {
        val s = daten.value.spiel
        val gemacht = if (s.sprungDatum == _heute.value.toString()) s.spruengeHeute else 0
        return (spruengeMax() - gemacht).coerceAtLeast(0)
    }

    fun freieTuerme() = TUERME.count { daten.value.spiel.gesamt >= it.punkteNoetig }
    fun aufgabeHeute() = tagesaufgabe(_heute.value, freieTuerme())
    fun aufgabeGeschafft() = daten.value.spiel.aufgabeDatum == _heute.value.toString()

    /** Zählt einen Sprung. false, wenn für heute keine Sprünge mehr übrig sind. */
    fun sprungZaehlen(): Boolean {
        if (spruengeUebrig() <= 0) return false
        val heute = _heute.value.toString()
        viewModelScope.launch {
            speicher.aendern { d ->
                val s = d.spiel
                val n = if (s.sprungDatum == heute) s.spruengeHeute + 1 else 1
                d.copy(spiel = s.copy(sprungDatum = heute, spruengeHeute = n, sprungZahl = s.sprungZahl + 1))
            }
        }
        return true
    }

    /** Verbucht einen Sprung und liefert die Texte für neue Belohnungen. */
    fun sprungVerbuchen(e: SprungErgebnis): List<String> {
        val d = daten.value
        val s = d.spiel
        val heute = _heute.value.toString()
        val texte = mutableListOf<String>()
        var punkte = e.punkte
        var abzeichen = s.abzeichen + Bewertung.abzeichenFuer(e)
        var aufgabeDatum = s.aufgabeDatum
        if (!aufgabeGeschafft()) {
            val a = aufgabeHeute()
            if (a.geschafft(e)) {
                punkte += a.bonus
                aufgabeDatum = heute
                abzeichen = abzeichen + "aufgabe"
                texte += "🎯 Tagesaufgabe geschafft: +${a.bonus} Bonus!"
            }
        }
        val gesamt = s.gesamt + punkte
        ABZEICHEN.filter { it.id in abzeichen && it.id !in s.abzeichen }.forEach { texte += "${it.emoji} Neues Abzeichen: ${it.titel}" }
        TUERME.filter { s.gesamt < it.punkteNoetig && gesamt >= it.punkteNoetig }
            .forEach { texte += "🔓 ${it.name}-${if (it.klippe) "Felsen" else "Turm"} freigeschaltet!" }
        OUTFITS.filter { s.gesamt < it.punkteNoetig && gesamt >= it.punkteNoetig }.forEach { texte += "👕 Neues Outfit: ${it.name}" }
        val rekord = punkte > s.rekord
        if (rekord && s.rekord > 0) texte += "🏆 Neuer Rekord!"
        viewModelScope.launch {
            speicher.aendern { dd ->
                val ss = dd.spiel
                dd.copy(
                    spiel = ss.copy(
                        gesamt = ss.gesamt + punkte, abzeichen = ss.abzeichen + abzeichen, aufgabeDatum = aufgabeDatum,
                        rekord = if (punkte > ss.rekord) punkte else ss.rekord,
                        rekordDatum = if (punkte > ss.rekord) heute else ss.rekordDatum,
                    ),
                )
            }
        }
        return texte
    }

    fun spielEinstellung(ton: Boolean? = null, vibration: Boolean? = null, outfit: Int? = null) = viewModelScope.launch {
        speicher.aendern { d ->
            d.copy(spiel = d.spiel.copy(ton = ton ?: d.spiel.ton, vibration = vibration ?: d.spiel.vibration, outfit = outfit ?: d.spiel.outfit))
        }
    }

    // ---------- Erinnerung ----------

    fun erinnerungSetzen(zeit: String?) = viewModelScope.launch {
        speicher.aendern { it.copy(erinnerung = zeit) }
        Erinnerung.planen(getApplication<Application>(), zeit)
    }

    // ---------- Export / Import ----------

    fun csvText() = Csv.exportieren(daten.value.eintraege)

    fun dateiname(endung: String) = "kopfschmerztagebuch-${_heute.value}.$endung"

    /** Schreibt eine Exportdatei in den Cache (zum Teilen über den FileProvider). */
    suspend fun exportDatei(art: String): File = withContext(Dispatchers.IO) {
        val dir = File(getApplication<Application>().cacheDir, "export").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val f = File(dir, dateiname(art))
        f.outputStream().use { exportSchreiben(art, it) }
        f
    }

    suspend fun exportNach(art: String, uri: Uri) = withContext(Dispatchers.IO) {
        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { exportSchreiben(art, it) }
    }

    private fun exportSchreiben(art: String, out: java.io.OutputStream) {
        when (art) {
            "csv" -> out.write(csvText().toByteArray(Charsets.UTF_8))
            "pdf" -> PdfBericht.schreiben(daten.value.eintraege, _heute.value, out)
            "json" -> out.write(JSON.encodeToString(Daten.serializer(), daten.value).toByteArray(Charsets.UTF_8))
        }
    }

    /**
     * Liest eine Datensicherung (JSON) oder eine CSV. Bei CSV werden nur Tage ergänzt, die noch fehlen.
     * Rückgabe: Meldung für die Anzeige.
     */
    suspend fun importieren(uri: Uri, ersetzen: Boolean): String {
        val text = withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        }.removePrefix("﻿")
        if (text.trimStart().startsWith("{")) {
            val neu = try { JSON.decodeFromString(Daten.serializer(), text) } catch (e: Exception) {
                return "Die Datei ist keine gültige Datensicherung."
            }
            if (ersetzen) {
                speicher.aendern { neu }
                Erinnerung.planen(getApplication<Application>(), neu.erinnerung)
                return "Sicherung wiederhergestellt: ${neu.eintraege.size} Einträge."
            }
            return ergaenzen(neu.eintraege)
        }
        val eintraege = Csv.importieren(text)
        if (eintraege.isEmpty()) return "In der Datei wurden keine Einträge gefunden."
        return ergaenzen(eintraege)
    }

    private suspend fun ergaenzen(neu: Map<String, Eintrag>): String {
        var uebernommen = 0
        speicher.aendern { d ->
            val fehlend = neu.filterKeys { it !in d.eintraege }
            uebernommen = fehlend.size
            d.copy(eintraege = d.eintraege + fehlend)
        }
        tagebuchAbzeichenPruefen()
        val uebersprungen = neu.size - uebernommen
        return "$uebernommen Einträge übernommen" + if (uebersprungen > 0) ", $uebersprungen übersprungen (Tag schon vorhanden)." else "."
    }

    fun allesLoeschen() = viewModelScope.launch {
        speicher.aendern { it.copy(eintraege = emptyMap()) }
    }
}

/** Hält gewählte Chips in der Reihenfolge der Auswahlliste (wie in der Web-Version). */
private object Auswahlreihenfolge {
    private fun sortiert(s: Set<String>, liste: List<String>) = liste.filter { it in s } + s.filter { it !in liste }
    fun ort(s: Set<String>) = sortiert(s, Auswahl.ORTE)
    fun art(s: Set<String>) = sortiert(s, Auswahl.ARTEN)
    fun ausloeser(s: Set<String>) = sortiert(s, Auswahl.AUSLOESER)
    fun begleit(s: Set<String>) = sortiert(s, Auswahl.BEGLEIT)
}
