import {
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
  ApiTags,
} from '@nestjs/swagger';
import { AlertsService } from './alerts.service.js';
import { AlertDto, ListAlertsQueryDto } from './dto/alert.dto.js';

@ApiTags('alerts')
@ApiNotFoundResponse({ description: 'Dispositivo o alerta no encontrados' })
@ApiBadRequestResponse({ description: 'UUID o filtros inválidos' })
@Controller('devices/:deviceId/alerts')
export class AlertsController {
  constructor(private readonly service: AlertsService) {}

  @Get()
  @ApiOperation({ summary: 'Historial de alertas (más recientes primero)' })
  @ApiOkResponse({ type: [AlertDto] })
  list(
    @Param('deviceId', ParseUUIDPipe) deviceId: string,
    @Query() query: ListAlertsQueryDto,
  ): Promise<AlertDto[]> {
    return this.service.list(deviceId, query);
  }

  @Post(':alertId/ack')
  @HttpCode(200)
  @ApiOperation({
    summary: 'Marcar una alerta como vista (status → ACKNOWLEDGED)',
  })
  @ApiOkResponse({ type: AlertDto })
  acknowledge(
    @Param('deviceId', ParseUUIDPipe) deviceId: string,
    @Param('alertId', ParseUUIDPipe) alertId: string,
  ): Promise<AlertDto> {
    return this.service.acknowledge(deviceId, alertId);
  }
}
