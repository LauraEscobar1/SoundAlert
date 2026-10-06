import {
  PRIORITY_POLICIES,
  PriorityLevel,
  SoundDefinition,
  UserContext,
} from '../../domain/index.js';
import { ContextDto, PriorityDto, SoundTypeDto } from './dto/catalog.dto.js';

export function toPriorityDto(level: PriorityLevel): PriorityDto {
  const p = PRIORITY_POLICIES[level];
  return {
    level: p.level,
    label: p.label,
    rank: p.rank,
    color: p.color,
    vibration: { count: p.vibrationCount, pattern: [...p.vibrationPattern] },
  };
}

export function toSoundTypeDto(d: SoundDefinition): SoundTypeDto {
  return { ...d };
}

const CONTEXT_INFO: Record<UserContext, Omit<ContextDto, 'context'>> = {
  [UserContext.HOME]: { name: 'Casa', icon: 'home' },
  [UserContext.STREET]: { name: 'Calle', icon: 'street' },
  [UserContext.UNIVERSITY]: { name: 'Universidad', icon: 'school' },
  [UserContext.WORK]: { name: 'Trabajo', icon: 'work' },
  [UserContext.OTHER]: { name: 'Otro', icon: 'other' },
};

export function toContextDto(context: UserContext): ContextDto {
  return { context, ...CONTEXT_INFO[context] };
}
