package de.kopfschmerztagebuch.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

val JSON = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

/**
 * Hält alle Daten der App in einer einzigen JSON-Datei im privaten App-Speicher.
 * Nichts verlässt das Gerät, außer über die Export-Funktionen.
 */
class Speicher private constructor(context: Context) {
    private val datei = AtomicFile(File(context.filesDir, "tagebuch.json"))
    private val sperre = Mutex()
    private val _daten = MutableStateFlow(lesen())
    val daten: StateFlow<Daten> = _daten.asStateFlow()

    private fun lesen(): Daten = try {
        if (datei.baseFile.exists()) JSON.decodeFromString(Daten.serializer(), datei.readFully().decodeToString())
        else Daten()
    } catch (e: Exception) {
        Daten()
    }

    suspend fun aendern(aenderung: (Daten) -> Daten): Daten = sperre.withLock {
        val neu = aenderung(_daten.value)
        if (neu != _daten.value) {
            _daten.value = neu
            withContext(Dispatchers.IO) { schreiben(neu) }
        }
        neu
    }

    private fun schreiben(d: Daten) {
        val out = datei.startWrite()
        try {
            out.write(JSON.encodeToString(Daten.serializer(), d).encodeToByteArray())
            datei.finishWrite(out)
        } catch (e: Exception) {
            datei.failWrite(out)
            throw e
        }
    }

    companion object {
        @Volatile private var instanz: Speicher? = null
        fun von(context: Context): Speicher =
            instanz ?: synchronized(this) {
                instanz ?: Speicher(context.applicationContext).also { instanz = it }
            }
    }
}
