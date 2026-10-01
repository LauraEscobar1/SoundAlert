import { SoundCategory, UserContext } from '../domain/index.js';
import {
  Alert,
  AlertQuery,
  Detection,
  DetectionQuery,
  Device,
  DeviceChanges,
  NewAlert,
  NewDetection,
  NewDevice,
  NewUser,
  RuleOverride,
  User,
} from './entities.js';

/**
 * Contratos de persistencia. Los servicios dependen de estas clases
 * abstractas; `DatabaseModule` decide la implementación (Supabase en
 * ejecución normal, memoria solo si se pide explícitamente para tests).
 */

export abstract class DatabaseHealth {
  /** Comprueba que la base de datos responde y el esquema existe. */
  abstract check(): Promise<{ ok: boolean; error?: string }>;
}

export abstract class UsersRepository {
  abstract create(data: NewUser): Promise<User>;
  abstract findById(id: string): Promise<User | null>;
}

export abstract class DevicesRepository {
  abstract create(data: NewDevice): Promise<Device>;
  abstract findById(id: string): Promise<Device | null>;
  abstract findByOwner(ownerId: string): Promise<Device[]>;
  abstract update(id: string, changes: DeviceChanges): Promise<Device | null>;
  abstract delete(id: string): Promise<boolean>;
}

export abstract class RuleOverridesRepository {
  abstract findByDeviceAndContext(
    deviceId: string,
    context: UserContext,
  ): Promise<RuleOverride[]>;
  abstract upsertMany(overrides: RuleOverride[]): Promise<void>;
  abstract deleteByDeviceAndContext(
    deviceId: string,
    context: UserContext,
  ): Promise<void>;
}

export abstract class DetectionsRepository {
  abstract create(data: NewDetection): Promise<Detection>;
  abstract findById(id: string): Promise<Detection | null>;
  abstract find(query: DetectionQuery): Promise<Detection[]>;
}

export abstract class AlertsRepository {
  abstract create(data: NewAlert): Promise<Alert>;
  abstract findById(id: string): Promise<Alert | null>;
  abstract findByDetection(detectionId: string): Promise<Alert | null>;
  abstract find(query: AlertQuery): Promise<Alert[]>;
  abstract findLatest(
    deviceId: string,
    category: SoundCategory,
  ): Promise<Alert | null>;
  abstract acknowledge(id: string, at: Date): Promise<Alert | null>;
}
