import { InternalServerErrorException, Logger } from '@nestjs/common';
import { SupabaseClient } from '@supabase/supabase-js';
import {
  AlertStatus,
  DetectionOutcome,
  PriorityLevel,
  SoundCategory,
  UserContext,
} from '../../domain/index.js';
import {
  Alert,
  AlertQuery,
  ClassificationSource,
  Detection,
  DetectionQuery,
  Device,
  DeviceChanges,
  DevicePlatform,
  NewAlert,
  NewDetection,
  NewDevice,
  NewUser,
  Prediction,
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

/** Implementaciones sobre Supabase (PostgreSQL). Esquema en supabase/migrations. */

type Row = Record<string, any>;
type DbError = { message: string; code?: string } | null;

const logger = new Logger('Supabase');

function check<T>(result: { data: T; error: DbError }, op: string): T {
  if (result.error) {
    // El detalle va al log del servidor; al cliente solo un mensaje genérico.
    logger.error(
      `${op}: [${result.error.code ?? '?'}] ${result.error.message}`,
    );
    throw new InternalServerErrorException(`Error de base de datos (${op})`);
  }
  return result.data;
}

const date = (v: string | null): Date | null => (v ? new Date(v) : null);
const num = (v: string | number | null): number | null =>
  v === null ? null : Number(v);

// ---------- Health ----------

export class SupabaseDatabaseHealth extends DatabaseHealth {
  constructor(private readonly db: SupabaseClient) {
    super();
  }

  async check() {
    const { error } = await this.db
      .from('contexts')
      .select('code', { head: true, count: 'exact' });
    return error ? { ok: false, error: error.message } : { ok: true };
  }
}

// ---------- Users ----------

function toUser(r: Row): User {
  return {
    id: r.id,
    name: r.name,
    email: r.email,
    createdAt: new Date(r.created_at),
    updatedAt: new Date(r.updated_at),
  };
}

export class SupabaseUsersRepository extends UsersRepository {
  constructor(private readonly db: SupabaseClient) {
    super();
  }

  async create(data: NewUser): Promise<User> {
    const row = check(
      await this.db
        .from('users')
        .insert({ name: data.name, email: data.email })
        .select()
        .single(),
      'crear usuario',
    );
    return toUser(row);
  }

  async findById(id: string): Promise<User | null> {
    const row = check(
      await this.db.from('users').select().eq('id', id).maybeSingle(),
      'buscar usuario',
    );
    return row ? toUser(row) : null;
  }
}

// ---------- Devices (+ device_settings) ----------

const DEVICE_SELECT = '*, device_settings(min_confidence, alerts_enabled)';

function toDevice(r: Row): Device {
  // Relación 1:1: PostgREST devuelve un objeto (o array si no detecta la unicidad).
  const s: Row =
    (Array.isArray(r.device_settings)
      ? r.device_settings[0]
      : r.device_settings) ?? {};
  return {
    id: r.id,
    ownerId: r.owner_id,
    name: r.name,
    platform: r.platform as DevicePlatform,
    currentContext: r.current_context as UserContext,
    minConfidence: num(s.min_confidence ?? null),
    alertsEnabled: s.alerts_enabled ?? true,
    createdAt: new Date(r.created_at),
    updatedAt: new Date(r.updated_at),
  };
}

function deviceRow(d: DeviceChanges): Row {
  const row: Row = {};
  if (d.ownerId !== undefined) row.owner_id = d.ownerId;
  if (d.name !== undefined) row.name = d.name;
  if (d.platform !== undefined) row.platform = d.platform;
  if (d.currentContext !== undefined) row.current_context = d.currentContext;
  return row;
}

function settingsRow(d: DeviceChanges): Row {
  const row: Row = {};
  if (d.minConfidence !== undefined) row.min_confidence = d.minConfidence;
  if (d.alertsEnabled !== undefined) row.alerts_enabled = d.alertsEnabled;
  return row;
}

export class SupabaseDevicesRepository extends DevicesRepository {
  constructor(private readonly db: SupabaseClient) {
    super();
  }

  async create(data: NewDevice): Promise<Device> {
    // Un trigger crea la fila de device_settings con valores por defecto.
    const { id } = check(
      await this.db
        .from('devices')
        .insert(deviceRow(data))
        .select('id')
        .single(),
      'crear dispositivo',
    ) as Row;
    const settings = settingsRow(data);
    if (Object.keys(settings).length) await this.updateSettings(id, settings);
    return (await this.findById(id))!;
  }

  async findById(id: string): Promise<Device | null> {
    const row = check(
      await this.db
        .from('devices')
        .select(DEVICE_SELECT)
        .eq('id', id)
        .maybeSingle(),
      'buscar dispositivo',
    );
    return row ? toDevice(row) : null;
  }

  async findByOwner(ownerId: string): Promise<Device[]> {
    const rows = check(
      await this.db
        .from('devices')
        .select(DEVICE_SELECT)
        .eq('owner_id', ownerId)
        .order('created_at', { ascending: true }),
      'listar dispositivos',
    );
    return (rows ?? []).map(toDevice);
  }

  async update(id: string, changes: DeviceChanges): Promise<Device | null> {
    const device = deviceRow(changes);
    const settings = settingsRow(changes);
    if (Object.keys(device).length) {
      const rows = check(
        await this.db.from('devices').update(device).eq('id', id).select('id'),
        'actualizar dispositivo',
      );
      if (!rows?.length) return null;
    }
    if (Object.keys(settings).length) await this.updateSettings(id, settings);
    return this.findById(id);
  }

  async delete(id: string): Promise<boolean> {
    const rows = check(
      await this.db.from('devices').delete().eq('id', id).select('id'),
      'eliminar dispositivo',
    );
    return (rows ?? []).length > 0;
  }

  private async updateSettings(deviceId: string, settings: Row): Promise<void> {
    check(
      await this.db
        .from('device_settings')
        .update(settings)
        .eq('device_id', deviceId),
      'actualizar configuración',
    );
  }
}

// ---------- Sound rules ----------

export class SupabaseRuleOverridesRepository extends RuleOverridesRepository {
  constructor(private readonly db: SupabaseClient) {
    super();
  }

  async findByDeviceAndContext(
    deviceId: string,
    context: UserContext,
  ): Promise<RuleOverride[]> {
    const rows = check(
      await this.db
        .from('sound_rules')
        .select()
        .eq('device_id', deviceId)
        .eq('context', context),
      'leer reglas',
    );
    return (rows ?? []).map((r: Row) => ({
      deviceId: r.device_id,
      context: r.context as UserContext,
      category: r.category as SoundCategory,
      enabled: r.enabled,
      priority: r.priority as PriorityLevel,
    }));
  }

  async upsertMany(overrides: RuleOverride[]): Promise<void> {
    if (!overrides.length) return;
    check(
      await this.db.from('sound_rules').upsert(
        overrides.map((o) => ({
          device_id: o.deviceId,
          context: o.context,
          category: o.category,
          enabled: o.enabled,
          priority: o.priority,
        })),
        { onConflict: 'device_id,context,category' },
      ),
      'guardar reglas',
    );
  }

  async deleteByDeviceAndContext(
    deviceId: string,
    context: UserContext,
  ): Promise<void> {
    check(
      await this.db
        .from('sound_rules')
        .delete()
        .eq('device_id', deviceId)
        .eq('context', context),
      'restablecer reglas',
    );
  }
}

// ---------- Detections ----------

function toDetection(r: Row): Detection {
  return {
    id: r.id,
    deviceId: r.device_id,
    context: r.context as UserContext,
    source: r.source as ClassificationSource,
    classifierName: r.classifier_name,
    classifierVersion: r.classifier_version,
    predictions: r.predictions as Prediction[],
    topLabel: r.top_label,
    topCategory: r.top_category as SoundCategory | null,
    topConfidence: num(r.top_confidence),
    outcome: r.outcome as DetectionOutcome,
    alerted: r.alerted,
    createdAt: new Date(r.created_at),
  };
}

export class SupabaseDetectionsRepository extends DetectionsRepository {
  constructor(private readonly db: SupabaseClient) {
    super();
  }

  async create(data: NewDetection): Promise<Detection> {
    const row = check(
      await this.db
        .from('detections')
        .insert({
          device_id: data.deviceId,
          context: data.context,
          source: data.source,
          classifier_name: data.classifierName,
          classifier_version: data.classifierVersion,
          predictions: data.predictions,
          top_label: data.topLabel,
          top_category: data.topCategory,
          top_confidence: data.topConfidence,
          outcome: data.outcome,
        })
        .select()
        .single(),
      'guardar detección',
    );
    return toDetection(row);
  }

  async findById(id: string): Promise<Detection | null> {
    const row = check(
      await this.db.from('detections').select().eq('id', id).maybeSingle(),
      'buscar detección',
    );
    return row ? toDetection(row) : null;
  }

  async find(q: DetectionQuery): Promise<Detection[]> {
    let query = this.db
      .from('detections')
      .select()
      .eq('device_id', q.deviceId)
      .order('created_at', { ascending: false })
      .limit(q.limit);
    if (q.alertedOnly) query = query.eq('alerted', true);
    if (q.since) query = query.gte('created_at', q.since.toISOString());
    const rows = check(await query, 'listar detecciones');
    return (rows ?? []).map(toDetection);
  }
}

// ---------- Alerts ----------

function toAlert(r: Row): Alert {
  return {
    id: r.id,
    deviceId: r.device_id,
    detectionId: r.detection_id,
    category: r.category as SoundCategory,
    priority: r.priority as PriorityLevel,
    context: r.context as UserContext,
    confidence: Number(r.confidence),
    icon: r.icon,
    message: r.message,
    vibrationCount: r.vibration_count,
    status: r.status as AlertStatus,
    createdAt: new Date(r.created_at),
    acknowledgedAt: date(r.acknowledged_at),
  };
}

export class SupabaseAlertsRepository extends AlertsRepository {
  constructor(private readonly db: SupabaseClient) {
    super();
  }

  async create(data: NewAlert): Promise<Alert> {
    const row = check(
      await this.db
        .from('alerts')
        .insert({
          device_id: data.deviceId,
          detection_id: data.detectionId,
          category: data.category,
          priority: data.priority,
          context: data.context,
          confidence: data.confidence,
          icon: data.icon,
          message: data.message,
          vibration_count: data.vibrationCount,
        })
        .select()
        .single(),
      'crear alerta',
    );
    return toAlert(row);
  }

  async findById(id: string): Promise<Alert | null> {
    const row = check(
      await this.db.from('alerts').select().eq('id', id).maybeSingle(),
      'buscar alerta',
    );
    return row ? toAlert(row) : null;
  }

  async findByDetection(detectionId: string): Promise<Alert | null> {
    const row = check(
      await this.db
        .from('alerts')
        .select()
        .eq('detection_id', detectionId)
        .maybeSingle(),
      'buscar alerta de detección',
    );
    return row ? toAlert(row) : null;
  }

  async find(q: AlertQuery): Promise<Alert[]> {
    let query = this.db
      .from('alerts')
      .select()
      .eq('device_id', q.deviceId)
      .order('created_at', { ascending: false })
      .limit(q.limit);
    if (q.priority) query = query.eq('priority', q.priority);
    if (q.status) query = query.eq('status', q.status);
    if (q.since) query = query.gte('created_at', q.since.toISOString());
    const rows = check(await query, 'listar alertas');
    return (rows ?? []).map(toAlert);
  }

  async findLatest(
    deviceId: string,
    category: SoundCategory,
  ): Promise<Alert | null> {
    const row = check(
      await this.db
        .from('alerts')
        .select()
        .eq('device_id', deviceId)
        .eq('category', category)
        .order('created_at', { ascending: false })
        .limit(1)
        .maybeSingle(),
      'última alerta',
    );
    return row ? toAlert(row) : null;
  }

  async acknowledge(id: string, at: Date): Promise<Alert | null> {
    check(
      await this.db
        .from('alerts')
        .update({
          status: AlertStatus.ACKNOWLEDGED,
          acknowledged_at: at.toISOString(),
        })
        .eq('id', id)
        .eq('status', AlertStatus.ACTIVE),
      'confirmar alerta',
    );
    return this.findById(id);
  }
}
