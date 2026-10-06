import {
  AlertStatus,
  DetectionOutcome,
  PriorityLevel,
  SoundCategory,
  UserContext,
} from '../domain/index.js';

export enum DevicePlatform {
  WEAR_OS = 'WEAR_OS',
  ANDROID = 'ANDROID',
  WATCH_OS = 'WATCH_OS',
  IOS = 'IOS',
  OTHER = 'OTHER',
}

/** Propietario de uno o varios dispositivos (tabla `users`). */
export interface User {
  id: string;
  name: string;
  email: string | null;
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Dispositivo con su configuración. En Supabase son dos tablas
 * (`devices` + `device_settings`); el repositorio las une.
 */
export interface Device {
  id: string;
  ownerId: string | null;
  name: string;
  platform: DevicePlatform;
  currentContext: UserContext;
  /** Si es null se usa la confianza mínima global. */
  minConfidence: number | null;
  alertsEnabled: boolean;
  createdAt: Date;
  updatedAt: Date;
}

/** Personalización de un sonido en un contexto para un dispositivo (tabla `sound_rules`). */
export interface RuleOverride {
  deviceId: string;
  context: UserContext;
  category: SoundCategory;
  enabled: boolean;
  priority: PriorityLevel;
}

/** Dónde se ejecutó la clasificación. */
export enum ClassificationSource {
  /** El backend clasificó el audio. */
  SERVER = 'SERVER',
  /** El reloj/teléfono clasificó localmente y envió el resultado. */
  DEVICE = 'DEVICE',
}

export interface Prediction {
  category: SoundCategory;
  confidence: number;
  rawLabel: string;
}

/** Registro de cada evento de detección (útil para métricas y re-entrenar la IA). */
export interface Detection {
  id: string;
  deviceId: string;
  context: UserContext;
  source: ClassificationSource;
  classifierName: string;
  classifierVersion: string;
  predictions: Prediction[];
  topLabel: string | null;
  topCategory: SoundCategory | null;
  topConfidence: number | null;
  outcome: DetectionOutcome;
  /** Derivado de `outcome` (columna generada en Supabase). */
  alerted: boolean;
  createdAt: Date;
}

export interface Alert {
  id: string;
  deviceId: string;
  detectionId: string;
  category: SoundCategory;
  priority: PriorityLevel;
  context: UserContext;
  confidence: number;
  icon: string;
  message: string;
  vibrationCount: number;
  status: AlertStatus;
  createdAt: Date;
  acknowledgedAt: Date | null;
}

export type NewUser = Omit<User, 'id' | 'createdAt' | 'updatedAt'>;
export type NewDevice = Omit<Device, 'id' | 'createdAt' | 'updatedAt'>;
export type DeviceChanges = Partial<
  Omit<Device, 'id' | 'createdAt' | 'updatedAt'>
>;
export type NewDetection = Omit<Detection, 'id' | 'createdAt' | 'alerted'>;
export type NewAlert = Omit<
  Alert,
  'id' | 'createdAt' | 'acknowledgedAt' | 'status'
>;

export interface AlertQuery {
  deviceId: string;
  priority?: PriorityLevel;
  status?: AlertStatus;
  context?: UserContext;
  since?: Date;
  limit: number;
}

export interface DetectionQuery {
  deviceId: string;
  alertedOnly?: boolean;
  context?: UserContext;
  since?: Date;
  limit: number;
}
