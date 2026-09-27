package de.kopfschmerztagebuch

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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

    override fun onResume() {
        super.onResume()
        vm.datumPruefen()
    }
}
