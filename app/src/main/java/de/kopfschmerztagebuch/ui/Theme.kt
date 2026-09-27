package de.kopfschmerztagebuch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Farben {
    val Wasser = Color(0xFF0E7490)
    val WasserDunkel = Color(0xFF0A5D74)
    val Tiefe = Color(0xFF0B3C49)
    val Becken = Color(0xFFEAF6F9)
    val Linie = Color(0xFFC9E4EC)
    val Koralle = Color(0xFFFF6B4A)
    val Frei = Color(0xFF2E9E6B)
    val Grau = Color(0xFF4A7482)

    fun staerke(n: Int) = when {
        n <= 3 -> Color(0xFFE8A93C)
        n <= 6 -> Color(0xFFE07B39)
        else -> Color(0xFFD6402B)
    }
}

private val Hell = lightColorScheme(
    primary = Farben.Wasser, onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBF3), onPrimaryContainer = Farben.Tiefe,
    secondary = Farben.Koralle, onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE1D8), onSecondaryContainer = Color(0xFF8A3A24),
    tertiary = Farben.Frei, onTertiary = Color.White,
    background = Farben.Becken, onBackground = Farben.Tiefe,
    surface = Color.White, onSurface = Farben.Tiefe,
    surfaceVariant = Color(0xFFF4FAFC), onSurfaceVariant = Farben.Grau,
    outline = Farben.Linie, outlineVariant = Farben.Linie,
    surfaceContainer = Color.White, surfaceContainerLow = Color.White, surfaceContainerHigh = Color(0xFFF4FAFC),
    surfaceContainerHighest = Color(0xFFE6F2F6),
)

private val Dunkel = darkColorScheme(
    primary = Color(0xFF6CCFE8), onPrimary = Color(0xFF003543),
    primaryContainer = Farben.WasserDunkel, onPrimaryContainer = Color(0xFFCDEBF3),
    secondary = Color(0xFFFF8F75), onSecondary = Color(0xFF5C1300),
    secondaryContainer = Color(0xFF7A2A16), onSecondaryContainer = Color(0xFFFFDAD1),
    tertiary = Color(0xFF6BD6A2), onTertiary = Color(0xFF00391F),
    background = Color(0xFF07252D), onBackground = Color(0xFFD9EEF3),
    surface = Color(0xFF0C323C), onSurface = Color(0xFFD9EEF3),
    surfaceVariant = Color(0xFF123F4B), onSurfaceVariant = Color(0xFF9FC3CE),
    outline = Color(0xFF2A5A67), outlineVariant = Color(0xFF2A5A67),
    surfaceContainer = Color(0xFF0C323C), surfaceContainerLow = Color(0xFF0C323C), surfaceContainerHigh = Color(0xFF123F4B),
    surfaceContainerHighest = Color(0xFF184955),
)

@Composable
fun TagebuchTheme(inhalt: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dunkel else Hell, content = inhalt)
}

/** Karte wie in der Web-Version: weiß, runde Ecken, dünner Rand. */
@Composable
fun Karte(
    modifier: Modifier = Modifier,
    titel: String? = null,
    hervorgehoben: Boolean = false,
    fehler: Boolean = false,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    val rand = when {
        fehler -> MaterialTheme.colorScheme.secondary
        hervorgehoben -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (fehler) 2.dp else 1.dp, rand),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (titel != null) Text(titel, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
            inhalt()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(werte: List<String>, gewaehlt: (String) -> Boolean, umschalten: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        werte.forEach { w ->
            val an = gewaehlt(w)
            FilterChip(
                selected = an,
                onClick = { umschalten(w) },
                label = { Text(w, fontWeight = if (an) FontWeight.SemiBold else FontWeight.Normal) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }
    }
}
