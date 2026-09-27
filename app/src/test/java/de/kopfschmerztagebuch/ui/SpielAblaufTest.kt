package de.kopfschmerztagebuch.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.kopfschmerztagebuch.MainActivity
import de.kopfschmerztagebuch.spiel.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Spielt den ganzen Ablauf durch: Tageseintrag, Sprungturm öffnen, Anlauf, Absprung, Salto, Ergebnis.
 * Legt dabei Bildschirmfotos unter app/build/bildschirmfotos ab.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpielAblaufTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun foto(name: String) {
        rule.waitForIdle()
        val fenster = rule.activity.window.decorView
        val bild = Bitmap.createBitmap(fenster.width, fenster.height, Bitmap.Config.ARGB_8888)
        fenster.draw(Canvas(bild))
        val dir = File("build/bildschirmfotos").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bild.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun eintragenUndVomTurmSpringen() {
        foto("0-formular")
        rule.onNodeWithText("Ja 😣").performClick()
        rule.onNodeWithText("💊 Ibuprofen").performScrollTo().performClick()
        rule.onNodeWithText("Übelkeit").performScrollTo().performClick()
        rule.onNodeWithText("unter 1 Stunde").performScrollTo().performClick()
        rule.onNodeWithText("unter 0,5 Liter").performScrollTo().performClick()
        foto("0b-formular-ausgefuellt")
        rule.onNodeWithText("Eintrag speichern").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Eintrag gespeichert.").assertExists()

        val vm = ViewModelProvider(rule.activity)[AppViewModel::class.java]
        val heute = vm.heute.value.toString()
        val eintrag = vm.daten.value.eintraege.getValue(heute)
        assertEquals(listOf("Ibuprofen"), eintrag.medikamente)
        assertEquals(listOf("Übelkeit"), eintrag.begleit)
        assertTrue("Einnahmezeit vorbelegt", eintrag.medikamentZeit.isNotEmpty())

        // Wirkung direkt auf der Bestätigungskarte nachtragen
        rule.onNodeWithText("Hat Ibuprofen geholfen?", substring = true).assertExists()
        rule.onNodeWithText("gut geholfen").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals("gut geholfen", vm.daten.value.eintraege.getValue(heute).wirkung)
        foto("1-gespeichert")

        // Die Spielschleife läuft endlos – ab hier die Zeit von Hand weiterdrehen.
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Sprungturm").performClick()
        rule.mainClock.advanceTimeBy(600)
        assertEquals(Phase.BEREIT, vm.spiel.phase)
        foto("2-bereit")

        rule.onNodeWithText("Springen!", substring = true).performClick()
        rule.mainClock.advanceTimeBy(500)
        assertEquals("Nach „Springen“ muss der Anlauf laufen", Phase.ANLAUF, vm.spiel.phase)
        foto("3-anlauf")

        rule.onNodeWithText("Absprung!", substring = true).performClick()
        rule.mainClock.advanceTimeBy(150)
        assertEquals("Nach „Absprung“ muss der Springer fliegen", Phase.FLUG, vm.spiel.phase)

        // Finger aufs Spielfeld halten = Hocke, der Springer dreht einen Salto
        rule.onNodeWithTag("spielfeld").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(400)
        foto("4-salto")
        rule.mainClock.advanceTimeBy(450)
        rule.onNodeWithTag("spielfeld").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(3000)

        assertEquals(Phase.FERTIG, vm.spiel.phase)
        val e = vm.spiel.ergebnis!!
        assertTrue("Salti: ${e.salti}", e.salti >= 1)
        assertEquals(5, e.noten.size)
        rule.mainClock.advanceTimeBy(500)
        foto("5-ergebnis")
        assertEquals(1, vm.daten.value.spiel.spruengeHeute)
        assertTrue(vm.daten.value.spiel.gesamt > 0)

        // Nochmal: auch der zweite Sprung muss starten
        rule.onNodeWithText("Nochmal springen", substring = true).performClick()
        rule.mainClock.advanceTimeBy(300)
        assertEquals(Phase.ANLAUF, vm.spiel.phase)
    }
}
