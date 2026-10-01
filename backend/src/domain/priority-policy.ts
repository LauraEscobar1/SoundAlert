import { PriorityLevel } from './enums.js';

export interface PriorityPolicy {
  level: PriorityLevel;
  /** Número de pulsos de vibración. */
  vibrationCount: number;
  /**
   * Patrón en milisegundos, formato Android/Wear OS:
   * [espera, vibra, pausa, vibra, ...].
   */
  vibrationPattern: number[];
  /** Mayor = más importante. Se usa para ordenar alertas. */
  rank: number;
  /** Color sugerido para la interfaz del reloj. */
  color: string;
  label: string;
}

const PULSE_MS = 400;
const GAP_MS = 250;

function buildPattern(pulses: number): number[] {
  const pattern = [0];
  for (let i = 0; i < pulses; i++) {
    pattern.push(PULSE_MS);
    if (i < pulses - 1) pattern.push(GAP_MS);
  }
  return pattern;
}

export const PRIORITY_POLICIES: Record<PriorityLevel, PriorityPolicy> = {
  [PriorityLevel.DANGER]: {
    level: PriorityLevel.DANGER,
    vibrationCount: 3,
    vibrationPattern: buildPattern(3),
    rank: 3,
    color: '#D32F2F',
    label: 'Peligro',
  },
  [PriorityLevel.ATTENTION]: {
    level: PriorityLevel.ATTENTION,
    vibrationCount: 2,
    vibrationPattern: buildPattern(2),
    rank: 2,
    color: '#F57C00',
    label: 'Atención',
  },
  [PriorityLevel.INFORMATION]: {
    level: PriorityLevel.INFORMATION,
    vibrationCount: 1,
    vibrationPattern: buildPattern(1),
    rank: 1,
    color: '#1976D2',
    label: 'Información',
  },
};
