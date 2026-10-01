import { randomUUID } from 'node:crypto';
import {
  AlertStatus,
  DetectionOutcome,
  SoundCategory,
  UserContext,
} from '../../domain/index.js';
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
} from '../entities.js';
import {
  AlertsRepository,
  DatabaseHealth,
  DetectionsRepository,
  DevicesRepository,
  RuleOverridesRepository,
  UsersRepository,
} from '../repositories.js';

/**
 * Implementaciones en memoria, SOLO para tests (DB_PROVIDER=memory).
 * Los datos se pierden al reiniciar.
 */

export class MemoryDatabaseHealth extends DatabaseHealth {
  async check() {
    return { ok: true };
  }
}

export class MemoryUsersRepository extends UsersRepository {
  private readonly items = new Map<string, User>();

  async create(data: NewUser): Promise<User> {
    const now = new Date();
    const user: User = {
      ...data,
      id: randomUUID(),
      createdAt: now,
      updatedAt: now,
    };
    this.items.set(user.id, user);
    return { ...user };
  }

  async findById(id: string): Promise<User | null> {
    const u = this.items.get(id);
    return u ? { ...u } : null;
  }
}

export class MemoryDevicesRepository extends DevicesRepository {
  private readonly items = new Map<string, Device>();

  async create(data: NewDevice): Promise<Device> {
    const now = new Date();
    const device: Device = {
      ...data,
      id: randomUUID(),
      createdAt: now,
      updatedAt: now,
    };
    this.items.set(device.id, device);
    return { ...device };
  }

  async findById(id: string): Promise<Device | null> {
    const d = this.items.get(id);
    return d ? { ...d } : null;
  }

  async findByOwner(ownerId: string): Promise<Device[]> {
    return [...this.items.values()]
      .filter((d) => d.ownerId === ownerId)
      .map((d) => ({ ...d }));
  }

  async update(id: string, changes: DeviceChanges): Promise<Device | null> {
    const current = this.items.get(id);
    if (!current) return null;
    const updated: Device = { ...current, ...changes, updatedAt: new Date() };
    this.items.set(id, updated);
    return { ...updated };
  }

  async delete(id: string): Promise<boolean> {
    return this.items.delete(id);
  }
}

export class MemoryRuleOverridesRepository extends RuleOverridesRepository {
  private readonly items = new Map<string, RuleOverride>();

  private key(
    o: Pick<RuleOverride, 'deviceId' | 'context' | 'category'>,
  ): string {
    return `${o.deviceId}|${o.context}|${o.category}`;
  }

  async findByDeviceAndContext(
    deviceId: string,
    context: UserContext,
  ): Promise<RuleOverride[]> {
    return [...this.items.values()]
      .filter((o) => o.deviceId === deviceId && o.context === context)
      .map((o) => ({ ...o }));
  }

  async upsertMany(overrides: RuleOverride[]): Promise<void> {
    for (const o of overrides) this.items.set(this.key(o), { ...o });
  }

  async deleteByDeviceAndContext(
    deviceId: string,
    context: UserContext,
  ): Promise<void> {
    for (const [k, o] of this.items) {
      if (o.deviceId === deviceId && o.context === context)
        this.items.delete(k);
    }
  }
}

export class MemoryDetectionsRepository extends DetectionsRepository {
  private readonly items: Detection[] = [];

  async create(data: NewDetection): Promise<Detection> {
    const detection: Detection = {
      ...data,
      id: randomUUID(),
      alerted: data.outcome === DetectionOutcome.ALERTED,
      createdAt: new Date(),
    };
    this.items.push(detection);
    return { ...detection };
  }

  async findById(id: string): Promise<Detection | null> {
    const d = this.items.find((x) => x.id === id);
    return d ? { ...d } : null;
  }

  async find(q: DetectionQuery): Promise<Detection[]> {
    return this.items
      .filter((d) => d.deviceId === q.deviceId)
      .filter((d) => !q.alertedOnly || d.alerted)
      .filter((d) => !q.since || d.createdAt >= q.since)
      .reverse()
      .slice(0, q.limit)
      .map((d) => ({ ...d }));
  }
}

export class MemoryAlertsRepository extends AlertsRepository {
  private readonly items: Alert[] = [];

  async create(data: NewAlert): Promise<Alert> {
    const alert: Alert = {
      ...data,
      id: randomUUID(),
      status: AlertStatus.ACTIVE,
      createdAt: new Date(),
      acknowledgedAt: null,
    };
    this.items.push(alert);
    return { ...alert };
  }

  async findById(id: string): Promise<Alert | null> {
    const a = this.items.find((x) => x.id === id);
    return a ? { ...a } : null;
  }

  async findByDetection(detectionId: string): Promise<Alert | null> {
    const a = this.items.find((x) => x.detectionId === detectionId);
    return a ? { ...a } : null;
  }

  async find(q: AlertQuery): Promise<Alert[]> {
    // Se insertan en orden cronológico: invertir da "más recientes primero".
    return [...this.items]
      .reverse()
      .filter((a) => a.deviceId === q.deviceId)
      .filter((a) => !q.priority || a.priority === q.priority)
      .filter((a) => !q.status || a.status === q.status)
      .filter((a) => !q.since || a.createdAt >= q.since)
      .slice(0, q.limit)
      .map((a) => ({ ...a }));
  }

  async findLatest(
    deviceId: string,
    category: SoundCategory,
  ): Promise<Alert | null> {
    const latest = [...this.items]
      .reverse()
      .find((a) => a.deviceId === deviceId && a.category === category);
    return latest ? { ...latest } : null;
  }

  async acknowledge(id: string, at: Date): Promise<Alert | null> {
    const a = this.items.find((x) => x.id === id);
    if (!a) return null;
    if (a.status === AlertStatus.ACTIVE) {
      a.status = AlertStatus.ACKNOWLEDGED;
      a.acknowledgedAt = at;
    }
    return { ...a };
  }
}
