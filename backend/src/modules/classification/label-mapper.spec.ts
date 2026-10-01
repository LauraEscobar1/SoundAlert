import { SoundCategory } from '../../domain/index.js';
import { LabelMapper } from './label-mapper.js';

describe('LabelMapper', () => {
  const mapper = new LabelMapper();

  it('traduce etiquetas AudioSet sin importar mayúsculas', () => {
    expect(mapper.toCategory('Siren')).toBe(SoundCategory.SIREN);
    expect(mapper.toCategory('Vehicle horn, car horn, honking')).toBe(
      SoundCategory.CAR_HORN,
    );
  });

  it('acepta directamente una SoundCategory', () => {
    expect(mapper.toCategory('doorbell')).toBe(SoundCategory.DOORBELL);
    expect(mapper.toCategory('FIRE_ALARM')).toBe(SoundCategory.FIRE_ALARM);
  });

  it('etiquetas desconocidas → UNKNOWN', () => {
    expect(mapper.toCategory('Music')).toBe(SoundCategory.UNKNOWN);
  });

  it('agrupa por categoría quedándose con la mayor confianza y ordena', () => {
    const r = mapper.map([
      { label: 'Doorbell', score: 0.5 },
      { label: 'Siren', score: 0.6 },
      { label: 'Ambulance (siren)', score: 0.8 },
    ]);
    expect(r).toEqual([
      {
        category: SoundCategory.SIREN,
        confidence: 0.8,
        rawLabel: 'Ambulance (siren)',
      },
      {
        category: SoundCategory.DOORBELL,
        confidence: 0.5,
        rawLabel: 'Doorbell',
      },
    ]);
  });
});
