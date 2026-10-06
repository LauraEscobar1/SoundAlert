import { Injectable, NotFoundException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AppConfig } from '../../config/configuration.js';
import { DEFAULT_CONTEXT, UserContext } from '../../domain/index.js';
import { Device, DevicePlatform } from '../../database/entities.js';
import {
  DevicesRepository,
  UsersRepository,
} from '../../database/repositories.js';
import {
  DeviceDto,
  RegisterDeviceDto,
  UpdateDeviceDto,
} from './dto/device.dto.js';

@Injectable()
export class DevicesService {
  private readonly globalMinConfidence: number;

  constructor(
    private readonly devices: DevicesRepository,
    private readonly users: UsersRepository,
    config: ConfigService,
  ) {
    this.globalMinConfidence =
      config.getOrThrow<AppConfig['alerts']>('alerts').minConfidence;
  }

  async register(dto: RegisterDeviceDto): Promise<Device> {
    if (dto.ownerId) await this.assertOwnerExists(dto.ownerId);
    return this.devices.create({
      ownerId: dto.ownerId ?? null,
      name: dto.name,
      platform: dto.platform ?? DevicePlatform.WEAR_OS,
      currentContext: dto.currentContext ?? DEFAULT_CONTEXT,
      minConfidence: dto.minConfidence ?? null,
      alertsEnabled: dto.alertsEnabled ?? true,
    });
  }

  async getOrThrow(id: string): Promise<Device> {
    const device = await this.devices.findById(id);
    if (!device) throw new NotFoundException(`Dispositivo ${id} no encontrado`);
    return device;
  }

  listByOwner(ownerId: string): Promise<Device[]> {
    return this.devices.findByOwner(ownerId);
  }

  async update(id: string, dto: UpdateDeviceDto): Promise<Device> {
    if (dto.ownerId) await this.assertOwnerExists(dto.ownerId);
    const device = await this.devices.update(id, dto);
    if (!device) throw new NotFoundException(`Dispositivo ${id} no encontrado`);
    return device;
  }

  async changeContext(id: string, context: UserContext): Promise<Device> {
    return this.update(id, { currentContext: context });
  }

  async remove(id: string): Promise<void> {
    if (!(await this.devices.delete(id))) {
      throw new NotFoundException(`Dispositivo ${id} no encontrado`);
    }
  }

  private async assertOwnerExists(ownerId: string): Promise<void> {
    if (!(await this.users.findById(ownerId))) {
      throw new NotFoundException(`Usuario ${ownerId} no encontrado`);
    }
  }

  effectiveMinConfidence(device: Device): number {
    return device.minConfidence ?? this.globalMinConfidence;
  }

  toDto(device: Device): DeviceDto {
    return {
      ...device,
      effectiveMinConfidence: this.effectiveMinConfidence(device),
    };
  }
}
