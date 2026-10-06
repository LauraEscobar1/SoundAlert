import { BadRequestException, Injectable } from '@nestjs/common';
import {
  ContextRule,
  getDefaultRules,
  getSoundDefinition,
  PriorityLevel,
  RECORD_ONLY_CATEGORIES,
  SoundCategory,
  UserContext,
} from '../../domain/index.js';
import { RuleOverridesRepository } from '../../database/repositories.js';
import { toPriorityDto } from '../catalog/catalog.mapper.js';
import { DevicesService } from '../devices/devices.service.js';
import { ContextRulesDto, RuleUpdateItemDto } from './dto/rule.dto.js';

export interface EffectiveRule extends ContextRule {
  customized: boolean;
  locked: boolean;
}

/** Un sonido de peligro por defecto nunca se puede silenciar ni bajar de prioridad. */
function isLocked(category: SoundCategory): boolean {
  return getSoundDefinition(category).defaultPriority === PriorityLevel.DANGER;
}

@Injectable()
export class RulesService {
  constructor(
    private readonly overrides: RuleOverridesRepository,
    private readonly devices: DevicesService,
  ) {}

  /** Reglas por defecto del contexto + personalizaciones del dispositivo. */
  async getEffectiveRules(
    deviceId: string,
    context: UserContext,
  ): Promise<EffectiveRule[]> {
    const custom = new Map(
      (await this.overrides.findByDeviceAndContext(deviceId, context)).map(
        (o) => [o.category, o],
      ),
    );
    return getDefaultRules(context).map((rule) => {
      const locked = isLocked(rule.category);
      const o = locked ? undefined : custom.get(rule.category);
      return o
        ? {
            category: rule.category,
            enabled: o.enabled,
            priority: o.priority,
            customized: true,
            locked,
          }
        : { ...rule, customized: false, locked };
    });
  }

  async getRules(
    deviceId: string,
    context: UserContext,
  ): Promise<ContextRulesDto> {
    await this.devices.getOrThrow(deviceId);
    return this.toDto(
      deviceId,
      context,
      await this.getEffectiveRules(deviceId, context),
    );
  }

  async updateRules(
    deviceId: string,
    context: UserContext,
    items: RuleUpdateItemDto[],
  ): Promise<ContextRulesDto> {
    await this.devices.getOrThrow(deviceId);
    const invalid = items.filter(
      (i) =>
        i.category === SoundCategory.UNKNOWN ||
        isLocked(i.category) ||
        RECORD_ONLY_CATEGORIES.includes(i.category),
    );
    if (invalid.length) {
      throw new BadRequestException(
        `No se pueden modificar: ${invalid.map((i) => i.category).join(', ')} (sonidos de peligro o desconocidos)`,
      );
    }
    await this.overrides.upsertMany(
      items.map((i) => ({ deviceId, context, ...i })),
    );
    return this.getRules(deviceId, context);
  }

  async resetRules(
    deviceId: string,
    context: UserContext,
  ): Promise<ContextRulesDto> {
    await this.devices.getOrThrow(deviceId);
    await this.overrides.deleteByDeviceAndContext(deviceId, context);
    return this.getRules(deviceId, context);
  }

  private toDto(
    deviceId: string,
    context: UserContext,
    rules: EffectiveRule[],
  ): ContextRulesDto {
    return {
      deviceId,
      context,
      rules: rules.map((r) => {
        const def = getSoundDefinition(r.category);
        return {
          category: r.category,
          soundName: def.name,
          icon: def.icon,
          enabled: r.enabled,
          priority: toPriorityDto(r.priority),
          customized: r.customized,
          locked: r.locked,
        };
      }),
    };
  }
}
