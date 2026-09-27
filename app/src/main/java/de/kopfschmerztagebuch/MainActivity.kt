package de.kopfschmerztagebuch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import de.kopfschmerztagebuch.erinnerung.Erinnerung
import de.kopfschmerztagebuch.ui.App
import de.kopfschmerztagebuch.ui.AppViewModel
import de.kopfschmerztagebuch.ui.TagebuchTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        Erinnerung.kanalAnlegen(this)
        Erinnerung.planen(this, vm.daten.value.erinnerung)
        setContent { TagebuchTheme { App(vm) } }
    }

    /** Neuer Tag, während die App offen ist: Formular und Sprungturm auf heute umstellen. */
    private val tageswechsel = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = vm.datumPruefen()
    }

    override fun onStart() {
        super.onStart()
        vm.datumPruefen()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, tageswechsel, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        unregisterReceiver(tageswechsel)
        super.onStop()
    }
}
