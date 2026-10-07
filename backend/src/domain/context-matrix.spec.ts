import {
  ACTIVE_CONTEXTS,
  DEFAULT_CONTEXT,
  getDefaultRules,
  isActiveContext,
  PriorityLevel,
  RECORD_ONLY_CATEGORIES,
  SoundCategory,
  UserContext,
} from './index.js';

const D = PriorityLevel.DANGER;
const A = PriorityLevel.ATTENTION;
const I = PriorityLevel.INFORMATION;
const S = SoundCategory;
type Row = [
  SoundCategory,
  PriorityLevel | null,
  PriorityLevel | null,
  PriorityLevel | null,
];

/** Matriz activa acordada: [categoría, HOME (CASA), STREET (CALLE), OTHER (OTRO)]. null = sin alerta. */
const MATRIX: Row[] = [
  [S.SIREN, D, D, D],
  [S.FIRE_ALARM, D, D, D],
  [S.SMOKE_ALARM, D, D, D],
  [S.GENERAL_ALARM, A, A, A],
  [S.GLASS_BREAK, A, A, A],
  [S.SCREAM, A, A, A],
  [S.CAR_HORN, null, A, A],
  [S.TIRE_SKID, null, A, A],
  [S.TRAIN_HORN, null, A, A],
  [S.REVERSING_VEHICLE, null, A, A],
  [S.CAR_ALARM, null, A, A],
  [S.BICYCLE_BELL, null, A, A],
  [S.BABY_CRYING, I, null, I],
  [S.DOG_BARK, I, I, I],
  [S.DOORBELL, I, null, I],
  [S.DOOR_KNOCK, I, null, I],
  [S.PHONE_RING, I, null, I],
  [S.ALARM_CLOCK, I, null, I],
  [S.WATER_RUNNING, I, null, I],
  [S.BELL, null, null, null],
  [S.WARNING_SIGNAL, null, null, null],
];

const effective = (context: UserContext, category: SoundCategory) => {
  const rule = getDefaultRules(context).find((r) => r.category === category)!;
  return rule.enabled ? rule.priority : null;
};

describe('Contextos activos', () => {
  it('son exactamente HOME, STREET y OTHER, con OTHER por defecto', () => {
    expect(ACTIVE_CONTEXTS).toEqual([
      UserContext.HOME,
      UserContext.STREET,
      UserContext.OTHER,
    ]);
    expect(DEFAULT_CONTEXT).toBe(UserContext.OTHER);
    expect(isActiveContext('STREET')).toBe(true);
    for (const v of ['UNIVERSITY', 'WORK', 'CALLE', '', undefined])
      expect(isActiveContext(v)).toBe(false);
  });
});

describe('Matriz activa contexto × categoría', () => {
  it.each(MATRIX)(
    '%s → HOME %s · STREET %s · OTHER %s',
    (category, home, street, other) => {
      expect(effective(UserContext.HOME, category)).toBe(home);
      expect(effective(UserContext.STREET, category)).toBe(street);
      expect(effective(UserContext.OTHER, category)).toBe(other);
    },
  );

  it('las categorías de solo registro nunca están activas en ningún contexto', () => {
    for (const ctx of Object.values(UserContext)) {
      for (const c of RECORD_ONLY_CATEGORIES)
        expect(effective(ctx, c)).toBeNull();
    }
  });

  it('las categorías sin clase YAMNet específica no alertan en ningún contexto activo', () => {
    for (const ctx of ACTIVE_CONTEXTS) {
      for (const c of [
        S.VEHICLE_APPROACHING,
        S.NAME_CALLED,
        S.MICROWAVE_BEEP,
        S.KETTLE_WHISTLE,
        S.SCHOOL_BELL,
      ]) {
        expect(effective(ctx, c)).toBeNull();
      }
    }
  });

  it('VEHICLE_APPROACHING no tiene regla activa en STREET (CALLE)', () => {
    expect(effective(UserContext.STREET, S.VEHICLE_APPROACHING)).toBeNull();
  });

  it('los contextos históricos UNIVERSITY y WORK conservan sus reglas', () => {
    expect(effective(UserContext.UNIVERSITY, S.SCHOOL_BELL)).toBe(I);
    expect(effective(UserContext.UNIVERSITY, S.NAME_CALLED)).toBe(A);
    expect(effective(UserContext.WORK, S.DOORBELL)).toBe(I);
    expect(effective(UserContext.WORK, S.CAR_HORN)).toBeNull();
  });
});
