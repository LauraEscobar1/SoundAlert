package com.soundalert.wear.classifier

import com.soundalert.wear.config.PipelineConfig
import com.soundalert.wear.config.StabilizerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Se ejecuta contra el yamnet_class_map.csv real que va dentro de la app. */
class LabelMapperTest {

    private val labels = File("src/main/assets/yamnet_class_map.csv").reader().use(LabelMapper::parseClassMap)
    private val mapper = LabelMapper(labels, PipelineConfig().stabilizer)

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
        LabelMapper(labels, PipelineConfig().stabilizer, mapOf(SoundCategory.DOORBELL to listOf("Kettle whistle")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `UNKNOWN no puede mapearse explicitamente`() {
        LabelMapper(labels, PipelineConfig().stabilizer, mapOf(SoundCategory.UNKNOWN to listOf("Music")))
    }

    @Test
    fun `clases verificadas se traducen a su categoria`() {
        val cases = mapOf(
            "Siren" to SoundCategory.SIREN,
            "Civil defense siren" to SoundCategory.SIREN,
            "Police car (siren)" to SoundCategory.SIREN,
            "Ambulance (siren)" to SoundCategory.SIREN,
            "Fire engine, fire truck (siren)" to SoundCategory.SIREN,
            "Emergency vehicle" to SoundCategory.SIREN,
            "Fire alarm" to SoundCategory.FIRE_ALARM,
            "Smoke detector, smoke alarm" to SoundCategory.SMOKE_ALARM,
            "Alarm" to SoundCategory.GENERAL_ALARM,
            "Vehicle horn, car horn, honking" to SoundCategory.CAR_HORN,
            "Air horn, truck horn" to SoundCategory.CAR_HORN,
            "Toot" to SoundCategory.CAR_HORN,
            "Car alarm" to SoundCategory.CAR_ALARM,
            "Skidding" to SoundCategory.TIRE_SKID,
            "Tire squeal" to SoundCategory.TIRE_SKID,
            "Reversing beeps" to SoundCategory.REVERSING_VEHICLE,
            "Train horn" to SoundCategory.TRAIN_HORN,
            "Train whistle" to SoundCategory.TRAIN_HORN,
            "Bicycle bell" to SoundCategory.BICYCLE_BELL,
            "Shatter" to SoundCategory.GLASS_BREAK,
            "Screaming" to SoundCategory.SCREAM,
            "Baby cry, infant cry" to SoundCategory.BABY_CRYING,
            "Bark" to SoundCategory.DOG_BARK,
            "Bell" to SoundCategory.BELL,
            "Church bell" to SoundCategory.BELL,
            "Jingle bell" to SoundCategory.BELL,
            "Chime" to SoundCategory.BELL,
            "Beep, bleep" to SoundCategory.WARNING_SIGNAL,
            "Buzzer" to SoundCategory.WARNING_SIGNAL,
            "Doorbell" to SoundCategory.DOORBELL,
            "Ding-dong" to SoundCategory.DOORBELL,
            "Knock" to SoundCategory.DOOR_KNOCK,
            "Telephone bell ringing" to SoundCategory.PHONE_RING,
            "Ringtone" to SoundCategory.PHONE_RING,
            "Alarm clock" to SoundCategory.ALARM_CLOCK,
            "Water tap, faucet" to SoundCategory.WATER_RUNNING,
        )
        // La tabla de arriba es el mapeo completo: ni una clase más ni una menos.
        assertEquals(cases.keys, YAMNET_CATEGORY_LABELS.values.flatten().toSet())
        for ((label, category) in cases) {
            assertEquals(label, category, mapper.categoryOf(labels.indexOf(label)))
            // También como clase principal de una ventana.
            val result = mapper.classify(scores(label to 0.8f))
            assertEquals(label, category, result.topCategory)
            assertEquals(label, CategoryScore(category, 0.8f, label), result.known.single())
        }
    }

    /**
     * Clases que NO deben mapearse: irrelevantes (voz, música, respiración…), genéricas
     * (Dog, Bell, Glass, Beep…), engañosas ("Honk" es el ganso; "Car passing by" no es
     * "se acerca") o sin validar (Explosion, Gunshot…). Todas deben ser UNKNOWN.
     */
    @Test
    fun `clases sin mapeo son UNKNOWN`() {
        val unmapped = listOf(
            // Irrelevantes / cotidianas (voz, música, aplausos y pasos: decisión explícita, siguen UNKNOWN)
            "Speech", "Conversation", "Whispering", "Music", "Breathing", "Snoring", "Cough", "Sneeze", "Laughter",
            "Applause", "Clapping", "Typing", "Computer keyboard", "Walk, footsteps", "Run", "Inside, small room",
            "Silence", "Television", "Radio", "Noise", "Environmental noise", "Sine wave",
            // Genéricas o engañosas
            "Dog", "Animal", "Ding", "Wind chime", "Glass", "Whistle", "Sine wave", "Chirp tone",
            "Honk", "French horn", "Vehicle", "Car", "Motorcycle", "Traffic noise, roadway noise",
            "Car passing by", "Accelerating, revving, vroom", "Microwave oven", "Telephone", "Train",
            // Sin validar o con demasiados falsos positivos
            "Explosion", "Boom", "Gunshot, gunfire", "Fireworks", "Bang", "Slam", "Thump, thud",
            "Smash, crash", "Breaking", "Shout", "Yell", "Crying, sobbing", "Rumble",
        )
        for (label in unmapped) {
            assertTrue("$label no existe en el CSV", label in labels)
            assertEquals(label, SoundCategory.UNKNOWN, mapper.categoryOf(labels.indexOf(label)))
        }
    }

    // ---------- Solo registro y genéricas ----------

    @Test
    fun `BELL y WARNING_SIGNAL son de solo registro, el resto alertables`() {
        assertEquals(setOf(SoundCategory.BELL, SoundCategory.WARNING_SIGNAL), SoundCategory.KNOWN.filterNot { it.alertable }.toSet())
        assertTrue(SoundCategory.ALERTABLE.none { it == SoundCategory.UNKNOWN })
    }

    @Test
    fun `Bell se descarta si hay timbre de bicicleta o de puerta`() {
        assertEquals(listOf(SoundCategory.BICYCLE_BELL), mapper.classify(scores("Bell" to 0.6f, "Bicycle bell" to 0.4f)).known.map { it.category })
        val door = mapper.classify(scores("Bell" to 0.6f, "Ding-dong" to 0.4f))
        assertEquals(listOf(SoundCategory.DOORBELL), door.known.map { it.category })
        assertEquals(SoundCategory.DOORBELL, door.topCategory)
        // Timbre por debajo de su umbral (0,35): la campana genérica se conserva.
        val weakDoor = mapper.classify(scores("Bell" to 0.6f, "Ding-dong" to 0.3f))
        assertEquals(listOf(SoundCategory.BELL, SoundCategory.DOORBELL), weakDoor.known.map { it.category })
        assertEquals(SoundCategory.BELL, weakDoor.topCategory)
        assertEquals(listOf(SoundCategory.BELL), mapper.classify(scores("Church bell" to 0.7f)).known.map { it.category })
    }

    @Test
    fun `un pitido junto a una alarma se descarta - incendio o humo tienen prioridad`() {
        val smoke = mapper.classify(scores("Beep, bleep" to 0.7f, "Smoke detector, smoke alarm" to 0.4f))
        assertEquals(listOf(SoundCategory.SMOKE_ALARM), smoke.known.map { it.category })
        assertEquals(SoundCategory.SMOKE_ALARM, smoke.topCategory)
        // Humo por debajo de su umbral (0,35): el pitido genérico se conserva.
        val weakSmoke = mapper.classify(scores("Beep, bleep" to 0.7f, "Smoke detector, smoke alarm" to 0.3f))
        assertEquals(listOf(SoundCategory.WARNING_SIGNAL, SoundCategory.SMOKE_ALARM), weakSmoke.known.map { it.category })
        val general = mapper.classify(scores("Buzzer" to 0.6f, "Alarm" to 0.6f))
        assertEquals(listOf(SoundCategory.GENERAL_ALARM), general.known.map { it.category })
        assertEquals(listOf(SoundCategory.WARNING_SIGNAL), mapper.classify(scores("Beep, bleep" to 0.7f)).known.map { it.category })
    }

    // ---------- GENERAL_ALARM ("Alarm", clase padre) ----------

    @Test
    fun `Alarm sola es GENERAL_ALARM`() {
        val result = mapper.classify(scores("Alarm" to 0.6f))
        assertEquals(SoundCategory.GENERAL_ALARM, result.topCategory)
        assertEquals(listOf(SoundCategory.GENERAL_ALARM), result.known.map { it.category })
    }

    @Test
    fun `Alarm junto a una sirena real se descarta (caso real Police car 0,74 + Alarm 0,41)`() {
        val siren = mapper.classify(scores("Police car (siren)" to 0.74f, "Alarm" to 0.41f))
        assertEquals(listOf(SoundCategory.SIREN), siren.known.map { it.category })
    }

    @Test
    fun `ninguna alarma especifica que alcanza su umbral termina como GENERAL_ALARM ni como pitido`() {
        val specificLabels = mapOf(
            SoundCategory.SIREN to "Siren",
            SoundCategory.FIRE_ALARM to "Fire alarm",
            SoundCategory.SMOKE_ALARM to "Smoke detector, smoke alarm",
            SoundCategory.CAR_ALARM to "Car alarm",
            SoundCategory.ALARM_CLOCK to "Alarm clock",
            SoundCategory.PHONE_RING to "Ringtone",
            SoundCategory.REVERSING_VEHICLE to "Reversing beeps",
        )
        assertEquals(SoundCategory.SPECIFIC_ALARMS, specificLabels.keys)
        for ((category, label) in specificLabels) {
            // La específica justo en su propio umbral (0,35 o 0,50 según la categoría).
            val threshold = mapper.detection.onThresholdFor(category)
            val result = mapper.classify(scores("Alarm" to 0.9f, label to threshold, "Beep, bleep" to 0.8f))
            assertEquals(label, listOf(category), result.known.map { it.category })
            assertEquals(label, category, result.topCategory)
        }
    }

    // ---------- Precedencia específica/genérica: la específica desplaza solo con SU umbral ----------

    @Test
    fun `los umbrales de precedencia son los de la configuracion recibida`() {
        // Con una configuración donde FIRE_ALARM exige 0,60, un incendio de 0,40 ya no desplaza a "Alarm".
        val strict = PipelineConfig().stabilizer.let {
            it.copy(onThresholdByCategory = it.onThresholdByCategory + (SoundCategory.FIRE_ALARM to 0.60f))
        }
        val custom = LabelMapper(labels, strict)
        val result = custom.classify(scores("Alarm" to 0.6f, "Fire alarm" to 0.40f))
        assertTrue(SoundCategory.GENERAL_ALARM in result.known.map { it.category })
        // Con la configuración por defecto (0,35), el mismo caso sí desplaza.
        assertEquals(listOf(SoundCategory.FIRE_ALARM), mapper.classify(scores("Alarm" to 0.6f, "Fire alarm" to 0.40f)).known.map { it.category })
    }

    @Test
    fun `1 - especifica en o por encima de su umbral desplaza a la generica`() {
        val fire = mapper.classify(scores("Alarm" to 0.6f, "Fire alarm" to 0.35f)) // FIRE_ALARM: 0,35
        assertEquals(listOf(SoundCategory.FIRE_ALARM), fire.known.map { it.category })
        assertEquals(SoundCategory.FIRE_ALARM, fire.topCategory)
        val carAlarm = mapper.classify(scores("Alarm" to 0.6f, "Car alarm" to 0.50f)) // CAR_ALARM: 0,50
        assertEquals(listOf(SoundCategory.CAR_ALARM), carAlarm.known.map { it.category })
    }

    @Test
    fun `2 - especifica por debajo de su umbral y generica por encima - la generica permanece`() {
        // Caso de la decisión: Alarm 0,60 (umbral 0,50) + Fire alarm 0,20 (umbral 0,35).
        val result = mapper.classify(scores("Alarm" to 0.60f, "Fire alarm" to 0.20f))
        assertEquals(
            listOf(CategoryScore(SoundCategory.GENERAL_ALARM, 0.60f, "Alarm"), CategoryScore(SoundCategory.FIRE_ALARM, 0.20f, "Fire alarm")),
            result.known,
        )
        assertEquals(SoundCategory.GENERAL_ALARM, result.topCategory)
        // El umbral es el de cada categoría: Car alarm 0,45 no alcanza su 0,50.
        val carAlarm = mapper.classify(scores("Alarm" to 0.6f, "Car alarm" to 0.45f))
        assertTrue(SoundCategory.GENERAL_ALARM in carAlarm.known.map { it.category })
    }

    @Test
    fun `3 - especifica y generica por debajo de sus umbrales - ninguna alcanza su umbral`() {
        // Caso real observado: Alarm 0,41 (umbral 0,50) + Fire alarm 0,26 (umbral 0,35).
        val result = mapper.classify(scores("Alarm" to 0.41f, "Fire alarm" to 0.26f))
        assertTrue(result.known.none { it.score >= mapper.detection.onThresholdFor(it.category) })
    }

    @Test
    fun `una alarma especifica muy debil no descarta la general`() {
        val result = mapper.classify(scores("Alarm" to 0.6f, "Siren" to 0.10f))
        assertTrue(SoundCategory.GENERAL_ALARM in result.known.map { it.category })
    }

    @Test
    fun `sonidos que no son alarmas no descartan la general`() {
        val result = mapper.classify(scores("Alarm" to 0.6f, "Bark" to 0.5f))
        assertEquals(setOf(SoundCategory.GENERAL_ALARM, SoundCategory.DOG_BARK), result.known.map { it.category }.toSet())
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
