import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import request from 'supertest';
import { App } from 'supertest/types.js';
import { AppModule } from '../src/app.module.js';
import { setupApp } from '../src/setup-app.js';

/**
 * Flujo HTTP completo con base de datos EN MEMORIA (rápido, sin red).
 * El mismo flujo contra Supabase real está en test/supabase.int-spec.ts.
 */

const AUDIO = Buffer.alloc(3200).toString('base64');
const MISSING = '00000000-0000-4000-8000-000000000000';

describe('SoundAlert API (e2e, memoria)', () => {
  let app: INestApplication<App>;
  let deviceId: string;

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();
    app = moduleRef.createNestApplication({ bodyParser: false });
    setupApp(app);
    await app.init();
  });

  afterAll(() => app.close());

  const api = () => request(app.getHttpServer());
  const detect = (extra: Record<string, unknown> = {}) =>
    api()
      .post(`/api/v1/devices/${deviceId}/detections/audio`)
      .send({
        audioBase64: AUDIO,
        format: 'PCM_16LE',
        sampleRate: 16000,
        channels: 1,
        ...extra,
      });

  it('GET /health', async () => {
    const res = await api().get('/health').expect(200);
    expect(res.body).toMatchObject({
      status: 'ok',
      database: { provider: 'memory', ok: true },
      classifier: { name: 'mock' },
    });
  });

  it('catálogo de prioridades con vibraciones 3/2/1', async () => {
    const res = await api().get('/api/v1/catalog/priorities').expect(200);
    expect(res.body.map((p: any) => [p.level, p.vibration.count])).toEqual([
      ['DANGER', 3],
      ['ATTENTION', 2],
      ['INFORMATION', 1],
    ]);
  });

  it('crea usuario y registra un dispositivo suyo', async () => {
    const user = await api()
      .post('/api/v1/users')
      .send({ name: 'Laura' })
      .expect(201);
    const res = await api()
      .post('/api/v1/devices')
      .send({ name: 'Reloj test', ownerId: user.body.id })
      .expect(201);
    expect(res.body).toMatchObject({
      ownerId: user.body.id,
      platform: 'WEAR_OS',
      currentContext: 'OTHER', // sin contexto explícito → OTHER (fallback seguro)
      alertsEnabled: true,
      effectiveMinConfidence: 0.6,
    });
    deviceId = res.body.id;
    const mine = await api()
      .get(`/api/v1/users/${user.body.id}/devices`)
      .expect(200);
    expect(mine.body.map((d: any) => d.id)).toEqual([deviceId]);
  });

  describe('validación', () => {
    it('dispositivo: nombre vacío, campos extra, contexto inválido, confianza > 1', async () => {
      await api().post('/api/v1/devices').send({ name: '' }).expect(400);
      await api()
        .post('/api/v1/devices')
        .send({ name: 'x', hacker: true })
        .expect(400);
      await api()
        .post('/api/v1/devices')
        .send({ name: 'x', currentContext: 'BEACH' })
        .expect(400);
      await api()
        .post('/api/v1/devices')
        .send({ name: 'x', minConfidence: 1.5 })
        .expect(400);
      await api()
        .post('/api/v1/devices')
        .send({ name: 'x', ownerId: MISSING })
        .expect(404);
    });

    it('deviceId debe ser UUID y existir', async () => {
      await api().get('/api/v1/devices/no-es-uuid').expect(400);
      await api().get(`/api/v1/devices/${MISSING}`).expect(404);
      await api()
        .post(`/api/v1/devices/${MISSING}/detections/audio`)
        .send({
          audioBase64: AUDIO,
          format: 'PCM_16LE',
          sampleRate: 16000,
          channels: 1,
        })
        .expect(404);
    });

    it('cambio de contexto: obligatorio y válido', async () => {
      await api()
        .put(`/api/v1/devices/${deviceId}/context`)
        .send({})
        .expect(400);
      await api()
        .put(`/api/v1/devices/${deviceId}/context`)
        .send({ currentContext: 'CALLE' })
        .expect(400);
    });

    it('detección: confianza entre 0 y 1, contexto y formato válidos', async () => {
      await detect({
        simulatedLabel: 'Siren',
        simulatedConfidence: 1.2,
      }).expect(400);
      await detect({
        simulatedLabel: 'Siren',
        simulatedConfidence: -0.1,
      }).expect(400);
      await detect({ context: 'BEACH' }).expect(400);
      await detect({ format: 'MP3' }).expect(400);
      await detect({ audioBase64: 'no base64!' }).expect(400);
      await api()
        .post(`/api/v1/devices/${deviceId}/detections/classified`)
        .send({ predictions: [{ label: 'Siren', confidence: 2 }] })
        .expect(400);
    });

    it('reglas: prioridad y contexto válidos', async () => {
      const base = `/api/v1/devices/${deviceId}/contexts`;
      await api()
        .put(`${base}/HOME/rules`)
        .send({
          rules: [{ category: 'DOG_BARK', enabled: true, priority: 'URGENT' }],
        })
        .expect(400);
      await api().get(`${base}/BEACH/rules`).expect(400);
    });
  });

  it('audio sin sonido reconocible no alerta', async () => {
    const res = await detect().expect(200);
    expect(res.body).toMatchObject({
      alerted: false,
      outcome: 'NO_PREDICTIONS',
      classification: null,
    });
  });

  describe('casos principales', () => {
    it('Caso 1: CALLE + Siren → DANGER, 3 vibraciones, alerta activa', async () => {
      await api()
        .put(`/api/v1/devices/${deviceId}/context`)
        .send({ currentContext: 'STREET' })
        .expect(200);
      const ctx = await api()
        .get(`/api/v1/devices/${deviceId}/context`)
        .expect(200);
      expect(ctx.body).toMatchObject({ context: 'STREET', name: 'Calle' });

      const res = await detect({ simulatedLabel: 'Siren' }).expect(200);
      expect(res.body).toMatchObject({
        alerted: true,
        outcome: 'ALERTED',
        context: 'STREET',
        classification: { label: 'Siren', category: 'SIREN', confidence: 0.9 },
        alert: {
          status: 'ACTIVE',
          icon: 'siren',
          message: 'Sirena cerca',
          priority: { level: 'DANGER' },
          vibration: { count: 3 },
        },
      });
    });

    it('Caso 2: CALLE + Vehicle horn → ATTENTION, 2 vibraciones', async () => {
      const res = await detect({ simulatedLabel: 'Vehicle horn' }).expect(200);
      expect(res.body).toMatchObject({
        classification: { label: 'Vehicle horn', category: 'CAR_HORN' },
        alert: { priority: { level: 'ATTENTION' }, vibration: { count: 2 } },
      });
    });

    it('Caso 3: CASA + Vehicle horn → no relevante, sin alerta', async () => {
      const res = await detect({
        simulatedLabel: 'Vehicle horn',
        context: 'HOME',
      }).expect(200);
      expect(res.body).toMatchObject({
        alerted: false,
        outcome: 'DISABLED_IN_CONTEXT',
        alert: null,
      });
    });

    it('Caso 4: CASA + Doorbell → INFORMATION, 1 vibración', async () => {
      const res = await detect({
        simulatedLabel: 'Doorbell',
        context: 'HOME',
      }).expect(200);
      expect(res.body.alert).toMatchObject({
        category: 'DOORBELL',
        priority: { level: 'INFORMATION' },
        vibration: { count: 1 },
      });
    });
  });

  it('no repite la misma alerta durante el cooldown', async () => {
    const res = await detect({ simulatedLabel: 'Siren' }).expect(200);
    expect(res.body).toMatchObject({ alerted: false, outcome: 'COOLDOWN' });
  });

  it('acepta predicciones hechas en el dispositivo', async () => {
    const res = await api()
      .post(`/api/v1/devices/${deviceId}/detections/classified`)
      .send({
        context: 'HOME',
        predictions: [{ label: 'Baby cry, infant cry', confidence: 0.88 }],
        classifierName: 'on-watch',
      })
      .expect(200);
    expect(res.body).toMatchObject({
      source: 'DEVICE',
      classifier: { name: 'on-watch' },
      // Matriz activa: BABY_CRYING en HOME (CASA) es INFORMATION.
      alert: { priority: { level: 'INFORMATION' } },
    });
  });

  it('personaliza reglas pero no permite desactivar sonidos de peligro', async () => {
    const base = `/api/v1/devices/${deviceId}/contexts/HOME/rules`;
    await api()
      .put(base)
      .send({
        rules: [{ category: 'SIREN', enabled: false, priority: 'INFORMATION' }],
      })
      .expect(400);

    const res = await api()
      .put(base)
      .send({
        rules: [
          { category: 'DOG_BARK', enabled: false, priority: 'INFORMATION' },
        ],
      })
      .expect(200);
    expect(
      res.body.rules.find((r: any) => r.category === 'DOG_BARK'),
    ).toMatchObject({ enabled: false, customized: true });

    const reset = await api().delete(base).expect(200);
    expect(
      reset.body.rules.find((r: any) => r.category === 'DOG_BARK').customized,
    ).toBe(false);
  });

  it('historial de detecciones y detalle con su alerta', async () => {
    const all = await api()
      .get(`/api/v1/devices/${deviceId}/detections`)
      .expect(200);
    // sin sonido, sirena, bocina, bocina en casa, timbre, cooldown, bebé
    expect(all.body).toHaveLength(7);
    const alerted = await api()
      .get(`/api/v1/devices/${deviceId}/detections?alertedOnly=true`)
      .expect(200);
    expect(alerted.body).toHaveLength(4);

    const siren = alerted.body.find(
      (d: any) => d.classification.category === 'SIREN',
    );
    const detail = await api()
      .get(`/api/v1/devices/${deviceId}/detections/${siren.id}`)
      .expect(200);
    expect(detail.body).toMatchObject({
      outcome: 'ALERTED',
      alert: { priority: { level: 'DANGER' } },
    });
    await api()
      .get(`/api/v1/devices/${deviceId}/detections/${MISSING}`)
      .expect(404);
  });

  it('historial y confirmación de alertas', async () => {
    const list = await api()
      .get(`/api/v1/devices/${deviceId}/alerts?status=ACTIVE`)
      .expect(200);
    expect(list.body).toHaveLength(4);
    const ack = await api()
      .post(`/api/v1/devices/${deviceId}/alerts/${list.body[0].id}/ack`)
      .expect(200);
    expect(ack.body).toMatchObject({ status: 'ACKNOWLEDGED' });
    expect(ack.body.acknowledgedAt).not.toBeNull();
    const active = await api()
      .get(`/api/v1/devices/${deviceId}/alerts?status=ACTIVE`)
      .expect(200);
    expect(active.body).toHaveLength(3);
    const danger = await api()
      .get(`/api/v1/devices/${deviceId}/alerts?priority=DANGER`)
      .expect(200);
    expect(danger.body).toHaveLength(1);
    await api()
      .get(`/api/v1/devices/${deviceId}/alerts?priority=INFO`)
      .expect(400);
  });

  it('eliminar el dispositivo', async () => {
    await api().delete(`/api/v1/devices/${deviceId}`).expect(204);
    await api().get(`/api/v1/devices/${deviceId}`).expect(404);
  });

  describe('contextos activos HOME/STREET/OTHER (CASA/CALLE/OTRO)', () => {
    const watch = (id: string, context: string, label: string) =>
      api()
        .post(`/api/v1/devices/${id}/detections/classified`)
        .send({
          context,
          predictions: [{ label, confidence: 0.9 }],
          classifierName: 'yamnet-litert',
        })
        .expect(200);
    const newDevice = async (currentContext?: string) =>
      (
        await api()
          .post('/api/v1/devices')
          .send({
            name: 'Reloj contexto',
            ...(currentContext ? { currentContext } : {}),
          })
          .expect(201)
      ).body.id as string;

    it('el catálogo solo ofrece los contextos activos', async () => {
      const res = await api().get('/api/v1/catalog/contexts').expect(200);
      expect(res.body).toEqual([
        { context: 'HOME', name: 'Casa', icon: 'home' },
        { context: 'STREET', name: 'Calle', icon: 'street' },
        { context: 'OTHER', name: 'Otro', icon: 'other' },
      ]);
    });

    it('rechaza contextos históricos o desconocidos como contexto activo', async () => {
      const id = await newDevice();
      for (const ctx of ['UNIVERSITY', 'WORK', 'CALLE']) {
        await api()
          .put(`/api/v1/devices/${id}/context`)
          .send({ currentContext: ctx })
          .expect(400);
        await api()
          .post('/api/v1/devices')
          .send({ name: 'x', currentContext: ctx })
          .expect(400);
        await api()
          .get(`/api/v1/devices/${id}/contexts/${ctx}/rules`)
          .expect(400);
        await api()
          .post(`/api/v1/devices/${id}/detections/classified`)
          .send({
            context: ctx,
            predictions: [{ label: 'Siren', confidence: 0.9 }],
          })
          .expect(400);
      }
    });

    it('consulta y cambia el contexto activo HOME → STREET → OTHER', async () => {
      const id = await newDevice('HOME');
      for (const [ctx, name] of [
        ['HOME', 'Casa'],
        ['STREET', 'Calle'],
        ['OTHER', 'Otro'],
      ]) {
        await api()
          .put(`/api/v1/devices/${id}/context`)
          .send({ currentContext: ctx })
          .expect(200);
        const res = await api()
          .get(`/api/v1/devices/${id}/context`)
          .expect(200);
        expect(res.body).toMatchObject({ context: ctx, name });
      }
    });

    // label → (contexto, prioridad esperada o null = sin alerta, vibraciones)
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
      '%s: %s + %s → %s',
      async (_, context, label, priority, vibrations) => {
        const id = await newDevice();
        const res = await watch(id, context, label);
        expect(res.body.context).toBe(context);
        if (priority) {
          expect(res.body).toMatchObject({
            alerted: true,
            alert: {
              context,
              priority: { level: priority },
              vibration: { count: vibrations },
            },
          });
        } else {
          expect(res.body).toMatchObject({
            alerted: false,
            outcome: 'DISABLED_IN_CONTEXT',
            alert: null,
          });
        }
        // Persistido: la detección guarda su contexto (y su alerta, si la hay).
        const detection = await api()
          .get(`/api/v1/devices/${id}/detections/${res.body.detectionId}`)
          .expect(200);
        expect(detection.body).toMatchObject({
          context,
          alerted: priority !== null,
        });
        if (priority)
          expect(detection.body.alert).toMatchObject({
            context,
            priority: { level: priority },
          });
      },
    );

    it('cambiar HOME → STREET → OTHER no mezcla contextos en el historial y permite filtrar', async () => {
      const id = await newDevice();
      for (const ctx of ['HOME', 'STREET', 'OTHER']) {
        await api()
          .put(`/api/v1/devices/${id}/context`)
          .send({ currentContext: ctx })
          .expect(200);
        // Sin "context" en el cuerpo: se usa el contexto activo del dispositivo.
        await api()
          .post(`/api/v1/devices/${id}/detections/classified`)
          .send({
            predictions: [
              { label: 'Smoke detector, smoke alarm', confidence: 0.9 },
            ],
          })
          .expect(200);
        await new Promise((r) => setTimeout(r, 5)); // orden de created_at
      }
      const all = await api()
        .get(`/api/v1/devices/${id}/detections`)
        .expect(200);
      expect(all.body.map((d: any) => d.context)).toEqual([
        'OTHER',
        'STREET',
        'HOME',
      ]);
      for (const ctx of ['HOME', 'STREET', 'OTHER']) {
        const dets = await api()
          .get(`/api/v1/devices/${id}/detections?context=${ctx}`)
          .expect(200);
        expect(dets.body.map((d: any) => d.context)).toEqual([ctx]);
      }
      // Alertas: la primera DANGER creó alerta; las otras dos caen en cooldown (misma categoría, 10 s).
      const alerts = await api()
        .get(`/api/v1/devices/${id}/alerts?context=HOME`)
        .expect(200);
      expect(alerts.body.map((a: any) => a.context)).toEqual(['HOME']);
      expect(
        (
          await api()
            .get(`/api/v1/devices/${id}/alerts?context=STREET`)
            .expect(200)
        ).body,
      ).toEqual([]);
    });

    it('las categorías del reloj existen y las de solo registro no admiten reglas', async () => {
      const id = await newDevice('HOME');
      const glass = await watch(id, 'HOME', 'GLASS_BREAK');
      expect(glass.body.alert).toMatchObject({
        category: 'GLASS_BREAK',
        priority: { level: 'ATTENTION' },
        context: 'HOME',
      });
      const bell = await watch(id, 'HOME', 'Church bell');
      expect(bell.body).toMatchObject({
        alerted: false,
        outcome: 'DISABLED_IN_CONTEXT',
      });
      expect(
        (
          await api()
            .get(`/api/v1/devices/${id}/detections/${bell.body.detectionId}`)
            .expect(200)
        ).body.classification,
      ).toMatchObject({ category: 'BELL' });
      await api()
        .put(`/api/v1/devices/${id}/contexts/HOME/rules`)
        .send({
          rules: [{ category: 'BELL', enabled: true, priority: 'INFORMATION' }],
        })
        .expect(400);
    });

    it('catálogo limpio: Car passing by, Dog y VEHICLE_APPROACHING no alertan en STREET; Bark sí', async () => {
      const id = await newDevice('STREET');
      const passing = await watch(id, 'STREET', 'Car passing by');
      expect(passing.body).toMatchObject({
        alerted: false,
        outcome: 'UNKNOWN_SOUND',
        alert: null,
      });
      const dog = await watch(id, 'STREET', 'Dog');
      expect(dog.body).toMatchObject({
        alerted: false,
        outcome: 'UNKNOWN_SOUND',
        alert: null,
      });
      // Aunque llegue la categoría directamente, no hay regla activa en STREET.
      const approaching = await watch(id, 'STREET', 'VEHICLE_APPROACHING');
      expect(approaching.body).toMatchObject({
        alerted: false,
        outcome: 'DISABLED_IN_CONTEXT',
        alert: null,
      });
      const bark = await watch(id, 'STREET', 'Bark');
      expect(bark.body).toMatchObject({
        alerted: true,
        context: 'STREET',
        alert: {
          category: 'DOG_BARK',
          priority: { level: 'INFORMATION' },
          vibration: { count: 1 },
        },
      });
    });
  });
});
