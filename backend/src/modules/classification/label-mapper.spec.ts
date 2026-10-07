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

  it('DOG_BARK solo con "Bark": "Dog" sola es UNKNOWN', () => {
    expect(mapper.toCategory('Bark')).toBe(SoundCategory.DOG_BARK);
    expect(mapper.toCategory('Dog')).toBe(SoundCategory.UNKNOWN);
  });

  it('clases genéricas o engañosas quedan UNKNOWN', () => {
    for (const label of [
      'Car passing by',
      'Microwave oven',
      'Speech',
      'Music',
      'Television',
      'Walk, footsteps',
      'Sine wave',
      'Honk',
      'French horn',
      'Motorcycle',
      'Train',
      'Vehicle',
      'Animal',
    ]) {
      expect(mapper.toCategory(label)).toBe(SoundCategory.UNKNOWN);
    }
  });

  it('mismas clases específicas que el reloj', () => {
    expect(mapper.toCategory('Shatter')).toBe(SoundCategory.GLASS_BREAK);
    expect(mapper.toCategory('Screaming')).toBe(SoundCategory.SCREAM);
    expect(mapper.toCategory('Skidding')).toBe(SoundCategory.TIRE_SKID);
    expect(mapper.toCategory('Reversing beeps')).toBe(
      SoundCategory.REVERSING_VEHICLE,
    );
    expect(mapper.toCategory('Train horn')).toBe(SoundCategory.TRAIN_HORN);
    expect(mapper.toCategory('Car alarm')).toBe(SoundCategory.CAR_ALARM);
    expect(mapper.toCategory('Alarm')).toBe(SoundCategory.GENERAL_ALARM);
  });
});
