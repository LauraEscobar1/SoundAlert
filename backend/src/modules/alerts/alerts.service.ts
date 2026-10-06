import { Injectable, NotFoundException } from '@nestjs/common';
import { getSoundDefinition } from '../../domain/index.js';
import { Alert } from '../../database/entities.js';
import { AlertsRepository } from '../../database/repositories.js';
import { toPriorityDto } from '../catalog/catalog.mapper.js';
import { DevicesService } from '../devices/devices.service.js';
import { AlertDto, ListAlertsQueryDto } from './dto/alert.dto.js';

@Injectable()
export class AlertsService {
  constructor(
    private readonly alerts: AlertsRepository,
    private readonly devices: DevicesService,
  ) {}

  async list(deviceId: string, q: ListAlertsQueryDto): Promise<AlertDto[]> {
    await this.devices.getOrThrow(deviceId);
    const alerts = await this.alerts.find({
      deviceId,
      limit: q.limit ?? 50,
      priority: q.priority,
      since: q.since,
      context: q.context,
      status: q.status,
    });
    return alerts.map(toAlertDto);
  }

  async acknowledge(deviceId: string, alertId: string): Promise<AlertDto> {
    await this.devices.getOrThrow(deviceId);
    const alert = await this.alerts.findById(alertId);
    if (!alert || alert.deviceId !== deviceId) {
      throw new NotFoundException(`Alerta ${alertId} no encontrada`);
    }
    return toAlertDto((await this.alerts.acknowledge(alertId, new Date()))!);
  }
}

export function toAlertDto(a: Alert): AlertDto {
  const def = getSoundDefinition(a.category);
  const priority = toPriorityDto(a.priority);
  return {
    id: a.id,
    deviceId: a.deviceId,
    detectionId: a.detectionId,
    status: a.status,
    category: a.category,
    soundName: def.name,
    icon: a.icon,
    message: a.message,
    priority,
    vibration: { count: a.vibrationCount, pattern: priority.vibration.pattern },
    context: a.context,
    confidence: a.confidence,
    createdAt: a.createdAt,
    acknowledgedAt: a.acknowledgedAt,
  };
}
