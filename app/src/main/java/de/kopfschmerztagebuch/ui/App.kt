package de.kopfschmerztagebuch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Pool
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun App(vm: AppViewModel) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val heute by vm.heute.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val meldung: (String) -> Unit = { text -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(text) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                    .background(Brush.verticalGradient(listOf(Farben.Wasser, Farben.WasserDunkel)))
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp),
            ) {
                Column {
                    Text("Kopfschmerz-Tagebuch", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        heute.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)),
                        color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp,
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf(
                    Triple(Tab.HEUTE, "Heute", Icons.Outlined.EditNote),
                    Triple(Tab.VERLAUF, "Verlauf", Icons.Outlined.CalendarMonth),
                    Triple(Tab.SPIEL, "Sprungturm", Icons.Outlined.Pool),
                    Triple(Tab.MEHR, "Mehr", Icons.Outlined.Settings),
                ).forEach { (t, name, icon) ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { vm.tabWechseln(t) },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(name) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                    )
                }
            }
        },
    ) { innen ->
        Box(Modifier.fillMaxSize().padding(innen).consumeWindowInsets(innen).imePadding()) {
            when (tab) {
                Tab.HEUTE -> HeuteScreen(vm, meldung)
                Tab.VERLAUF -> VerlaufScreen(vm, meldung)
                Tab.SPIEL -> SpielScreen(vm)
                Tab.MEHR -> MehrScreen(vm, meldung)
            }
        }
    }
}
