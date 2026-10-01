import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import { IsDate, IsEnum, IsInt, IsOptional, Max, Min } from 'class-validator';
import {
  AlertStatus,
  PriorityLevel,
  SoundCategory,
  UserContext,
} from '../../../domain/index.js';
import { PriorityDto, VibrationDto } from '../../catalog/dto/catalog.dto.js';

/** Todo lo que el reloj necesita para mostrar y hacer vibrar una alerta. */
export class AlertDto {
  @ApiProperty({ format: 'uuid' })
  id: string;

  @ApiProperty({ format: 'uuid' })
  deviceId: string;

  @ApiProperty({ format: 'uuid' })
  detectionId: string;

  @ApiProperty({ enum: AlertStatus, example: AlertStatus.ACTIVE })
  status: AlertStatus;

  @ApiProperty({ enum: SoundCategory })
  category: SoundCategory;

  @ApiProperty({ example: 'Sirena' })
  soundName: string;

  @ApiProperty({ example: 'siren' })
  icon: string;

  @ApiProperty({ example: 'Sirena cerca' })
  message: string;

  @ApiProperty({
    type: PriorityDto,
    description: 'Nivel, color y patrón de vibración',
  })
  priority: PriorityDto;

  @ApiProperty({
    type: VibrationDto,
    description: 'Vibración que debe ejecutar el reloj (3/2/1 pulsos)',
  })
  vibration: VibrationDto;

  @ApiProperty({ enum: UserContext })
  context: UserContext;

  @ApiProperty({ example: 0.92 })
  confidence: number;

  @ApiProperty()
  createdAt: Date;

  @ApiProperty({ type: Date, nullable: true })
  acknowledgedAt: Date | null;
}

export class ListAlertsQueryDto {
  @ApiPropertyOptional({ default: 50, minimum: 1, maximum: 200 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(200)
  limit?: number;

  @ApiPropertyOptional({ enum: PriorityLevel })
  @IsOptional()
  @IsEnum(PriorityLevel)
  priority?: PriorityLevel;

  @ApiPropertyOptional({ type: String, format: 'date-time' })
  @IsOptional()
  @Type(() => Date)
  @IsDate()
  since?: Date;

  @ApiPropertyOptional({
    enum: AlertStatus,
    description: 'ACTIVE = aún no vistas',
  })
  @IsOptional()
  @IsEnum(AlertStatus)
  status?: AlertStatus;
}
