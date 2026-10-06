package com.soundalert.wear.rules

import com.soundalert.wear.classifier.SoundCategory
import com.soundalert.wear.classifier.SoundCategory.ALARM_CLOCK
import com.soundalert.wear.classifier.SoundCategory.BABY_CRYING
import com.soundalert.wear.classifier.SoundCategory.BICYCLE_BELL
import com.soundalert.wear.classifier.SoundCategory.CAR_ALARM
import com.soundalert.wear.classifier.SoundCategory.CAR_HORN
import com.soundalert.wear.classifier.SoundCategory.DOG_BARK
import com.soundalert.wear.classifier.SoundCategory.DOORBELL
import com.soundalert.wear.classifier.SoundCategory.DOOR_KNOCK
import com.soundalert.wear.classifier.SoundCategory.FIRE_ALARM
import com.soundalert.wear.classifier.SoundCategory.GENERAL_ALARM
import com.soundalert.wear.classifier.SoundCategory.GLASS_BREAK
import com.soundalert.wear.classifier.SoundCategory.PHONE_RING
import com.soundalert.wear.classifier.SoundCategory.REVERSING_VEHICLE
import com.soundalert.wear.classifier.SoundCategory.SCREAM
import com.soundalert.wear.classifier.SoundCategory.SIREN
import com.soundalert.wear.classifier.SoundCategory.SMOKE_ALARM
import com.soundalert.wear.classifier.SoundCategory.TIRE_SKID
import com.soundalert.wear.classifier.SoundCategory.TRAIN_HORN
import com.soundalert.wear.classifier.SoundCategory.WATER_RUNNING
import com.soundalert.wear.context.SoundAlertContext
import com.soundalert.wear.rules.Priority.ATTENTION
import com.soundalert.wear.rules.Priority.DANGER
import com.soundalert.wear.rules.Priority.INFORMATION

/** Regla: en este contexto, este sonido tiene esta prioridad. Es configuración, no un evento. */
data class SoundRule(val context: SoundAlertContext, val category: SoundCategory, val priority: Priority)

/**
 * SoundCategory + contexto → regla. Las reglas son configuración fija: una
 * detección solo las LEE, nunca crea ni modifica una regla.
 *
 * Un sonido sin regla en un contexto no es relevante ahí (p. ej. la bocina en
 * CASA) y no genera alerta. UNKNOWN y las categorías de solo registro (BELL,
 * WARNING_SIGNAL) no pueden tener regla: el constructor lo rechaza.
 */
class RuleEngine(rules: Map<SoundAlertContext, Map<SoundCategory, Priority>> = DEFAULT_RULES) {

    private val table: Map<SoundAlertContext, Map<SoundCategory, Priority>>

    init {
        require(rules.values.none { SoundCategory.UNKNOWN in it }) { "UNKNOWN no puede tener reglas" }
        val recordOnly = rules.values.flatMap { it.keys }.filterNot { it.alertable }.toSet()
        require(recordOnly.isEmpty()) { "Categorías de solo registro no pueden tener reglas: $recordOnly" }
        table = rules.mapValues { (_, byCategory) -> byCategory.toMap() }
    }

    fun match(category: SoundCategory, context: SoundAlertContext): SoundRule? {
        if (!category.known || !category.alertable) return null
        val priority = table[context]?.get(category) ?: return null
        return SoundRule(context, category, priority)
    }

    fun rulesFor(context: SoundAlertContext): List<SoundRule> =
        table[context].orEmpty().map { (category, priority) -> SoundRule(context, category, priority) }

    companion object {
        /**
         * Activas en todos los contextos.
         *  - Sirena, incendio y humo: peligro siempre (igual que en el backend).
         *  - Alarma general: ATTENTION, no DANGER, porque no sabemos qué alarma es
         *    (antirrobo, evacuación, un aparato…). Pendiente: ¿DANGER en CASA/TRABAJO?
         */
        private val ALWAYS = mapOf(
            SIREN to DANGER,
            FIRE_ALARM to DANGER,
            SMOKE_ALARM to DANGER,
            GENERAL_ALARM to ATTENTION,
        )

        /**
         * Matriz. Activas: CASA, CALLE y OTRO (iguales que HOME, STREET y OTHER en el
         * backend). TRABAJO y TRANSPORTE se conservan pero no pueden ser el contexto
         * activo (ContextManager los rechaza).
         *
         * Decisiones pendientes (documentadas, sin regla especial todavía):
         *  - GLASS_BREAK: ATTENTION en todos los contextos. "Shatter" no distingue un vaso
         *    que cae de una ventana rota por un intruso, y DANGER implica una alerta
         *    persistente que vibra cada 15 s hasta confirmar: con falsos positivos
         *    domésticos frecuentes sería contraproducente. Revisable con datos reales.
         *  - CAR_HORN en TRANSPORTE: ATTENTION, igual que en CALLE. Dentro de un
         *    vehículo una bocina suele ir dirigida a ese vehículo (útil si conduce).
         *  - TIRE_SKID en CALLE: ¿DANGER? Hoy ATTENTION.
         *  - SCREAM: ATTENTION en todos; puede ser juego, TV o deporte.
         *  - CAR_ALARM en CASA: sin regla (a menudo es un coche ajeno en la calle).
         */
        val DEFAULT_RULES: Map<SoundAlertContext, Map<SoundCategory, Priority>> = mapOf(
            SoundAlertContext.CALLE to ALWAYS + mapOf(
                CAR_HORN to ATTENTION,
                BICYCLE_BELL to ATTENTION,
                TIRE_SKID to ATTENTION,
                REVERSING_VEHICLE to ATTENTION,
                TRAIN_HORN to ATTENTION,
                CAR_ALARM to ATTENTION,
                GLASS_BREAK to ATTENTION,
                SCREAM to ATTENTION,
                DOG_BARK to INFORMATION,
            ),
            SoundAlertContext.CASA to ALWAYS + mapOf(
                GLASS_BREAK to ATTENTION,
                SCREAM to ATTENTION,
                BABY_CRYING to INFORMATION,
                DOORBELL to INFORMATION,
                DOOR_KNOCK to INFORMATION,
                PHONE_RING to INFORMATION,
                ALARM_CLOCK to INFORMATION,
                DOG_BARK to INFORMATION,
                WATER_RUNNING to INFORMATION,
            ),
            SoundAlertContext.TRABAJO to ALWAYS + mapOf(
                GLASS_BREAK to ATTENTION,
                SCREAM to ATTENTION,
                // Almacenes, obras, aparcamientos.
                REVERSING_VEHICLE to ATTENTION,
                PHONE_RING to INFORMATION,
                DOOR_KNOCK to INFORMATION,
                DOORBELL to INFORMATION,
            ),
            // En un vehículo o transporte público: lo que pasa en la vía y el teléfono.
            SoundAlertContext.TRANSPORTE to ALWAYS + mapOf(
                CAR_HORN to ATTENTION,
                TIRE_SKID to ATTENTION,
                TRAIN_HORN to ATTENTION,
                GLASS_BREAK to ATTENTION,
                SCREAM to ATTENTION,
                PHONE_RING to INFORMATION,
            ),
            // Entorno no definido: se vigila todo con su prioridad por defecto.
            SoundAlertContext.OTRO to ALWAYS + mapOf(
                CAR_HORN to ATTENTION,
                CAR_ALARM to ATTENTION,
                TIRE_SKID to ATTENTION,
                REVERSING_VEHICLE to ATTENTION,
                TRAIN_HORN to ATTENTION,
                BICYCLE_BELL to ATTENTION,
                GLASS_BREAK to ATTENTION,
                SCREAM to ATTENTION,
                BABY_CRYING to INFORMATION,
                DOORBELL to INFORMATION,
                DOOR_KNOCK to INFORMATION,
                PHONE_RING to INFORMATION,
                ALARM_CLOCK to INFORMATION,
                DOG_BARK to INFORMATION,
                WATER_RUNNING to INFORMATION,
            ),
        )
    }
}
