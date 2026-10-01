import {
  ContextRule,
  DetectionOutcome,
  PRIORITY_POLICIES,
  SoundCategory,
} from '../../domain/index.js';
import { Prediction } from '../../database/entities.js';

export { DetectionOutcome };

export interface EngineDecision {
  outcome: DetectionOutcome;
  /** Predicción elegida para alertar (solo si outcome = ALERTED). */
  prediction?: Prediction;
  rule?: ContextRule;
}

/**
 * Decide si un conjunto de predicciones debe generar una alerta.
 * Lógica pura (sin E/S), fácil de testear y de ajustar.
 *
 * 1. Descarta UNKNOWN y predicciones bajo el umbral de confianza.
 * 2. Descarta sonidos deshabilitados en el contexto actual.
 * 3. Entre los candidatos elige el de mayor prioridad y, a igualdad, mayor confianza.
 */
export function evaluate(
  predictions: Prediction[],
  rules: ContextRule[],
  minConfidence: number,
): EngineDecision {
  if (!predictions.length) return { outcome: DetectionOutcome.NO_PREDICTIONS };

  const known = predictions.filter((p) => p.category !== SoundCategory.UNKNOWN);
  if (!known.length) return { outcome: DetectionOutcome.UNKNOWN_SOUND };

  const confident = known.filter((p) => p.confidence >= minConfidence);
  if (!confident.length) return { outcome: DetectionOutcome.LOW_CONFIDENCE };

  const ruleMap = new Map(rules.map((r) => [r.category, r]));
  const candidates = confident
    .map((prediction) => ({
      prediction,
      rule: ruleMap.get(prediction.category),
    }))
    .filter(
      (c): c is { prediction: Prediction; rule: ContextRule } =>
        !!c.rule?.enabled,
    );
  if (!candidates.length)
    return { outcome: DetectionOutcome.DISABLED_IN_CONTEXT };

  candidates.sort(
    (a, b) =>
      PRIORITY_POLICIES[b.rule.priority].rank -
        PRIORITY_POLICIES[a.rule.priority].rank ||
      b.prediction.confidence - a.prediction.confidence,
  );
  return { outcome: DetectionOutcome.ALERTED, ...candidates[0] };
}
