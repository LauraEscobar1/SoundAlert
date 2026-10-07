import { PriorityLevel, SoundCategory, UserContext } from './enums.js';
import { getSoundDefinition } from './sound-catalog.js';

/**
 * Regla efectiva de un sonido dentro de un contexto.
 * `priority` sobrescribe la prioridad por defecto del catálogo.
 */
export interface ContextRule {
  category: SoundCategory;
  enabled: boolean;
  priority: PriorityLevel;
}

const S = SoundCategory;
const D = PriorityLevel.DANGER;
const A = PriorityLevel.ATTENTION;
const I = PriorityLevel.INFORMATION;

type RuleSeed = [SoundCategory, PriorityLevel];

/** Sonidos de peligro: siempre activos en todos los contextos. */
const ALWAYS: RuleSeed[] = [
  [S.SIREN, D],
  [S.FIRE_ALARM, D],
  [S.SMOKE_ALARM, D],
];

/**
 * Matriz activa (HOME = CASA, STREET = CALLE, OTHER = OTRO), idéntica a la del reloj
 * (wear/…/RuleEngine.kt). Las categorías sin clase YAMNet específica
 * (VEHICLE_APPROACHING, NAME_CALLED, MICROWAVE_BEEP, KETTLE_WHISTLE, SCHOOL_BELL)
 * no tienen reglas en los contextos activos: nunca alertan. UNIVERSITY y WORK
 * (históricos, no activables) no cambian.
 */
const SEEDS: Record<UserContext, RuleSeed[]> = {
  [UserContext.HOME]: [
    ...ALWAYS,
    [S.GENERAL_ALARM, A],
    [S.GLASS_BREAK, A],
    [S.SCREAM, A],
    [S.BABY_CRYING, I],
    [S.DOG_BARK, I],
    [S.DOORBELL, I],
    [S.DOOR_KNOCK, I],
    [S.PHONE_RING, I],
    [S.ALARM_CLOCK, I],
    [S.WATER_RUNNING, I],
  ],
  [UserContext.STREET]: [
    ...ALWAYS,
    [S.GENERAL_ALARM, A],
    [S.GLASS_BREAK, A],
    [S.SCREAM, A],
    [S.CAR_HORN, A],
    [S.TIRE_SKID, A],
    [S.TRAIN_HORN, A],
    [S.REVERSING_VEHICLE, A],
    [S.CAR_ALARM, A],
    [S.BICYCLE_BELL, A],
    [S.DOG_BARK, I],
  ],
  [UserContext.OTHER]: [
    ...ALWAYS,
    [S.GENERAL_ALARM, A],
    [S.GLASS_BREAK, A],
    [S.SCREAM, A],
    [S.CAR_HORN, A],
    [S.TIRE_SKID, A],
    [S.TRAIN_HORN, A],
    [S.REVERSING_VEHICLE, A],
    [S.CAR_ALARM, A],
    [S.BICYCLE_BELL, A],
    [S.BABY_CRYING, I],
    [S.DOG_BARK, I],
    [S.DOORBELL, I],
    [S.DOOR_KNOCK, I],
    [S.PHONE_RING, I],
    [S.ALARM_CLOCK, I],
    [S.WATER_RUNNING, I],
  ],
  // Históricos: sin cambios.
  [UserContext.UNIVERSITY]: [
    ...ALWAYS,
    [S.NAME_CALLED, A],
    [S.SCHOOL_BELL, I],
    [S.PHONE_RING, I],
    [S.DOOR_KNOCK, I],
  ],
  [UserContext.WORK]: [
    ...ALWAYS,
    [S.NAME_CALLED, A],
    [S.PHONE_RING, I],
    [S.DOOR_KNOCK, I],
    [S.DOORBELL, I],
  ],
};

/**
 * Reglas por defecto de un contexto. Los sonidos que no aparecen en la
 * semilla quedan deshabilitados (la app se concentra en lo relevante).
 */
export function getDefaultRules(context: UserContext): ContextRule[] {
  const seeded = new Map(SEEDS[context]);
  return Object.values(SoundCategory)
    .filter((c) => c !== SoundCategory.UNKNOWN)
    .map((category) => ({
      category,
      enabled: seeded.has(category),
      priority:
        seeded.get(category) ?? getSoundDefinition(category).defaultPriority,
    }));
}
