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

/**
 * Contexto (lugar/entorno) en el que se encuentra el usuario. NO se deduce del
 * sonido: es un estado independiente del dispositivo.
 *
 * Activos: HOME, STREET y OTHER ([ACTIVE_CONTEXTS]). UNIVERSITY y WORK son
 * históricos: se conservan para leer datos ya guardados y sus reglas, pero no se
 * pueden fijar como contexto activo ni aparecen en el catálogo.
 */
export enum UserContext {
  HOME = 'HOME',
  STREET = 'STREET',
  UNIVERSITY = 'UNIVERSITY',
  WORK = 'WORK',
  /** Entorno no definido: contexto por defecto (vigila todo con su prioridad por defecto). */
  OTHER = 'OTHER',
}

/** Única lista de contextos activos (en el reloj: CASA, CALLE, OTRO). */
export const ACTIVE_CONTEXTS: readonly UserContext[] = [
  UserContext.HOME,
  UserContext.STREET,
  UserContext.OTHER,
];

/** Contexto cuando no hay uno explícito. */
export const DEFAULT_CONTEXT = UserContext.OTHER;

export function isActiveContext(value: unknown): value is UserContext {
  return ACTIVE_CONTEXTS.includes(value as UserContext);
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
  // Categorías del reloj (YAMNet). Ver wear/…/SoundCategory.kt.
  GENERAL_ALARM = 'GENERAL_ALARM',
  CAR_ALARM = 'CAR_ALARM',
  TIRE_SKID = 'TIRE_SKID',
  REVERSING_VEHICLE = 'REVERSING_VEHICLE',
  TRAIN_HORN = 'TRAIN_HORN',
  GLASS_BREAK = 'GLASS_BREAK',
  SCREAM = 'SCREAM',
  /** Solo registro: nunca alerta. */
  BELL = 'BELL',
  /** Solo registro: nunca alerta. */
  WARNING_SIGNAL = 'WARNING_SIGNAL',
  UNKNOWN = 'UNKNOWN',
}

/** Categorías de solo registro: se guardan como detección pero no admiten reglas ni alertas. */
export const RECORD_ONLY_CATEGORIES: readonly SoundCategory[] = [
  SoundCategory.BELL,
  SoundCategory.WARNING_SIGNAL,
];

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
