import {
  getDefaultRules,
  PRIORITY_POLICIES,
  PriorityLevel,
  SoundCategory,
  UserContext,
} from './index.js';

describe('Política de prioridad (vibración)', () => {
  it.each([
    [PriorityLevel.DANGER, 3, [0, 400, 250, 400, 250, 400]],
    [PriorityLevel.ATTENTION, 2, [0, 400, 250, 400]],
    [PriorityLevel.INFORMATION, 1, [0, 400]],
  ])('%s → %i vibraciones', (level, count, pattern) => {
    expect(PRIORITY_POLICIES[level].vibrationCount).toBe(count);
    expect(PRIORITY_POLICIES[level].vibrationPattern).toEqual(pattern);
  });

  it('DANGER > ATTENTION > INFORMATION', () => {
    const rank = (l: PriorityLevel) => PRIORITY_POLICIES[l].rank;
    expect(rank(PriorityLevel.DANGER)).toBeGreaterThan(
      rank(PriorityLevel.ATTENTION),
    );
    expect(rank(PriorityLevel.ATTENTION)).toBeGreaterThan(
      rank(PriorityLevel.INFORMATION),
    );
  });
});

describe('Reglas por contexto', () => {
  const rule = (ctx: UserContext, category: SoundCategory) =>
    getDefaultRules(ctx).find((r) => r.category === category);

  it('CALLE: sirena = DANGER, bocina = ATTENTION', () => {
    expect(rule(UserContext.STREET, SoundCategory.SIREN)).toEqual({
      category: SoundCategory.SIREN,
      enabled: true,
      priority: PriorityLevel.DANGER,
    });
    expect(rule(UserContext.STREET, SoundCategory.CAR_HORN)).toMatchObject({
      enabled: true,
      priority: PriorityLevel.ATTENTION,
    });
  });

  it('CASA: bocina no es relevante, timbre = INFORMATION', () => {
    expect(rule(UserContext.HOME, SoundCategory.CAR_HORN)?.enabled).toBe(false);
    expect(rule(UserContext.HOME, SoundCategory.DOORBELL)).toMatchObject({
      enabled: true,
      priority: PriorityLevel.INFORMATION,
    });
  });

  it('los sonidos de peligro están activos en todos los contextos', () => {
    for (const ctx of Object.values(UserContext)) {
      for (const c of [
        SoundCategory.SIREN,
        SoundCategory.FIRE_ALARM,
        SoundCategory.SMOKE_ALARM,
      ]) {
        expect(rule(ctx, c)).toMatchObject({
          enabled: true,
          priority: PriorityLevel.DANGER,
        });
      }
    }
  });

  it('UNKNOWN nunca tiene regla', () => {
    for (const ctx of Object.values(UserContext)) {
      expect(rule(ctx, SoundCategory.UNKNOWN)).toBeUndefined();
    }
  });
});
