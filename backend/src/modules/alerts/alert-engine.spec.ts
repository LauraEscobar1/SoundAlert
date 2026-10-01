import {
  getDefaultRules,
  PriorityLevel,
  SoundCategory,
  UserContext,
} from '../../domain/index.js';
import { DetectionOutcome, evaluate } from './alert-engine.js';

const p = (category: SoundCategory, confidence: number) => ({
  category,
  confidence,
  rawLabel: category,
});

describe('evaluate (motor de alertas)', () => {
  const street = getDefaultRules(UserContext.STREET);
  const home = getDefaultRules(UserContext.HOME);

  it('sin predicciones no alerta', () => {
    expect(evaluate([], street, 0.6).outcome).toBe(
      DetectionOutcome.NO_PREDICTIONS,
    );
  });

  it('sonido desconocido no alerta', () => {
    expect(
      evaluate([p(SoundCategory.UNKNOWN, 0.99)], street, 0.6).outcome,
    ).toBe(DetectionOutcome.UNKNOWN_SOUND);
  });

  it('respeta el umbral de confianza', () => {
    expect(evaluate([p(SoundCategory.SIREN, 0.4)], street, 0.6).outcome).toBe(
      DetectionOutcome.LOW_CONFIDENCE,
    );
  });

  it('ignora sonidos deshabilitados en el contexto (bocina en casa)', () => {
    expect(evaluate([p(SoundCategory.CAR_HORN, 0.9)], home, 0.6).outcome).toBe(
      DetectionOutcome.DISABLED_IN_CONTEXT,
    );
  });

  it('prioriza peligro sobre atención aunque tenga menor confianza', () => {
    const d = evaluate(
      [p(SoundCategory.CAR_HORN, 0.95), p(SoundCategory.SIREN, 0.7)],
      street,
      0.6,
    );
    expect(d.outcome).toBe(DetectionOutcome.ALERTED);
    expect(d.prediction?.category).toBe(SoundCategory.SIREN);
    expect(d.rule?.priority).toBe(PriorityLevel.DANGER);
  });

  it('las alarmas de peligro están activas en todos los contextos', () => {
    for (const ctx of Object.values(UserContext)) {
      const d = evaluate(
        [p(SoundCategory.FIRE_ALARM, 0.8)],
        getDefaultRules(ctx),
        0.6,
      );
      expect(d.outcome).toBe(DetectionOutcome.ALERTED);
    }
  });
});
