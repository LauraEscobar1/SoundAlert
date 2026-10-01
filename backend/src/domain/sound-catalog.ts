import { PriorityLevel, SoundCategory } from './enums.js';

export interface SoundDefinition {
  category: SoundCategory;
  name: string;
  /** Identificador de icono que la app del reloj traduce a un recurso gráfico. */
  icon: string;
  defaultPriority: PriorityLevel;
  /** Texto corto que se muestra en el reloj. */
  shortMessage: string;
}

const D = PriorityLevel.DANGER;
const A = PriorityLevel.ATTENTION;
const I = PriorityLevel.INFORMATION;

const definitions: SoundDefinition[] = [
  {
    category: SoundCategory.SIREN,
    name: 'Sirena',
    icon: 'siren',
    defaultPriority: D,
    shortMessage: 'Sirena cerca',
  },
  {
    category: SoundCategory.FIRE_ALARM,
    name: 'Alarma de incendio',
    icon: 'fire_alarm',
    defaultPriority: D,
    shortMessage: 'Alarma de incendio',
  },
  {
    category: SoundCategory.SMOKE_ALARM,
    name: 'Detector de humo',
    icon: 'smoke',
    defaultPriority: D,
    shortMessage: 'Detector de humo',
  },
  {
    category: SoundCategory.CAR_HORN,
    name: 'Bocina de vehículo',
    icon: 'car_horn',
    defaultPriority: A,
    shortMessage: 'Bocina',
  },
  {
    category: SoundCategory.VEHICLE_APPROACHING,
    name: 'Vehículo acercándose',
    icon: 'car',
    defaultPriority: A,
    shortMessage: 'Vehículo cerca',
  },
  {
    category: SoundCategory.BICYCLE_BELL,
    name: 'Timbre de bicicleta',
    icon: 'bicycle',
    defaultPriority: A,
    shortMessage: 'Bicicleta',
  },
  {
    category: SoundCategory.BABY_CRYING,
    name: 'Bebé llorando',
    icon: 'baby',
    defaultPriority: A,
    shortMessage: 'Bebé llorando',
  },
  {
    category: SoundCategory.NAME_CALLED,
    name: 'Te están llamando',
    icon: 'voice',
    defaultPriority: A,
    shortMessage: 'Te llaman',
  },
  {
    category: SoundCategory.DOORBELL,
    name: 'Timbre de puerta',
    icon: 'doorbell',
    defaultPriority: I,
    shortMessage: 'Timbre',
  },
  {
    category: SoundCategory.DOOR_KNOCK,
    name: 'Golpe en la puerta',
    icon: 'door',
    defaultPriority: I,
    shortMessage: 'Tocan la puerta',
  },
  {
    category: SoundCategory.PHONE_RING,
    name: 'Teléfono sonando',
    icon: 'phone',
    defaultPriority: I,
    shortMessage: 'Teléfono',
  },
  {
    category: SoundCategory.DOG_BARK,
    name: 'Perro ladrando',
    icon: 'dog',
    defaultPriority: I,
    shortMessage: 'Perro ladrando',
  },
  {
    category: SoundCategory.ALARM_CLOCK,
    name: 'Despertador',
    icon: 'alarm_clock',
    defaultPriority: I,
    shortMessage: 'Despertador',
  },
  {
    category: SoundCategory.MICROWAVE_BEEP,
    name: 'Microondas',
    icon: 'microwave',
    defaultPriority: I,
    shortMessage: 'Microondas listo',
  },
  {
    category: SoundCategory.KETTLE_WHISTLE,
    name: 'Tetera',
    icon: 'kettle',
    defaultPriority: I,
    shortMessage: 'Tetera',
  },
  {
    category: SoundCategory.WATER_RUNNING,
    name: 'Agua corriendo',
    icon: 'water',
    defaultPriority: I,
    shortMessage: 'Agua corriendo',
  },
  {
    category: SoundCategory.SCHOOL_BELL,
    name: 'Timbre de clase',
    icon: 'bell',
    defaultPriority: I,
    shortMessage: 'Timbre de clase',
  },
  {
    category: SoundCategory.UNKNOWN,
    name: 'Sonido desconocido',
    icon: 'unknown',
    defaultPriority: I,
    shortMessage: 'Sonido',
  },
];

export const SOUND_CATALOG: ReadonlyMap<SoundCategory, SoundDefinition> =
  new Map(definitions.map((d) => [d.category, d]));

export function getSoundDefinition(category: SoundCategory): SoundDefinition {
  return (
    SOUND_CATALOG.get(category) ?? SOUND_CATALOG.get(SoundCategory.UNKNOWN)!
  );
}
