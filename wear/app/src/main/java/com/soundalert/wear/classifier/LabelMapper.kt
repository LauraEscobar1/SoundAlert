package com.soundalert.wear.classifier

import java.io.Reader

/** Puntuación de una categoría y la clase del modelo que la produjo. */
data class CategoryScore(val category: SoundCategory, val score: Float, val label: String)

/** Clase nativa del modelo con su puntuación (para logs). */
data class LabelScore(val label: String, val score: Float)

/**
 * Resultado de una ventana.
 *
 * [top]/[topCategory]: la clase con más puntuación y su categoría, o
 * [SoundCategory.UNKNOWN] si no tiene mapeo. La puntuación de una clase
 * UNKNOWN es la confianza en esa clase (p. ej. Music), no en una categoría.
 *
 * [known]: categorías conocidas con puntuación > 0, de mayor a menor. Es lo
 * único que entra al estabilizador. YAMNet es multietiqueta: una sirena de
 * fondo mientras alguien habla aparece aquí aunque [top] sea Speech.
 */
data class WindowClassification(
    val top: LabelScore,
    val topCategory: SoundCategory,
    val known: List<CategoryScore>,
)

/**
 * Traduce las 521 clases de YAMNet (ontología AudioSet) a [SoundCategory].
 * Cada nombre de [YAMNET_CATEGORY_LABELS] se ha comprobado contra
 * yamnet_class_map.csv; si alguno no existiera, el constructor falla.
 * Las clases sin mapeo (Speech, Music…) son [SoundCategory.UNKNOWN].
 * No guarda estado entre ventanas: cada resultado depende solo de sus puntuaciones.
 */
class LabelMapper(
    val labels: List<String>,
    mapping: Map<SoundCategory, List<String>> = YAMNET_CATEGORY_LABELS,
) {
    private val categoryByIndex: Array<SoundCategory>

    init {
        require(SoundCategory.UNKNOWN !in mapping) { "UNKNOWN no se mapea: es la ausencia de mapeo" }
        val index = labels.withIndex().associate { (i, l) -> l to i }
        categoryByIndex = Array(labels.size) { SoundCategory.UNKNOWN }
        for ((category, names) in mapping) {
            for (name in names) {
                val i = requireNotNull(index[name]) { "La clase \"$name\" no existe en el modelo" }
                check(categoryByIndex[i] == SoundCategory.UNKNOWN) { "La clase \"$name\" está asignada dos veces" }
                categoryByIndex[i] = category
            }
        }
    }

    /** Categoría de una clase del modelo; [SoundCategory.UNKNOWN] si no tiene mapeo. */
    fun categoryOf(classIndex: Int): SoundCategory {
        require(classIndex in labels.indices) { "Índice de clase fuera de rango: $classIndex" }
        return categoryByIndex[classIndex]
    }

    fun classify(scores: FloatArray): WindowClassification {
        val known = map(scores)
        var topIndex = 0
        for (i in scores.indices) if (scores[i] > scores[topIndex]) topIndex = i
        return WindowClassification(
            top = LabelScore(labels[topIndex], scores[topIndex]),
            topCategory = categoryByIndex[topIndex],
            known = known,
        )
    }

    /**
     * Categorías CONOCIDAS con puntuación > 0 (máximo de sus clases), de mayor
     * a menor. Nunca incluye [SoundCategory.UNKNOWN] ni categorías a 0.
     */
    fun map(scores: FloatArray): List<CategoryScore> {
        require(scores.size == labels.size) { "Se esperaban ${labels.size} puntuaciones, llegaron ${scores.size}" }
        val best = HashMap<SoundCategory, CategoryScore>()
        for (i in scores.indices) {
            val category = categoryByIndex[i]
            if (!category.known || scores[i] <= 0f) continue
            val current = best[category]
            if (current == null || scores[i] > current.score) {
                best[category] = CategoryScore(category, scores[i], labels[i])
            }
        }
        return best.values.sortedByDescending { it.score }
    }

    /** Las k clases nativas con más puntuación (mapeadas o no). */
    fun topLabels(scores: FloatArray, k: Int): List<LabelScore> =
        scores.indices.sortedByDescending { scores[it] }.take(k).map { LabelScore(labels[it], scores[it]) }

    companion object {
        /** Lee yamnet_class_map.csv (index,mid,display_name) y devuelve los nombres por índice. */
        fun parseClassMap(reader: Reader): List<String> {
            val rows = reader.readLines().map { it.trimEnd('\r') }.filter { it.isNotBlank() }
            require(rows.first().startsWith("index,")) { "Cabecera inesperada: ${rows.first()}" }
            return rows.drop(1).mapIndexed { expected, line ->
                val first = line.indexOf(',')
                val second = line.indexOf(',', first + 1)
                val index = line.substring(0, first).toInt()
                check(index == expected) { "Índice fuera de orden en la fila $expected: $line" }
                line.substring(second + 1).removeSurrounding("\"")
            }
        }
    }
}

/** Clases de YAMNet (nombres exactos de yamnet_class_map.csv) por categoría. */
val YAMNET_CATEGORY_LABELS: Map<SoundCategory, List<String>> = mapOf(
    SoundCategory.SIREN to listOf(
        "Siren",
        "Civil defense siren",
        "Police car (siren)",
        "Ambulance (siren)",
        "Fire engine, fire truck (siren)",
        "Emergency vehicle",
    ),
    SoundCategory.FIRE_ALARM to listOf("Fire alarm"),
    SoundCategory.SMOKE_ALARM to listOf("Smoke detector, smoke alarm"),
    SoundCategory.CAR_HORN to listOf("Vehicle horn, car horn, honking", "Air horn, truck horn", "Toot"),
    SoundCategory.VEHICLE_APPROACHING to listOf("Car passing by"),
    SoundCategory.BICYCLE_BELL to listOf("Bicycle bell"),
    SoundCategory.DOORBELL to listOf("Doorbell", "Ding-dong"),
    SoundCategory.DOOR_KNOCK to listOf("Knock"),
    SoundCategory.PHONE_RING to listOf("Telephone bell ringing", "Ringtone"),
    SoundCategory.BABY_CRYING to listOf("Baby cry, infant cry"),
    SoundCategory.DOG_BARK to listOf("Bark", "Dog"),
    SoundCategory.ALARM_CLOCK to listOf("Alarm clock"),
    SoundCategory.MICROWAVE_BEEP to listOf("Microwave oven"),
    SoundCategory.WATER_RUNNING to listOf("Water tap, faucet"),
)
