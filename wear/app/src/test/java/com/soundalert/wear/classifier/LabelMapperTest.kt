package com.soundalert.wear.classifier

import org.junit.Assert.assertEquals
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
        // Cada categoría conocida de la v1 tiene al menos una clase; UNKNOWN ninguna.
        assertEquals(SoundCategory.KNOWN.toSet(), YAMNET_CATEGORY_LABELS.keys)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `una clase inexistente se detecta al construir`() {
        LabelMapper(labels, mapOf(SoundCategory.DOORBELL to listOf("Kettle whistle")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `UNKNOWN no puede mapearse explicitamente`() {
        LabelMapper(labels, mapOf(SoundCategory.UNKNOWN to listOf("Music")))
    }

    @Test
    fun `clases verificadas se traducen a su categoria`() {
        val cases = mapOf(
            "Siren" to SoundCategory.SIREN,
            "Police car (siren)" to SoundCategory.SIREN,
            "Ambulance (siren)" to SoundCategory.SIREN,
            "Emergency vehicle" to SoundCategory.SIREN,
            "Vehicle horn, car horn, honking" to SoundCategory.CAR_HORN,
            "Bicycle bell" to SoundCategory.BICYCLE_BELL,
            "Doorbell" to SoundCategory.DOORBELL,
            "Ding-dong" to SoundCategory.DOORBELL,
            "Knock" to SoundCategory.DOOR_KNOCK,
            "Smoke detector, smoke alarm" to SoundCategory.SMOKE_ALARM,
            "Baby cry, infant cry" to SoundCategory.BABY_CRYING,
        )
        for ((label, category) in cases) {
            assertEquals(label, category, mapper.categoryOf(labels.indexOf(label)))
            // También como clase principal de una ventana.
            val result = mapper.classify(scores(label to 0.8f))
            assertEquals(label, category, result.topCategory)
            assertEquals(label, CategoryScore(category, 0.8f, label), result.known.single())
        }
    }

    @Test
    fun `clases sin mapeo son UNKNOWN`() {
        for (label in listOf("Speech", "Music", "Walk, footsteps", "Silence", "Bell", "Whistle", "Alarm")) {
            assertEquals(label, SoundCategory.UNKNOWN, mapper.categoryOf(labels.indexOf(label)))
        }
    }

    /** Los casos observados en las pruebas reales: antes se mostraban como SIREN 0.00. */
    @Test
    fun `una clase principal sin mapeo da UNKNOWN y ninguna categoria conocida`() {
        for (label in listOf("Speech", "Music", "Walk, footsteps")) {
            val result = mapper.classify(scores(label to 0.89f))
            assertEquals(label, SoundCategory.UNKNOWN, result.topCategory)
            assertEquals(LabelScore(label, 0.89f), result.top)
            assertTrue("$label no debe producir categorías conocidas: ${result.known}", result.known.isEmpty())
        }
    }

    @Test
    fun `nunca devuelve categorias a cero ni UNKNOWN en la lista de conocidas`() {
        assertTrue(mapper.map(FloatArray(labels.size)).isEmpty())
        val known = mapper.map(scores("Music" to 0.9f, "Siren" to 0.004f))
        assertEquals(listOf(SoundCategory.SIREN), known.map { it.category })
    }

    @Test
    fun `no conserva la categoria de una ventana anterior`() {
        val siren = mapper.classify(scores("Police car (siren)" to 0.9f))
        assertEquals(SoundCategory.SIREN, siren.topCategory)

        val music = mapper.classify(scores("Music" to 0.89f))
        assertEquals(SoundCategory.UNKNOWN, music.topCategory)
        assertTrue(music.known.isEmpty())
    }

    @Test
    fun `una sirena de fondo se conserva aunque la clase principal sea voz`() {
        val result = mapper.classify(scores("Speech" to 0.7f, "Siren" to 0.5f))
        assertEquals(SoundCategory.UNKNOWN, result.topCategory)
        assertEquals(CategoryScore(SoundCategory.SIREN, 0.5f, "Siren"), result.known.single())
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
