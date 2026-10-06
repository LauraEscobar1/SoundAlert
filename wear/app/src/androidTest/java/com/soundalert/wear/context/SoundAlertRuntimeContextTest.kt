package com.soundalert.wear.context

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.soundalert.wear.SoundAlertRuntime
import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.detection.DetectionEvent
import com.soundalert.wear.rules.Priority
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cableado REAL de la app (mismo proceso, SoundAlertApp inicializado): el clasificador
 * que usa el pipeline (SoundAlertRuntime.contextualClassifier) lee la MISMA instancia
 * de contexto que cambia la pantalla (SoundAlertRuntime.contextManager), y el contexto
 * se guarda en SharedPreferences reales.
 */
@RunWith(AndroidJUnit4::class)
class SoundAlertRuntimeContextTest {

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private lateinit var original: SoundAlertContext

    @Before
    fun saveOriginal() {
        original = SoundAlertRuntime.contextManager.current
    }

    @After
    fun restoreOriginal() {
        SoundAlertRuntime.alertManager(app).activeAlerts.forEach { SoundAlertRuntime.alertManager(app).acknowledge(it.id) }
        SoundAlertRuntime.contextManager.set(original)
    }

    private fun detect(category: SoundCategory) = InstrumentationRegistry.getInstrumentation().runOnMainSync {
        SoundAlertRuntime.contextualClassifier(app).onEvent(DetectionEvent.Started(category, 0.9f, category.name, false, 0))
    }

    @Test
    fun elPipelineUsaElContextoSeleccionadoEnLaApp() {
        val contexts = SoundAlertRuntime.contextManager
        assertSame("una sola instancia por proceso", contexts, SoundAlertRuntime.contextManager)

        contexts.set(SoundAlertContext.CASA)
        detect(SoundCategory.DOORBELL)
        val doorbell = SoundAlertRuntime.detectionHistory.detections.value.first()
        assertEquals(SoundAlertContext.CASA to Priority.INFORMATION, doorbell.context to doorbell.priority)

        contexts.set(SoundAlertContext.CALLE)
        detect(SoundCategory.CAR_HORN)
        val horn = SoundAlertRuntime.detectionHistory.detections.value.first()
        assertEquals(SoundAlertContext.CALLE to Priority.ATTENTION, horn.context to horn.priority)

        detect(SoundCategory.SIREN)
        val siren = SoundAlertRuntime.detectionHistory.detections.value.first()
        assertEquals(SoundAlertContext.CALLE to Priority.DANGER, siren.context to siren.priority)
        val sirenAlert = SoundAlertRuntime.alertManager(app).alerts.value.first { it.detectionId == siren.id }
        assertEquals(SoundAlertContext.CALLE, sirenAlert.context)
    }

    @Test
    fun elContextoSeGuardaEnSharedPreferences() {
        val store = SharedPreferencesContextStore(app, "soundalert_context_test")
        val manager = ContextManager(store = store)
        manager.set(SoundAlertContext.CALLE)
        assertEquals(SoundAlertContext.CALLE, ContextManager(store = SharedPreferencesContextStore(app, "soundalert_context_test")).current)
        manager.set(SoundAlertContext.OTRO)
    }
}
