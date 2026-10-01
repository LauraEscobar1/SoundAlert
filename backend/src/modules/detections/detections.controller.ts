import {
  Body,
  Controller,
  Get,
  HttpCode,
  Param,
  ParseUUIDPipe,
  Post,
  Query,
} from '@nestjs/common';
import {
  ApiBadRequestResponse,
  ApiNotFoundResponse,
  ApiOkResponse,
  ApiOperation,
  ApiPayloadTooLargeResponse,
  ApiServiceUnavailableResponse,
  ApiTags,
} from '@nestjs/swagger';
import { DetectionsService } from './detections.service.js';
import {
  AudioDetectionDto,
  ClassifiedDetectionDto,
  DetectionDetailDto,
  DetectionDto,
  DetectionResultDto,
  ListDetectionsQueryDto,
} from './dto/detection.dto.js';

@ApiTags('detections')
@ApiNotFoundResponse({ description: 'Dispositivo o detección no encontrados' })
@ApiBadRequestResponse({ description: 'Cuerpo, parámetros o UUID inválidos' })
@Controller('devices/:deviceId/detections')
export class DetectionsController {
  constructor(private readonly service: DetectionsService) {}

  @Post('audio')
  @HttpCode(200)
  @ApiOperation({
    summary:
      'Flujo principal: audio → clasificador (mock) → reglas → prioridad → alerta → Supabase',
    description:
      'Con el clasificador mock usa `simulatedLabel` ("Siren", "Vehicle horn", "Doorbell"...) para simular el sonido. ' +
      'Si `alerted` es true, `alert` trae icono, mensaje, prioridad y vibración para el reloj.',
  })
  @ApiOkResponse({ type: DetectionResultDto })
  @ApiPayloadTooLargeResponse()
  @ApiServiceUnavailableResponse({
    description: 'El servicio de IA no responde',
  })
  fromAudio(
    @Param('deviceId', ParseUUIDPipe) deviceId: string,
    @Body() dto: AudioDetectionDto,
  ): Promise<DetectionResultDto> {
    return this.service.fromAudio(deviceId, dto);
  }

  @Post('classified')
  @HttpCode(200)
  @ApiOperation({
    summary:
      'Enviar predicciones ya calculadas en el dispositivo; el servidor prioriza y decide si alertar',
  })
  @ApiOkResponse({ type: DetectionResultDto })
  fromClassification(
    @Param('deviceId', ParseUUIDPipe) deviceId: string,
    @Body() dto: ClassifiedDetectionDto,
  ): Promise<DetectionResultDto> {
    return this.service.fromClassification(deviceId, dto);
  }

  @Get()
  @ApiOperation({ summary: 'Historial de detecciones (más recientes primero)' })
  @ApiOkResponse({ type: [DetectionDto] })
  list(
    @Param('deviceId', ParseUUIDPipe) deviceId: string,
    @Query() query: ListDetectionsQueryDto,
  ): Promise<DetectionDto[]> {
    return this.service.list(deviceId, query);
  }

  @Get(':detectionId')
  @ApiOperation({
    summary: 'Detalle de una detección con su alerta (si la hubo)',
  })
  @ApiOkResponse({ type: DetectionDetailDto })
  get(
    @Param('deviceId', ParseUUIDPipe) deviceId: string,
    @Param('detectionId', ParseUUIDPipe) detectionId: string,
  ): Promise<DetectionDetailDto> {
    return this.service.get(deviceId, detectionId);
  }
}
