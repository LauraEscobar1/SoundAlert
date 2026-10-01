import { ApiProperty, ApiPropertyOptional, PartialType } from '@nestjs/swagger';
import {
  IsBoolean,
  IsEnum,
  IsNotEmpty,
  IsNumber,
  IsOptional,
  IsString,
  IsUUID,
  Max,
  MaxLength,
  Min,
  ValidateIf,
} from 'class-validator';
import { UserContext } from '../../../domain/index.js';
import { DevicePlatform } from '../../../database/entities.js';

export class RegisterDeviceDto {
  @ApiProperty({ example: 'Reloj de Laura', maxLength: 80 })
  @IsString()
  @IsNotEmpty()
  @MaxLength(80)
  name: string;

  @ApiPropertyOptional({
    format: 'uuid',
    nullable: true,
    description:
      'Usuario propietario (POST /users). Opcional mientras no haya autenticación',
  })
  @IsOptional()
  @ValidateIf((_, v) => v !== null)
  @IsUUID()
  ownerId?: string | null;

  @ApiPropertyOptional({
    enum: DevicePlatform,
    default: DevicePlatform.WEAR_OS,
  })
  @IsOptional()
  @IsEnum(DevicePlatform)
  platform?: DevicePlatform;

  @ApiPropertyOptional({ enum: UserContext, default: UserContext.HOME })
  @IsOptional()
  @IsEnum(UserContext)
  currentContext?: UserContext;

  @ApiPropertyOptional({
    example: 0.7,
    minimum: 0,
    maximum: 1,
    nullable: true,
    description:
      'Confianza mínima para alertar. null = usar la global del servidor',
  })
  @IsOptional()
  @ValidateIf((_, v) => v !== null)
  @IsNumber()
  @Min(0)
  @Max(1)
  minConfidence?: number | null;

  @ApiPropertyOptional({
    default: true,
    description: 'Interruptor general de alertas',
  })
  @IsOptional()
  @IsBoolean()
  alertsEnabled?: boolean;
}

export class UpdateDeviceDto extends PartialType(RegisterDeviceDto) {}

export class ChangeContextDto {
  @ApiProperty({ enum: UserContext, example: UserContext.STREET })
  @IsEnum(UserContext)
  currentContext: UserContext;
}

export class DeviceContextDto {
  @ApiProperty({ format: 'uuid' })
  deviceId: string;

  @ApiProperty({ enum: UserContext, example: UserContext.STREET })
  context: UserContext;

  @ApiProperty({ example: 'Calle' })
  name: string;

  @ApiProperty({ example: 'street' })
  icon: string;
}

export class DeviceDto {
  @ApiProperty({ format: 'uuid' })
  id: string;

  @ApiProperty({ type: String, format: 'uuid', nullable: true })
  ownerId: string | null;

  @ApiProperty({ example: 'Reloj de Laura' })
  name: string;

  @ApiProperty({ enum: DevicePlatform })
  platform: DevicePlatform;

  @ApiProperty({ enum: UserContext })
  currentContext: UserContext;

  @ApiProperty({ type: Number, nullable: true })
  minConfidence: number | null;

  @ApiProperty({ description: 'Confianza mínima que realmente se aplica' })
  effectiveMinConfidence: number;

  @ApiProperty()
  alertsEnabled: boolean;

  @ApiProperty()
  createdAt: Date;

  @ApiProperty()
  updatedAt: Date;
}
