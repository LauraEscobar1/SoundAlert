package com.soundalert.wear.classifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Se ejecuta contra el yamnet_class_map.csv real que va dentro de la app. */
class LabelMapperTest {

    private val labels = File("src/main/assets/yamnet_class_map.csv").reader().use(LabelMapper::parseClassMap)
    private val mapper = LabelMapper(labels)

    private fun scores(vararg pairs: Pair<String, Float>) = FloatArray(labels.size).also { s ->
        pairs.forEach { (label, v) -> s[labels.indexOf(label).also { check(it >= 0) { label } }] = v }
    }

    @Test
    fun `el CSV tiene las 521 clases oficiales en orden`() {
        assertEquals(521, labels.size)
        assertEquals("Speech", labels[0])
        assertEquals("Field recording", labels[520])
        // Nombres con comas entre comillas en el CSV.
        assertEquals("Vehicle horn, car horn, honking", labels[302])
        assertEquals(390, labels.indexOf("Siren"))
    }

    @Test
    fun `todas las clases del mapeo existen en el CSV (si no, el constructor falla)`() {
        YAMNET_CATEGORY_LABELS.values.flatten().forEach { assertTrue(it, it in labels) }
        // Cada categoría de la v1 tiene al menos una clase.
        assertEquals(SoundCategory.entries.toSet(), YAMNET_CATEGORY_LABELS.keys)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `una clase inexistente se detecta al construir`() {
        LabelMapper(labels, mapOf(SoundCategory.DOORBELL to listOf("Kettle whistle")))
    }

    @Test
    fun `clases verificadas se traducen a su categoria`() {
        val cases = mapOf(
            "Siren" to SoundCategory.SIREN,
            "Ambulance (siren)" to SoundCategory.SIREN,
            "Vehicle horn, car horn, honking" to SoundCategory.CAR_HORN,
            "Doorbell" to SoundCategory.DOORBELL,
            "Smoke detector, smoke alarm" to SoundCategory.SMOKE_ALARM,
            "Baby cry, infant cry" to SoundCategory.BABY_CRYING,
        )
        for ((label, category) in cases) assertEquals(label, category, mapper.categoryOf(labels.indexOf(label)))
    }

    @Test
    fun `voz, musica y silencio no generan categoria`() {
        for (label in listOf("Speech", "Music", "Silence", "Bell", "Whistle", "Alarm")) {
            assertNull(label, mapper.categoryOf(labels.indexOf(label)))
        }
    }

    @Test
    fun `agrupa por categoria quedandose con el maximo y ordena`() {
        val result = mapper.map(
            scores(
                "Siren" to 0.4f,
                "Police car (siren)" to 0.7f,
                "Doorbell" to 0.5f,
                "Speech" to 0.9f,
            ),
        )
        assertEquals(
            listOf(
                CategoryScore(SoundCategory.SIREN, 0.7f, "Police car (siren)"),
                CategoryScore(SoundCategory.DOORBELL, 0.5f, "Doorbell"),
            ),
            result.filter { it.score > 0f },
        )
    }

    @Test
    fun `topLabels incluye clases no mapeadas`() {
        val top = mapper.topLabels(scores("Speech" to 0.9f, "Siren" to 0.6f, "Music" to 0.3f), 2)
        assertEquals(listOf(LabelScore("Speech", 0.9f), LabelScore("Siren", 0.6f)), top)
    }
}
