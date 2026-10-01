import { ApiProperty } from '@nestjs/swagger';
import {
  PriorityLevel,
  SoundCategory,
  UserContext,
} from '../../../domain/index.js';

export class VibrationDto {
  @ApiProperty({ example: 3, description: 'Número de pulsos' })
  count: number;

  @ApiProperty({
    example: [0, 400, 250, 400, 250, 400],
    description:
      'Patrón en ms [espera, vibra, pausa, vibra...] (formato Android/Wear OS)',
    type: [Number],
  })
  pattern: number[];
}

export class PriorityDto {
  @ApiProperty({ enum: PriorityLevel })
  level: PriorityLevel;

  @ApiProperty({ example: 'Peligro' })
  label: string;

  @ApiProperty({ example: 3, description: 'Mayor = más importante' })
  rank: number;

  @ApiProperty({ example: '#D32F2F' })
  color: string;

  @ApiProperty({ type: VibrationDto })
  vibration: VibrationDto;
}

export class SoundTypeDto {
  @ApiProperty({ enum: SoundCategory })
  category: SoundCategory;

  @ApiProperty({ example: 'Sirena' })
  name: string;

  @ApiProperty({ example: 'siren', description: 'Id de icono para la app' })
  icon: string;

  @ApiProperty({ enum: PriorityLevel })
  defaultPriority: PriorityLevel;

  @ApiProperty({ example: 'Sirena cerca' })
  shortMessage: string;
}

export class ContextDto {
  @ApiProperty({ enum: UserContext })
  context: UserContext;

  @ApiProperty({ example: 'Casa' })
  name: string;

  @ApiProperty({ example: 'home' })
  icon: string;
}
