import { Logger } from '@nestjs/common';
import { LabelMapper } from './label-mapper.js';
import {
  AudioInput,
  ClassificationResult,
  SoundClassifier,
} from './sound-classifier.interface.js';

/**
 * Clasificador provisional mientras se elige el modelo de IA.
 * No analiza el audio: si recibe `metadata.simulatedLabel` lo devuelve con
 * la confianza de `metadata.simulatedConfidence` (0.9 por defecto); si no,
 * devuelve una lista vacía. Sirve para probar el flujo completo de punta a punta.
 */
export class MockSoundClassifier implements SoundClassifier {
  readonly info = { name: 'mock', version: '0.0.0' };
  private readonly logger = new Logger(MockSoundClassifier.name);

  constructor(private readonly mapper = new LabelMapper()) {}

  isReady(): boolean {
    return true;
  }

  async classify(input: AudioInput): Promise<ClassificationResult[]> {
    const label = input.metadata?.simulatedLabel;
    if (!label) {
      this.logger.debug(
        `Audio recibido (${input.data.length} bytes) sin simulatedLabel`,
      );
      return [];
    }
    const score = Number(input.metadata?.simulatedConfidence ?? 0.9);
    return this.mapper.map([
      { label, score: Number.isFinite(score) ? score : 0.9 },
    ]);
  }
}
