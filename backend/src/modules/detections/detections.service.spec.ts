import { ConfigService } from '@nestjs/config';
import {
  AlertStatus,
  DetectionOutcome,
  PriorityLevel,
  UserContext,
} from '../../domain/index.js';
import {
  ClassificationSource,
  DevicePlatform,
} from '../../database/entities.js';
import {
  MemoryAlertsRepository,
  MemoryDetectionsRepository,
  MemoryDevicesRepository,
  MemoryRuleOverridesRepository,
  MemoryUsersRepository,
} from '../../database/memory/memory.repositories.js';
import { MockSoundClassifier } from '../classification/mock-sound-classifier.js';
import {
  AudioFormat,
  SoundClassifier,
} from '../classification/sound-classifier.interface.js';
import { DevicesService } from '../devices/devices.service.js';
import { RulesService } from '../rules/rules.service.js';
import { DetectionsService } from './detections.service.js';

describe('DetectionsService (flujo completo con clasificador mock)', () => {
  let service: DetectionsService;
  let devices: DevicesService;
  let detectionsRepo: MemoryDetectionsRepository;
  let alertsRepo: MemoryAlertsRepository;

  const build = (classifier: SoundClassifier = new MockSoundClassifier()) => {
    const config = new ConfigService({
      alerts: { minConfidence: 0.6, cooldownSeconds: 10 },
      audio: { maxBytes: 1024 },
    });
    detectionsRepo = new MemoryDetectionsRepository();
    alertsRepo = new MemoryAlertsRepository();
    devices = new DevicesService(
      new MemoryDevicesRepository(),
      new MemoryUsersRepository(),
      config,
    );
    const rules = new RulesService(
      new MemoryRuleOverridesRepository(),
      devices,
    );
    service = new DetectionsService(
      classifier,
      devices,
      rules,
      detectionsRepo,
      alertsRepo,
      config,
    );
  };

  const newDevice = async (context: UserContext) =>
    (
      await devices.register({
        name: 'Reloj',
        currentContext: context,
        platform: DevicePlatform.WEAR_OS,
      })
    ).id;

  const simulate = (deviceId: string, simulatedLabel?: string, extra = {}) =>
    service.fromAudio(deviceId, {
      audioBase64: 'AAAA',
      format: AudioFormat.PCM_16LE,
      sampleRate: 16000,
      channels: 1,
      simulatedLabel,
      ...extra,
    });

  beforeEach(() => build());

  it('Caso 1: CALLE + Siren → DANGER, 3 vibraciones, alerta ACTIVE, guardada', async () => {
    const deviceId = await newDevice(UserContext.STREET);
    const res = await simulate(deviceId, 'Siren');

    expect(res).toMatchObject({
      alerted: true,
      outcome: DetectionOutcome.ALERTED,
      context: UserContext.STREET,
      source: ClassificationSource.SERVER,
      classifier: { name: 'mock', version: '0.0.0' },
      classification: { label: 'Siren', category: 'SIREN', confidence: 0.9 },
      alert: {
        status: AlertStatus.ACTIVE,
        icon: 'siren',
        message: 'Sirena cerca',
        priority: { level: PriorityLevel.DANGER },
        vibration: { count: 3, pattern: [0, 400, 250, 400, 250, 400] },
      },
    });

    // Persistencia: detección y alerta guardadas y enlazadas.
    const saved = await detectionsRepo.findById(res.detectionId);
    expect(saved).toMatchObject({
      deviceId,
      topLabel: 'Siren',
      outcome: 'ALERTED',
      alerted: true,
    });
    const alert = await alertsRepo.findByDetection(res.detectionId);
    expect(alert).toMatchObject({
      id: res.alert!.id,
      vibrationCount: 3,
      priority: 'DANGER',
    });
  });

  it('Caso 2: CALLE + Vehicle horn → ATTENTION, 2 vibraciones', async () => {
    const res = await simulate(
      await newDevice(UserContext.STREET),
      'Vehicle horn',
    );
    expect(res.classification).toMatchObject({
      label: 'Vehicle horn',
      category: 'CAR_HORN',
    });
    expect(res.alert).toMatchObject({
      priority: { level: PriorityLevel.ATTENTION },
      vibration: { count: 2 },
    });
  });

  it('Caso 3: CASA + Vehicle horn → no relevante, sin alerta (pero se guarda la detección)', async () => {
    const res = await simulate(
      await newDevice(UserContext.HOME),
      'Vehicle horn',
    );
    expect(res).toMatchObject({
      alerted: false,
      outcome: DetectionOutcome.DISABLED_IN_CONTEXT,
      alert: null,
      classification: { category: 'CAR_HORN' },
    });
    expect(await detectionsRepo.findById(res.detectionId)).toMatchObject({
      alerted: false,
    });
    expect(await alertsRepo.findByDetection(res.detectionId)).toBeNull();
  });

  it('Caso 4: CASA + Doorbell → INFORMATION, 1 vibración', async () => {
    const res = await simulate(await newDevice(UserContext.HOME), 'Doorbell');
    expect(res.alert).toMatchObject({
      category: 'DOORBELL',
      icon: 'doorbell',
      priority: { level: PriorityLevel.INFORMATION },
      vibration: { count: 1 },
    });
  });

  it('el contexto del body tiene prioridad sobre el del dispositivo', async () => {
    const res = await simulate(
      await newDevice(UserContext.HOME),
      'Vehicle horn',
      { context: UserContext.STREET },
    );
    expect(res).toMatchObject({ alerted: true, context: UserContext.STREET });
  });

  it('confianza por debajo del umbral → LOW_CONFIDENCE', async () => {
    const res = await simulate(await newDevice(UserContext.STREET), 'Siren', {
      simulatedConfidence: 0.3,
    });
    expect(res).toMatchObject({
      alerted: false,
      outcome: DetectionOutcome.LOW_CONFIDENCE,
    });
  });

  it('cooldown: no repite la misma alerta seguida', async () => {
    const deviceId = await newDevice(UserContext.STREET);
    await simulate(deviceId, 'Siren');
    expect((await simulate(deviceId, 'Siren')).outcome).toBe(
      DetectionOutcome.COOLDOWN,
    );
  });

  it('alertas desactivadas en la configuración → ALERTS_DISABLED', async () => {
    const deviceId = await newDevice(UserContext.STREET);
    await devices.update(deviceId, { alertsEnabled: false });
    expect((await simulate(deviceId, 'Siren')).outcome).toBe(
      DetectionOutcome.ALERTS_DISABLED,
    );
  });

  it('depende solo del contrato SoundClassifier (cualquier implementación sirve)', async () => {
    const fake: SoundClassifier = {
      info: { name: 'fake-ai', version: '9.9' },
      isReady: () => true,
      classify: async () => [
        {
          category: 'FIRE_ALARM' as never,
          confidence: 0.99,
          rawLabel: 'Fire alarm',
        },
      ],
    };
    build(fake);
    const res = await simulate(await newDevice(UserContext.WORK));
    expect(res).toMatchObject({
      classifier: { name: 'fake-ai', version: '9.9' },
      alert: { category: 'FIRE_ALARM', priority: { level: 'DANGER' } },
    });
  });
});
