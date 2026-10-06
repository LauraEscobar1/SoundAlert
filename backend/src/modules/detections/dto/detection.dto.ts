import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { Transform, Type } from 'class-transformer';
import {
  ArrayMaxSize,
  IsArray,
  IsBase64,
  IsBoolean,
  IsDate,
  IsEnum,
  IsIn,
  IsInt,
  IsNotEmpty,
  IsNumber,
  IsOptional,
  IsString,
  Max,
  MaxLength,
  Min,
  ValidateNested,
} from 'class-validator';
import {
  DetectionOutcome,
  SoundCategory,
  UserContext,
  ACTIVE_CONTEXTS,
} from '../../../domain/index.js';
import { ClassificationSource } from '../../../database/entities.js';
import { AlertDto } from '../../alerts/dto/alert.dto.js';
import { AudioFormat } from '../../classification/sound-classifier.interface.js';

/** Opción A: el reloj envía el audio y el backend lo clasifica con la IA. */
export class AudioDetectionDto {
  @ApiProperty({
    description:
      'Fragmento de audio en base64 (recomendado ~1 s). Con el mock basta "AAAA"',
    example: 'AAAA',
  })
  @IsBase64()
  @IsNotEmpty()
  audioBase64: string;

  @ApiProperty({ enum: AudioFormat, example: AudioFormat.PCM_16LE })
  @IsEnum(AudioFormat)
  format: AudioFormat;

  @ApiProperty({ example: 16000 })
  @IsInt()
  @Min(8000)
  @Max(48000)
  sampleRate: number;

  @ApiProperty({ example: 1 })
  @IsInt()
  @Min(1)
  @Max(2)
  channels: number;

  @ApiPropertyOptional({ example: 975 })
  @IsOptional()
  @IsInt()
  @Min(1)
  durationMs?: number;

  @ApiPropertyOptional({
    enum: ACTIVE_CONTEXTS,
    example: UserContext.STREET,
    description:
      'Contexto activo (HOME, STREET, OTHER). Si se omite se usa el contexto actual del dispositivo',
  })
  @IsOptional()
  @IsIn(ACTIVE_CONTEXTS)
  context?: UserContext;

  @ApiPropertyOptional({
    example: 'Siren',
    description:
      'SOLO DESARROLLO: etiqueta que devolverá el clasificador mock (p. ej. "Siren", "Vehicle horn", "Doorbell"). Los clasificadores reales la ignoran',
  })
  @IsOptional()
  @IsString()
  @MaxLength(100)
  simulatedLabel?: string;

  @ApiPropertyOptional({
    example: 0.9,
    minimum: 0,
    maximum: 1,
    description:
      'SOLO DESARROLLO: confianza del clasificador mock (0-1, por defecto 0.9)',
  })
  @IsOptional()
  @IsNumber()
  @Min(0)
  @Max(1)
  simulatedConfidence?: number;
}

export class PredictionInputDto {
  @ApiProperty({
    example: 'Siren',
    description:
      'Etiqueta del modelo (AudioSet) o directamente una SoundCategory',
  })
  @IsString()
  @IsNotEmpty()
  @MaxLength(100)
  label: string;

  @ApiProperty({ example: 0.93, minimum: 0, maximum: 1 })
  @IsNumber()
  @Min(0)
  @Max(1)
  confidence: number;
}

/** Opción B: el reloj ya clasificó localmente (modelo on-device) y envía el resultado. */
export class ClassifiedDetectionDto {
  @ApiProperty({ type: [PredictionInputDto] })
  @IsArray()
  @ArrayMaxSize(20)
  @ValidateNested({ each: true })
  @Type(() => PredictionInputDto)
  predictions: PredictionInputDto[];

  @ApiPropertyOptional({
    enum: ACTIVE_CONTEXTS,
    description:
      'Contexto activo en el reloj cuando se confirmó la detección. Si se omite, el del dispositivo',
  })
  @IsOptional()
  @IsIn(ACTIVE_CONTEXTS)
  context?: UserContext;

  @ApiPropertyOptional({ example: 'yamnet-tflite' })
  @IsOptional()
  @IsString()
  @MaxLength(60)
  classifierName?: string;

  @ApiPropertyOptional({ example: '1.0.0' })
  @IsOptional()
  @IsString()
  @MaxLength(30)
  classifierVersion?: string;
}

export class PredictionDto {
  @ApiProperty({ enum: SoundCategory })
  category: SoundCategory;

  @ApiProperty({ example: 0.9, minimum: 0, maximum: 1 })
  confidence: number;

  @ApiProperty({
    example: 'Siren',
    description: 'Etiqueta original del clasificador',
  })
  rawLabel: string;
}

/** Resultado principal de la clasificación (la predicción con mayor confianza). */
export class ClassificationDto {
  @ApiProperty({
    example: 'Siren',
    description: 'Etiqueta devuelta por el clasificador',
  })
  label: string;

  @ApiProperty({ enum: SoundCategory, example: SoundCategory.SIREN })
  category: SoundCategory;

  @ApiProperty({ example: 'Sirena' })
  soundName: string;

  @ApiProperty({ example: 0.9, minimum: 0, maximum: 1 })
  confidence: number;
}

export class ClassifierInfoDto {
  @ApiProperty({ example: 'mock' })
  name: string;

  @ApiProperty({ example: '0.0.0' })
  version: string;
}

/** Respuesta de POST /detections/*: lo que el reloj necesita para reaccionar. */
export class DetectionResultDto {
  @ApiProperty({ format: 'uuid' })
  detectionId: string;

  @ApiProperty({
    description: 'true si el reloj debe mostrar la alerta y vibrar',
  })
  alerted: boolean;

  @ApiProperty({ enum: DetectionOutcome, description: 'Motivo de la decisión' })
  outcome: DetectionOutcome;

  @ApiProperty({ type: ClassificationDto, nullable: true })
  classification: ClassificationDto | null;

  @ApiProperty({
    type: AlertDto,
    nullable: true,
    description: 'null si no se generó alerta',
  })
  alert: AlertDto | null;

  @ApiProperty({ enum: UserContext })
  context: UserContext;

  @ApiProperty({ enum: ClassificationSource })
  source: ClassificationSource;

  @ApiProperty({ type: ClassifierInfoDto })
  classifier: ClassifierInfoDto;

  @ApiProperty({ type: [PredictionDto] })
  predictions: PredictionDto[];
}

/** Detección guardada (historial). */
export class DetectionDto {
  @ApiProperty({ format: 'uuid' })
  id: string;

  @ApiProperty({ format: 'uuid' })
  deviceId: string;

  @ApiProperty({ enum: UserContext })
  context: UserContext;

  @ApiProperty({ enum: ClassificationSource })
  source: ClassificationSource;

  @ApiProperty({ type: ClassifierInfoDto })
  classifier: ClassifierInfoDto;

  @ApiProperty({ type: ClassificationDto, nullable: true })
  classification: ClassificationDto | null;

  @ApiProperty({ type: [PredictionDto] })
  predictions: PredictionDto[];

  @ApiProperty({ enum: DetectionOutcome })
  outcome: DetectionOutcome;

  @ApiProperty()
  alerted: boolean;

  @ApiProperty()
  createdAt: Date;
}

export class DetectionDetailDto extends DetectionDto {
  @ApiProperty({ type: AlertDto, nullable: true })
  alert: AlertDto | null;
}

export class ListDetectionsQueryDto {
  @ApiPropertyOptional({ default: 50, minimum: 1, maximum: 200 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(200)
  limit?: number;

  @ApiPropertyOptional({
    default: false,
    description: 'Solo las que generaron alerta',
  })
  @IsOptional()
  @Transform(({ value }) =>
    value === 'true' ? true : value === 'false' ? false : value,
  )
  @IsBoolean()
  alertedOnly?: boolean;

  @ApiPropertyOptional({ type: String, format: 'date-time' })
  @IsOptional()
  @Type(() => Date)
  @IsDate()
  since?: Date;

  @ApiPropertyOptional({
    enum: UserContext,
    description:
      'Solo las ocurridas en este contexto (incluye contextos históricos)',
  })
  @IsOptional()
  @IsEnum(UserContext)
  context?: UserContext;
}
