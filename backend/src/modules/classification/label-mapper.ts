import { SoundCategory } from '../../domain/index.js';
import {
  ClassificationResult,
  RawPrediction,
} from './sound-classifier.interface.js';

/**
 * Traduce etiquetas nativas del modelo a `SoundCategory`.
 * Las entradas por defecto usan nombres de la ontología AudioSet
 * (la que usan YAMNet, PANNs, AST...). Si el modelo elegido usa otras
 * etiquetas, basta con pasar un mapa distinto.
 */
export const DEFAULT_LABEL_MAP: Record<string, SoundCategory> = {
  // Peligro
  siren: SoundCategory.SIREN,
  'civil defense siren': SoundCategory.SIREN,
  'police car (siren)': SoundCategory.SIREN,
  'ambulance (siren)': SoundCategory.SIREN,
  'fire engine, fire truck (siren)': SoundCategory.SIREN,
  'emergency vehicle': SoundCategory.SIREN,
  'fire alarm': SoundCategory.FIRE_ALARM,
  'smoke detector, smoke alarm': SoundCategory.SMOKE_ALARM,
  // Atención
  'vehicle horn, car horn, honking': SoundCategory.CAR_HORN,
  'vehicle horn': SoundCategory.CAR_HORN,
  'car horn': SoundCategory.CAR_HORN,
  honking: SoundCategory.CAR_HORN,
  'air horn, truck horn': SoundCategory.CAR_HORN,
  'car passing by': SoundCategory.VEHICLE_APPROACHING,
  'bicycle bell': SoundCategory.BICYCLE_BELL,
  'baby cry, infant cry': SoundCategory.BABY_CRYING,
  // Información
  doorbell: SoundCategory.DOORBELL,
  'ding-dong': SoundCategory.DOORBELL,
  knock: SoundCategory.DOOR_KNOCK,
  'telephone bell ringing': SoundCategory.PHONE_RING,
  ringtone: SoundCategory.PHONE_RING,
  bark: SoundCategory.DOG_BARK,
  dog: SoundCategory.DOG_BARK,
  'alarm clock': SoundCategory.ALARM_CLOCK,
  'microwave oven': SoundCategory.MICROWAVE_BEEP,
  kettle: SoundCategory.KETTLE_WHISTLE,
  'water tap, faucet': SoundCategory.WATER_RUNNING,
  'school bell': SoundCategory.SCHOOL_BELL,
};

export class LabelMapper {
  private readonly labels: Map<string, SoundCategory>;

  constructor(map: Record<string, SoundCategory> = DEFAULT_LABEL_MAP) {
    this.labels = new Map(
      Object.entries(map).map(([k, v]) => [k.toLowerCase(), v]),
    );
  }

  toCategory(label: string): SoundCategory {
    const key = label.trim().toLowerCase();
    if (this.labels.has(key)) return this.labels.get(key)!;
    if (
      (Object.values(SoundCategory) as string[]).includes(
        label.trim().toUpperCase(),
      )
    ) {
      return label.trim().toUpperCase() as SoundCategory;
    }
    return SoundCategory.UNKNOWN;
  }

  /**
   * Convierte predicciones crudas en resultados de dominio. Si varias
   * etiquetas caen en la misma categoría, se conserva la de mayor score.
   */
  map(predictions: RawPrediction[]): ClassificationResult[] {
    const best = new Map<SoundCategory, ClassificationResult>();
    for (const p of predictions) {
      const category = this.toCategory(p.label);
      const confidence = Math.max(0, Math.min(1, p.score));
      const current = best.get(category);
      if (!current || confidence > current.confidence) {
        best.set(category, { category, confidence, rawLabel: p.label });
      }
    }
    return [...best.values()].sort((a, b) => b.confidence - a.confidence);
  }
}
