import { ApiProperty } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import {
  ArrayMaxSize,
  ArrayMinSize,
  IsArray,
  IsBoolean,
  IsEnum,
  ValidateNested,
} from 'class-validator';
import {
  PriorityLevel,
  SoundCategory,
  UserContext,
} from '../../../domain/index.js';
import { PriorityDto } from '../../catalog/dto/catalog.dto.js';

export class RuleDto {
  @ApiProperty({ enum: SoundCategory })
  category: SoundCategory;

  @ApiProperty({ example: 'Sirena' })
  soundName: string;

  @ApiProperty({ example: 'siren' })
  icon: string;

  @ApiProperty({ description: 'Si el sonido genera alertas en este contexto' })
  enabled: boolean;

  @ApiProperty({ type: PriorityDto })
  priority: PriorityDto;

  @ApiProperty({ description: 'true si el usuario personalizó esta regla' })
  customized: boolean;

  @ApiProperty({
    description: 'Los sonidos de peligro no se pueden desactivar',
  })
  locked: boolean;
}

export class ContextRulesDto {
  @ApiProperty({ format: 'uuid' })
  deviceId: string;

  @ApiProperty({ enum: UserContext })
  context: UserContext;

  @ApiProperty({ type: [RuleDto] })
  rules: RuleDto[];
}

export class RuleUpdateItemDto {
  @ApiProperty({ enum: SoundCategory })
  @IsEnum(SoundCategory)
  category: SoundCategory;

  @ApiProperty()
  @IsBoolean()
  enabled: boolean;

  @ApiProperty({ enum: PriorityLevel })
  @IsEnum(PriorityLevel)
  priority: PriorityLevel;
}

export class UpdateRulesDto {
  @ApiProperty({ type: [RuleUpdateItemDto] })
  @IsArray()
  @ArrayMinSize(1)
  @ArrayMaxSize(50)
  @ValidateNested({ each: true })
  @Type(() => RuleUpdateItemDto)
  rules: RuleUpdateItemDto[];
}
