import { SoundCategory } from '../../domain/index.js';
import { MockSoundClassifier } from './mock-sound-classifier.js';
import {
  AudioFormat,
  AudioInput,
  SoundClassifier,
} from './sound-classifier.interface.js';

const input = (metadata?: Record<string, string>): AudioInput => ({
  data: Buffer.alloc(16),
  format: AudioFormat.PCM_16LE,
  sampleRate: 16000,
  channels: 1,
  metadata,
});

describe('MockSoundClassifier', () => {
  // Se usa a través del contrato, igual que el resto del backend.
  const classifier: SoundClassifier = new MockSoundClassifier();

  it('se identifica como mock y está listo', () => {
    expect(classifier.info).toEqual({ name: 'mock', version: '0.0.0' });
    expect(classifier.isReady()).toBe(true);
  });

  it.each([
    ['Siren', SoundCategory.SIREN],
    ['Vehicle horn', SoundCategory.CAR_HORN],
    ['Vehicle horn, car horn, honking', SoundCategory.CAR_HORN],
    ['Doorbell', SoundCategory.DOORBELL],
    ['FIRE_ALARM', SoundCategory.FIRE_ALARM],
  ])('"%s" → %s con confianza 0.9 por defecto', async (label, category) => {
    expect(await classifier.classify(input({ simulatedLabel: label }))).toEqual(
      [{ category, confidence: 0.9, rawLabel: label }],
    );
  });

  it('usa simulatedConfidence', async () => {
    const [p] = await classifier.classify(
      input({ simulatedLabel: 'Siren', simulatedConfidence: '0.42' }),
    );
    expect(p.confidence).toBe(0.42);
  });

  it('etiqueta desconocida → UNKNOWN', async () => {
    const [p] = await classifier.classify(input({ simulatedLabel: 'Music' }));
    expect(p.category).toBe(SoundCategory.UNKNOWN);
  });

  it('sin simulatedLabel no devuelve predicciones', async () => {
    expect(await classifier.classify(input())).toEqual([]);
  });
});
