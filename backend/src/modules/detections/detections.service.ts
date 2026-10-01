import {
  Inject,
  Injectable,
  NotFoundException,
  PayloadTooLargeException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AppConfig } from '../../config/configuration.js';
import {
  DetectionOutcome,
  getSoundDefinition,
  PRIORITY_POLICIES,
  UserContext,
} from '../../domain/index.js';
import {
  ClassificationSource,
  Detection,
  Device,
  Prediction,
} from '../../database/entities.js';
import {
  AlertsRepository,
  DetectionsRepository,
} from '../../database/repositories.js';
import { EngineDecision, evaluate } from '../alerts/alert-engine.js';
import { toAlertDto } from '../alerts/alerts.service.js';
import { LabelMapper } from '../classification/label-mapper.js';
import { SOUND_CLASSIFIER } from '../classification/sound-classifier.interface.js';
import type {
  ClassifierInfo,
  SoundClassifier,
} from '../classification/sound-classifier.interface.js';
import { DevicesService } from '../devices/devices.service.js';
import { RulesService } from '../rules/rules.service.js';
import {
  AudioDetectionDto,
  ClassificationDto,
  ClassifiedDetectionDto,
  DetectionDetailDto,
  DetectionDto,
  DetectionResultDto,
  ListDetectionsQueryDto,
} from './dto/detection.dto.js';

/**
 * Orquesta el flujo principal:
 * dispositivo → clasificación (SoundClassifier) → reglas del contexto →
 * prioridad → alerta (icono + mensaje + vibración) → Supabase → respuesta.
 */
@Injectable()
export class DetectionsService {
  private readonly cooldownMs: number;
  private readonly maxAudioBytes: number;
  private readonly labelMapper = new LabelMapper();

  constructor(
    @Inject(SOUND_CLASSIFIER) private readonly classifier: SoundClassifier,
    private readonly devices: DevicesService,
    private readonly rules: RulesService,
    private readonly detections: DetectionsRepository,
    private readonly alerts: AlertsRepository,
    config: ConfigService,
  ) {
    this.cooldownMs =
      config.getOrThrow<AppConfig['alerts']>('alerts').cooldownSeconds * 1000;
    this.maxAudioBytes =
      config.getOrThrow<AppConfig['audio']>('audio').maxBytes;
  }

  /** El backend clasifica el audio con el clasificador configurado. */
  async fromAudio(
    deviceId: string,
    dto: AudioDetectionDto,
  ): Promise<DetectionResultDto> {
    const device = await this.devices.getOrThrow(deviceId);
    const data = Buffer.from(dto.audioBase64, 'base64');
    if (data.length > this.maxAudioBytes) {
      throw new PayloadTooLargeException(
        `El audio supera ${this.maxAudioBytes} bytes`,
      );
    }

    const metadata: Record<string, string> = {};
    if (dto.simulatedLabel) metadata.simulatedLabel = dto.simulatedLabel;
    if (dto.simulatedConfidence !== undefined)
      metadata.simulatedConfidence = String(dto.simulatedConfidence);

    const predictions = await this.classifier.classify({
      data,
      format: dto.format,
      sampleRate: dto.sampleRate,
      channels: dto.channels,
      durationMs: dto.durationMs,
      metadata,
    });

    return this.process(
      device,
      dto.context,
      predictions,
      ClassificationSource.SERVER,
      this.classifier.info,
    );
  }

  /** El dispositivo ya clasificó localmente; aquí solo se prioriza y alerta. */
  async fromClassification(
    deviceId: string,
    dto: ClassifiedDetectionDto,
  ): Promise<DetectionResultDto> {
    const device = await this.devices.getOrThrow(deviceId);
    const predictions = this.labelMapper.map(
      dto.predictions.map((p) => ({ label: p.label, score: p.confidence })),
    );
    return this.process(
      device,
      dto.context,
      predictions,
      ClassificationSource.DEVICE,
      {
        name: dto.classifierName ?? 'device',
        version: dto.classifierVersion ?? 'unknown',
      },
    );
  }

  async list(
    deviceId: string,
    q: ListDetectionsQueryDto,
  ): Promise<DetectionDto[]> {
    await this.devices.getOrThrow(deviceId);
    const items = await this.detections.find({
      deviceId,
      limit: q.limit ?? 50,
      alertedOnly: q.alertedOnly,
      since: q.since,
    });
    return items.map(toDetectionDto);
  }

  async get(
    deviceId: string,
    detectionId: string,
  ): Promise<DetectionDetailDto> {
    await this.devices.getOrThrow(deviceId);
    const detection = await this.detections.findById(detectionId);
    if (!detection || detection.deviceId !== deviceId) {
      throw new NotFoundException(`Detección ${detectionId} no encontrada`);
    }
    const alert = detection.alerted
      ? await this.alerts.findByDetection(detection.id)
      : null;
    return {
      ...toDetectionDto(detection),
      alert: alert ? toAlertDto(alert) : null,
    };
  }

  private async process(
    device: Device,
    requestedContext: UserContext | undefined,
    predictions: Prediction[],
    source: ClassificationSource,
    classifier: ClassifierInfo,
  ): Promise<DetectionResultDto> {
    const context = requestedContext ?? device.currentContext;
    const decision = await this.decide(device, context, predictions);
    const top = predictions[0] ?? null;

    const detection = await this.detections.create({
      deviceId: device.id,
      context,
      source,
      classifierName: classifier.name,
      classifierVersion: classifier.version,
      predictions,
      topLabel: top?.rawLabel ?? null,
      topCategory: top?.category ?? null,
      topConfidence: top?.confidence ?? null,
      outcome: decision.outcome,
    });

    let alert = null;
    if (decision.outcome === DetectionOutcome.ALERTED) {
      const { prediction, rule } = decision as Required<EngineDecision>;
      const sound = getSoundDefinition(prediction.category);
      alert = await this.alerts.create({
        deviceId: device.id,
        detectionId: detection.id,
        category: prediction.category,
        priority: rule.priority,
        context,
        confidence: prediction.confidence,
        icon: sound.icon,
        message: sound.shortMessage,
        vibrationCount: PRIORITY_POLICIES[rule.priority].vibrationCount,
      });
    }

    return {
      detectionId: detection.id,
      alerted: alert !== null,
      outcome: decision.outcome,
      classification: toClassificationDto(top),
      alert: alert ? toAlertDto(alert) : null,
      context,
      source,
      classifier: { name: classifier.name, version: classifier.version },
      predictions,
    };
  }

  private async decide(
    device: Device,
    context: UserContext,
    predictions: Prediction[],
  ): Promise<EngineDecision> {
    if (!device.alertsEnabled)
      return { outcome: DetectionOutcome.ALERTS_DISABLED };

    const rules = await this.rules.getEffectiveRules(device.id, context);
    const decision = evaluate(
      predictions,
      rules,
      this.devices.effectiveMinConfidence(device),
    );
    if (decision.outcome !== DetectionOutcome.ALERTED || this.cooldownMs <= 0)
      return decision;

    // Evita vibrar repetidamente por el mismo sonido continuo (p. ej. una sirena de 30 s).
    const last = await this.alerts.findLatest(
      device.id,
      decision.prediction!.category,
    );
    if (last && Date.now() - last.createdAt.getTime() < this.cooldownMs) {
      return { outcome: DetectionOutcome.COOLDOWN };
    }
    return decision;
  }
}

function toClassificationDto(p: Prediction | null): ClassificationDto | null {
  if (!p) return null;
  return {
    label: p.rawLabel,
    category: p.category,
    soundName: getSoundDefinition(p.category).name,
    confidence: p.confidence,
  };
}

function toDetectionDto(d: Detection): DetectionDto {
  const top = d.topCategory
    ? {
        category: d.topCategory,
        confidence: d.topConfidence ?? 0,
        rawLabel: d.topLabel ?? d.topCategory,
      }
    : null;
  return {
    id: d.id,
    deviceId: d.deviceId,
    context: d.context,
    source: d.source,
    classifier: { name: d.classifierName, version: d.classifierVersion },
    classification: toClassificationDto(top),
    predictions: d.predictions,
    outcome: d.outcome,
    alerted: d.alerted,
    createdAt: d.createdAt,
  };
}
