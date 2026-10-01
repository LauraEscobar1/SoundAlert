/**
 * Nivel de prioridad de una alerta. Determina la vibración y la jerarquía visual.
 */
export enum PriorityLevel {
  /** Sirenas, alarmas → 3 vibraciones. */
  DANGER = 'DANGER',
  /** Bocina de vehículo → 2 vibraciones. */
  ATTENTION = 'ATTENTION',
  /** Timbre de puerta → 1 vibración. */
  INFORMATION = 'INFORMATION',
}

/** Contexto (lugar) en el que se encuentra el usuario. */
export enum UserContext {
  HOME = 'HOME',
  STREET = 'STREET',
  UNIVERSITY = 'UNIVERSITY',
  WORK = 'WORK',
}

/**
 * Categorías de sonido que SoundAlert entiende.
 * Independientes del modelo de IA: cada modelo mapea sus etiquetas a estas.
 */
export enum SoundCategory {
  SIREN = 'SIREN',
  FIRE_ALARM = 'FIRE_ALARM',
  SMOKE_ALARM = 'SMOKE_ALARM',
  CAR_HORN = 'CAR_HORN',
  VEHICLE_APPROACHING = 'VEHICLE_APPROACHING',
  BICYCLE_BELL = 'BICYCLE_BELL',
  DOORBELL = 'DOORBELL',
  DOOR_KNOCK = 'DOOR_KNOCK',
  PHONE_RING = 'PHONE_RING',
  BABY_CRYING = 'BABY_CRYING',
  DOG_BARK = 'DOG_BARK',
  NAME_CALLED = 'NAME_CALLED',
  ALARM_CLOCK = 'ALARM_CLOCK',
  MICROWAVE_BEEP = 'MICROWAVE_BEEP',
  KETTLE_WHISTLE = 'KETTLE_WHISTLE',
  WATER_RUNNING = 'WATER_RUNNING',
  SCHOOL_BELL = 'SCHOOL_BELL',
  UNKNOWN = 'UNKNOWN',
}

/** Resultado de procesar una detección. */
export enum DetectionOutcome {
  ALERTED = 'ALERTED',
  NO_PREDICTIONS = 'NO_PREDICTIONS',
  UNKNOWN_SOUND = 'UNKNOWN_SOUND',
  LOW_CONFIDENCE = 'LOW_CONFIDENCE',
  DISABLED_IN_CONTEXT = 'DISABLED_IN_CONTEXT',
  COOLDOWN = 'COOLDOWN',
  ALERTS_DISABLED = 'ALERTS_DISABLED',
}

/** Estado de una alerta en el reloj. */
export enum AlertStatus {
  /** Recién generada, el usuario aún no la ha visto. */
  ACTIVE = 'ACTIVE',
  /** El usuario la marcó como vista. */
  ACKNOWLEDGED = 'ACKNOWLEDGED',
}
