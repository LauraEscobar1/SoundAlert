import {
  AlertStatus,
  DetectionOutcome,
  PriorityLevel,
  SoundCategory,
  UserContext,
} from '../domain/index.js';
import { ClassificationSource, DevicePlatform } from './entities.js';
import {
  AlertsRepository,
  DatabaseHealth,
  DetectionsRepository,
  DevicesRepository,
  RuleOverridesRepository,
  UsersRepository,
} from './repositories.js';

export interface RepositorySet {
  health: DatabaseHealth;
  users: UsersRepository;
  devices: DevicesRepository;
  rules: RuleOverridesRepository;
  detections: DetectionsRepository;
  alerts: AlertsRepository;
}

/**
 * Suite de contrato: cualquier implementación de los repositorios
 * (memoria, Supabase...) debe comportarse igual. Se ejecuta en
 * memory.repositories.spec.ts y en test/supabase.int-spec.ts.
 *
 * `track` recibe los ids creados para que el llamador pueda limpiarlos.
 */
export function repositoryContract(
  getRepos: () => RepositorySet,
  track: (kind: 'user' | 'device', id: string) => void = () => {},
): void {
  it('health responde ok', async () => {
    expect(await getRepos().health.check()).toMatchObject({ ok: true });
  });

  it('users: INSERT y SELECT', async () => {
    const { users } = getRepos();
    const user = await users.create({ name: 'Contrato', email: null });
    track('user', user.id);
    expect(user).toMatchObject({ name: 'Contrato', email: null });
    expect(user.createdAt).toBeInstanceOf(Date);
    expect(await users.findById(user.id)).toEqual(user);
  });

  it('devices + configuración: INSERT, SELECT, UPDATE, DELETE', async () => {
    const { users, devices } = getRepos();
    const owner = await users.create({ name: 'Propietario', email: null });
    track('user', owner.id);

    const device = await devices.create({
      ownerId: owner.id,
      name: 'Reloj contrato',
      platform: DevicePlatform.WEAR_OS,
      currentContext: UserContext.HOME,
      minConfidence: 0.75,
      alertsEnabled: true,
    });
    track('device', device.id);
    expect(device).toMatchObject({
      ownerId: owner.id,
      currentContext: UserContext.HOME,
      minConfidence: 0.75,
      alertsEnabled: true,
    });
    expect(await devices.findById(device.id)).toEqual(device);
    expect((await devices.findByOwner(owner.id)).map((d) => d.id)).toEqual([
      device.id,
    ]);

    const updated = await devices.update(device.id, {
      currentContext: UserContext.STREET,
      alertsEnabled: false,
      minConfidence: null,
    });
    expect(updated).toMatchObject({
      currentContext: UserContext.STREET,
      alertsEnabled: false,
      minConfidence: null,
    });

    expect(
      await devices.update('00000000-0000-4000-8000-000000000000', {
        name: 'x',
      }),
    ).toBeNull();
    expect(await devices.delete(device.id)).toBe(true);
    expect(await devices.findById(device.id)).toBeNull();
  });

  it('sound_rules: upsert, lectura por contexto y reset', async () => {
    const { devices, rules } = getRepos();
    const device = await newDevice(devices, track);
    const rule = {
      deviceId: device.id,
      context: UserContext.HOME,
      category: SoundCategory.DOG_BARK,
      enabled: false,
      priority: PriorityLevel.INFORMATION,
    };
    await rules.upsertMany([rule]);
    await rules.upsertMany([
      { ...rule, enabled: true, priority: PriorityLevel.ATTENTION },
    ]);
    expect(
      await rules.findByDeviceAndContext(device.id, UserContext.HOME),
    ).toEqual([{ ...rule, enabled: true, priority: PriorityLevel.ATTENTION }]);
    expect(
      await rules.findByDeviceAndContext(device.id, UserContext.STREET),
    ).toEqual([]);
    await rules.deleteByDeviceAndContext(device.id, UserContext.HOME);
    expect(
      await rules.findByDeviceAndContext(device.id, UserContext.HOME),
    ).toEqual([]);
  });

  it('detections + alerts: INSERT, SELECT, filtros y confirmación', async () => {
    const { devices, detections, alerts } = getRepos();
    const device = await newDevice(devices, track);

    const detection = await detections.create({
      deviceId: device.id,
      context: UserContext.STREET,
      source: ClassificationSource.SERVER,
      classifierName: 'mock',
      classifierVersion: '0.0.0',
      predictions: [
        { category: SoundCategory.SIREN, confidence: 0.9, rawLabel: 'Siren' },
      ],
      topLabel: 'Siren',
      topCategory: SoundCategory.SIREN,
      topConfidence: 0.9,
      outcome: DetectionOutcome.ALERTED,
    });
    expect(detection).toMatchObject({
      alerted: true,
      topConfidence: 0.9,
      topLabel: 'Siren',
    });
    expect(await detections.findById(detection.id)).toEqual(detection);

    const ignored = await detections.create({
      ...detection,
      topCategory: null,
      topConfidence: null,
      topLabel: null,
      predictions: [],
      outcome: DetectionOutcome.NO_PREDICTIONS,
    });
    expect(ignored.alerted).toBe(false);
    expect(
      (await detections.find({ deviceId: device.id, limit: 10 })).map(
        (d) => d.id,
      ),
    ).toEqual([ignored.id, detection.id]);
    expect(
      (
        await detections.find({
          deviceId: device.id,
          limit: 10,
          alertedOnly: true,
        })
      ).map((d) => d.id),
    ).toEqual([detection.id]);

    const alert = await alerts.create({
      deviceId: device.id,
      detectionId: detection.id,
      category: SoundCategory.SIREN,
      priority: PriorityLevel.DANGER,
      context: UserContext.STREET,
      confidence: 0.9,
      icon: 'siren',
      message: 'Sirena cerca',
      vibrationCount: 3,
    });
    expect(alert).toMatchObject({
      status: AlertStatus.ACTIVE,
      acknowledgedAt: null,
      vibrationCount: 3,
    });
    expect(await alerts.findById(alert.id)).toEqual(alert);
    expect(await alerts.findByDetection(detection.id)).toEqual(alert);
    expect(await alerts.findLatest(device.id, SoundCategory.SIREN)).toEqual(
      alert,
    );
    expect(
      await alerts.findLatest(device.id, SoundCategory.DOORBELL),
    ).toBeNull();
    expect(
      await alerts.find({
        deviceId: device.id,
        limit: 10,
        priority: PriorityLevel.ATTENTION,
      }),
    ).toEqual([]);

    const acked = await alerts.acknowledge(alert.id, new Date());
    expect(acked).toMatchObject({ status: AlertStatus.ACKNOWLEDGED });
    expect(acked!.acknowledgedAt).toBeInstanceOf(Date);
    expect(
      await alerts.find({
        deviceId: device.id,
        limit: 10,
        status: AlertStatus.ACTIVE,
      }),
    ).toEqual([]);
  });
}

async function newDevice(
  devices: DevicesRepository,
  track: (kind: 'user' | 'device', id: string) => void,
) {
  const device = await devices.create({
    ownerId: null,
    name: 'Reloj contrato',
    platform: DevicePlatform.WEAR_OS,
    currentContext: UserContext.HOME,
    minConfidence: null,
    alertsEnabled: true,
  });
  track('device', device.id);
  return device;
}
