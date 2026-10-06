import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { createClient, SupabaseClient } from '@supabase/supabase-js';
import request from 'supertest';
import { App } from 'supertest/types.js';
import { AppModule } from '../src/app.module.js';
import {
  repositoryContract,
  RepositorySet,
} from '../src/database/repositories.contract.js';
import {
  SupabaseAlertsRepository,
  SupabaseDatabaseHealth,
  SupabaseDetectionsRepository,
  SupabaseDevicesRepository,
  SupabaseRuleOverridesRepository,
  SupabaseUsersRepository,
} from '../src/database/supabase/supabase.repositories.js';
import { UserContext } from '../src/domain/index.js';
import { setupApp } from '../src/setup-app.js';

/**
 * Integración contra Supabase REAL (npm run test:supabase).
 * Crea datos de prueba y los borra al final; con KEEP_TEST_DATA=1 se conservan
 * para verlos en el Table Editor.
 */

const url = process.env.SUPABASE_URL;
const key = process.env.SUPABASE_SERVICE_ROLE_KEY;
if (!url || !key) {
  throw new Error(
    'Define SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY en backend/.env para ejecutar estos tests',
  );
}
const keep = process.env.KEEP_TEST_DATA === '1';

/** Columnas que el backend lee/escribe en cada tabla (debe coincidir con la migración). */
const EXPECTED_SCHEMA: Record<string, string[]> = {
  users: ['id', 'name', 'email', 'created_at', 'updated_at'],
  contexts: ['code', 'name', 'icon', 'sort_order'],
  devices: [
    'id',
    'owner_id',
    'name',
    'platform',
    'current_context',
    'created_at',
    'updated_at',
  ],
  device_settings: [
    'device_id',
    'min_confidence',
    'alerts_enabled',
    'updated_at',
  ],
  sound_rules: [
    'device_id',
    'context',
    'category',
    'enabled',
    'priority',
    'created_at',
    'updated_at',
  ],
  detections: [
    'id',
    'device_id',
    'context',
    'source',
    'classifier_name',
    'classifier_version',
    'predictions',
    'top_label',
    'top_category',
    'top_confidence',
    'outcome',
    'alerted',
    'created_at',
  ],
  alerts: [
    'id',
    'device_id',
    'detection_id',
    'category',
    'priority',
    'context',
    'confidence',
    'icon',
    'message',
    'vibration_count',
    'status',
    'created_at',
    'acknowledged_at',
  ],
};

const db: SupabaseClient = createClient(url, key, {
  auth: { persistSession: false, autoRefreshToken: false },
});
const created = { user: new Set<string>(), device: new Set<string>() };
const track = (kind: 'user' | 'device', id: string) => created[kind].add(id);

afterAll(async () => {
  if (keep) return;
  if (created.device.size)
    await db
      .from('devices')
      .delete()
      .in('id', [...created.device]);
  if (created.user.size)
    await db
      .from('users')
      .delete()
      .in('id', [...created.user]);
});

describe('Supabase: esquema', () => {
  it.each(Object.entries(EXPECTED_SCHEMA))(
    'la tabla %s tiene las columnas que espera NestJS',
    async (table, cols) => {
      const { error } = await db.from(table).select(cols.join(',')).limit(1);
      expect(error, error ? `${table}: ${error.message}` : '').toBeNull();
    },
  );

  it('contexts contiene exactamente los contextos del backend', async () => {
    const { data, error } = await db
      .from('contexts')
      .select('code')
      .order('sort_order');
    expect(error).toBeNull();
    expect(data!.map((r) => r.code)).toEqual(Object.values(UserContext));
  });

  it('RLS: la clave anon no puede leer datos', async () => {
    const anonKey = process.env.SUPABASE_ANON_KEY;
    if (!anonKey) return; // opcional
    const anon = createClient(url, anonKey, {
      auth: { persistSession: false },
    });
    const { data } = await anon.from('devices').select('id').limit(1);
    expect(data ?? []).toEqual([]);
  });
});

describe('Supabase: repositorios (INSERT / SELECT / UPDATE / DELETE)', () => {
  const repos: RepositorySet = {
    health: new SupabaseDatabaseHealth(db),
    users: new SupabaseUsersRepository(db),
    devices: new SupabaseDevicesRepository(db),
    rules: new SupabaseRuleOverridesRepository(db),
    detections: new SupabaseDetectionsRepository(db),
    alerts: new SupabaseAlertsRepository(db),
  };
  repositoryContract(() => repos, track);

  it('crear un dispositivo crea su device_settings (trigger)', async () => {
    const device = await repos.devices.create({
      ownerId: null,
      name: 'Trigger',
      platform: 'WEAR_OS' as never,
      currentContext: UserContext.HOME,
      minConfidence: null,
      alertsEnabled: true,
    });
    track('device', device.id);
    const { data } = await db
      .from('device_settings')
      .select()
      .eq('device_id', device.id)
      .single();
    expect(data).toMatchObject({
      device_id: device.id,
      alerts_enabled: true,
      min_confidence: null,
    });
  });
});

describe('Supabase: flujo completo por HTTP', () => {
  let app: INestApplication<App>;
  let userId: string;
  let deviceId: string;

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();
    app = moduleRef.createNestApplication({ bodyParser: false });
    setupApp(app);
    await app.init();
  });
  afterAll(() => app?.close());

  const api = () => request(app.getHttpServer());
  const detect = (simulatedLabel: string, context: string) =>
    api()
      .post(`/api/v1/devices/${deviceId}/detections/audio`)
      .send({
        audioBase64: 'AAAA',
        format: 'PCM_16LE',
        sampleRate: 16000,
        channels: 1,
        simulatedLabel,
        context,
      })
      .expect(200);

  it('health: conectado a Supabase', async () => {
    const res = await api().get('/health').expect(200);
    expect(res.body).toMatchObject({
      status: 'ok',
      database: { provider: 'supabase', ok: true },
    });
  });

  it('registra usuario y dispositivo', async () => {
    userId = (
      await api()
        .post('/api/v1/users')
        .send({ name: 'Test integración' })
        .expect(201)
    ).body.id;
    track('user', userId);
    const res = await api()
      .post('/api/v1/devices')
      .send({
        name: 'Reloj integración',
        ownerId: userId,
        currentContext: 'STREET',
      })
      .expect(201);
    deviceId = res.body.id;
    track('device', deviceId);
    const { data } = await db
      .from('devices')
      .select('owner_id, current_context')
      .eq('id', deviceId)
      .single();
    expect(data).toEqual({ owner_id: userId, current_context: 'STREET' });
  });

  it('Caso 1: CALLE + Siren → DANGER, 3 vibraciones, guardado en Supabase', async () => {
    const { body } = await detect('Siren', 'STREET');
    expect(body).toMatchObject({
      alerted: true,
      classification: { label: 'Siren', category: 'SIREN' },
      alert: {
        status: 'ACTIVE',
        priority: { level: 'DANGER' },
        vibration: { count: 3 },
      },
    });

    const det = await db
      .from('detections')
      .select()
      .eq('id', body.detectionId)
      .single();
    expect(det.data).toMatchObject({
      device_id: deviceId,
      context: 'STREET',
      top_label: 'Siren',
      top_category: 'SIREN',
      outcome: 'ALERTED',
      alerted: true,
      classifier_name: 'mock',
    });
    const alert = await db
      .from('alerts')
      .select()
      .eq('detection_id', body.detectionId)
      .single();
    expect(alert.data).toMatchObject({
      id: body.alert.id,
      priority: 'DANGER',
      vibration_count: 3,
      status: 'ACTIVE',
      icon: 'siren',
    });
  });

  it('Caso 2: CALLE + Vehicle horn → ATTENTION, 2 vibraciones', async () => {
    const { body } = await detect('Vehicle horn', 'STREET');
    expect(body.alert).toMatchObject({
      category: 'CAR_HORN',
      priority: { level: 'ATTENTION' },
      vibration: { count: 2 },
    });
    const { data } = await db
      .from('alerts')
      .select('vibration_count')
      .eq('id', body.alert.id)
      .single();
    expect(data).toEqual({ vibration_count: 2 });
  });

  it('Caso 3: CASA + Vehicle horn → sin alerta; detección guardada sin alerta', async () => {
    const { body } = await detect('Vehicle horn', 'HOME');
    expect(body).toMatchObject({
      alerted: false,
      outcome: 'DISABLED_IN_CONTEXT',
      alert: null,
    });
    const det = await db
      .from('detections')
      .select('outcome, alerted')
      .eq('id', body.detectionId)
      .single();
    expect(det.data).toEqual({
      outcome: 'DISABLED_IN_CONTEXT',
      alerted: false,
    });
    const { count } = await db
      .from('alerts')
      .select('id', { count: 'exact', head: true })
      .eq('detection_id', body.detectionId);
    expect(count).toBe(0);
  });

  it('Caso 4: CASA + Doorbell → INFORMATION, 1 vibración', async () => {
    const { body } = await detect('Doorbell', 'HOME');
    expect(body.alert).toMatchObject({
      category: 'DOORBELL',
      priority: { level: 'INFORMATION' },
      vibration: { count: 1 },
    });
  });

  it('consulta historial, confirma una alerta y cambia contexto/reglas en Supabase', async () => {
    const dets = await api()
      .get(`/api/v1/devices/${deviceId}/detections`)
      .expect(200);
    expect(dets.body).toHaveLength(4);
    const alerts = await api()
      .get(`/api/v1/devices/${deviceId}/alerts`)
      .expect(200);
    expect(alerts.body.map((a: any) => a.priority.level)).toEqual([
      'INFORMATION',
      'ATTENTION',
      'DANGER',
    ]);

    await api()
      .post(`/api/v1/devices/${deviceId}/alerts/${alerts.body[2].id}/ack`)
      .expect(200);
    const { data } = await db
      .from('alerts')
      .select('status, acknowledged_at')
      .eq('id', alerts.body[2].id)
      .single();
    expect(data!.status).toBe('ACKNOWLEDGED');
    expect(data!.acknowledged_at).not.toBeNull();

    await api()
      .put(`/api/v1/devices/${deviceId}/context`)
      .send({ currentContext: 'HOME' })
      .expect(200);
    await api()
      .put(`/api/v1/devices/${deviceId}/contexts/HOME/rules`)
      .send({
        rules: [{ category: 'CAR_HORN', enabled: true, priority: 'ATTENTION' }],
      })
      .expect(200);
    const rules = await db
      .from('sound_rules')
      .select('category, enabled, priority')
      .eq('device_id', deviceId);
    expect(rules.data).toEqual([
      { category: 'CAR_HORN', enabled: true, priority: 'ATTENTION' },
    ]);

    // Con la regla personalizada, la bocina en casa ya sí alerta.
    const { body } = await detect('Vehicle horn', 'HOME');
    expect(body.outcome).toBe('COOLDOWN'); // misma categoría hace < 10 s
  });

  it('borrar el dispositivo elimina en cascada detecciones y alertas', async () => {
    if (keep) return;
    await api().delete(`/api/v1/devices/${deviceId}`).expect(204);
    const d = await db
      .from('detections')
      .select('id', { count: 'exact', head: true })
      .eq('device_id', deviceId);
    const a = await db
      .from('alerts')
      .select('id', { count: 'exact', head: true })
      .eq('device_id', deviceId);
    const s = await db
      .from('device_settings')
      .select('device_id', { count: 'exact', head: true })
      .eq('device_id', deviceId);
    expect([d.count, a.count, s.count]).toEqual([0, 0, 0]);
  });
});

describe('Supabase: contexto activo CASA/CALLE/OTRO (HOME/STREET/OTHER) persistido', () => {
  let app: INestApplication<App>;

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();
    app = moduleRef.createNestApplication({ bodyParser: false });
    setupApp(app);
    await app.init();
  });
  afterAll(() => app?.close());

  const api = () => request(app.getHttpServer());
  const newDevice = async () => {
    const res = await api()
      .post('/api/v1/devices')
      .send({ name: 'Reloj contexto (test)' })
      .expect(201);
    track('device', res.body.id);
    return res.body as { id: string; currentContext: string };
  };
  const watch = (id: string, context: string, label: string) =>
    api()
      .post(`/api/v1/devices/${id}/detections/classified`)
      .send({
        context,
        predictions: [{ label, confidence: 0.9 }],
        classifierName: 'yamnet-litert',
      })
      .expect(200);

  it('el catálogo de contextos de la base de datos incluye OTHER', async () => {
    const { data } = await db
      .from('contexts')
      .select('code')
      .eq('code', 'OTHER')
      .single();
    expect(data).toEqual({ code: 'OTHER' });
  });

  it('un dispositivo sin contexto explícito queda en OTHER', async () => {
    const device = await newDevice();
    expect(device.currentContext).toBe('OTHER');
    const { data } = await db
      .from('devices')
      .select('current_context')
      .eq('id', device.id)
      .single();
    expect(data).toEqual({ current_context: 'OTHER' });
  });

  const cases: Array<[string, string, string, string | null, number]> = [
    ['Caso 1', 'HOME', 'Siren', 'DANGER', 3],
    ['Caso 2', 'STREET', 'Siren', 'DANGER', 3],
    ['Caso 3', 'OTHER', 'Siren', 'DANGER', 3],
    ['Caso 4', 'STREET', 'Vehicle horn, car horn, honking', 'ATTENTION', 2],
    ['Caso 5', 'HOME', 'Vehicle horn, car horn, honking', null, 0],
    ['Caso 6', 'OTHER', 'Vehicle horn, car horn, honking', 'ATTENTION', 2],
    ['Caso 7', 'HOME', 'Doorbell', 'INFORMATION', 1],
    ['Caso 8', 'STREET', 'Doorbell', null, 0],
    ['Caso 9', 'OTHER', 'Doorbell', 'INFORMATION', 1],
  ];
  it.each(cases)(
    '%s: %s + %s → %s, contexto guardado en Supabase',
    async (_, context, label, priority, vibrations) => {
      const device = await newDevice();
      const { body } = await watch(device.id, context, label);

      const det = await db
        .from('detections')
        .select('context, alerted, outcome')
        .eq('id', body.detectionId)
        .single();
      expect(det.data).toMatchObject({ context, alerted: priority !== null });
      const alert = await db
        .from('alerts')
        .select('context, priority, vibration_count')
        .eq('detection_id', body.detectionId)
        .maybeSingle();
      if (priority) {
        expect(alert.data).toEqual({
          context,
          priority,
          vibration_count: vibrations,
        });
      } else {
        expect(det.data!.outcome).toBe('DISABLED_IN_CONTEXT');
        expect(alert.data).toBeNull();
      }
    },
  );

  it('HOME → STREET → OTHER: cada detección conserva su contexto y el historial filtra por contexto', async () => {
    const device = await newDevice();
    for (const ctx of ['HOME', 'STREET', 'OTHER']) {
      await api()
        .put(`/api/v1/devices/${device.id}/context`)
        .send({ currentContext: ctx })
        .expect(200);
      await api()
        .post(`/api/v1/devices/${device.id}/detections/classified`)
        .send({ predictions: [{ label: 'Bark', confidence: 0.9 }] })
        .expect(200);
    }
    const { data } = await db
      .from('detections')
      .select('context')
      .eq('device_id', device.id)
      .order('created_at');
    expect(data!.map((d) => d.context)).toEqual(['HOME', 'STREET', 'OTHER']);
    for (const ctx of ['HOME', 'STREET', 'OTHER']) {
      const res = await api()
        .get(`/api/v1/devices/${device.id}/detections?context=${ctx}`)
        .expect(200);
      expect(res.body.map((d: any) => d.context)).toEqual([ctx]);
    }
    const alerts = await api()
      .get(`/api/v1/devices/${device.id}/alerts?context=HOME`)
      .expect(200);
    expect(alerts.body.map((a: any) => a.context)).toEqual(['HOME']);
  });
});
