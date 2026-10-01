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

const SEEDS: Record<UserContext, RuleSeed[]> = {
  [UserContext.HOME]: [
    ...ALWAYS,
    [S.BABY_CRYING, A],
    [S.NAME_CALLED, A],
    [S.DOORBELL, I],
    [S.DOOR_KNOCK, I],
    [S.PHONE_RING, I],
    [S.DOG_BARK, I],
    [S.ALARM_CLOCK, I],
    [S.MICROWAVE_BEEP, I],
    [S.KETTLE_WHISTLE, I],
    [S.WATER_RUNNING, I],
  ],
  [UserContext.STREET]: [
    ...ALWAYS,
    [S.CAR_HORN, A],
    [S.VEHICLE_APPROACHING, A],
    [S.BICYCLE_BELL, A],
    [S.NAME_CALLED, A],
    [S.DOG_BARK, I],
  ],
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
