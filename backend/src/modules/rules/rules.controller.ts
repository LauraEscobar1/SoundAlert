import {
  Body,
  Controller,
  Delete,
  Get,
  Param,
  ParseUUIDPipe,
  Put,
} from '@nestjs/common';
import {
  ApiBadRequestResponse,
  ApiNotFoundResponse,
  ApiOkResponse,
  ApiOperation,
  ApiParam,
  ApiTags,
} from '@nestjs/swagger';
import { ACTIVE_CONTEXTS, UserContext } from '../../domain/index.js';
import { ParseActiveContextPipe } from '../../common/active-context.pipe.js';
import { ContextRulesDto, UpdateRulesDto } from './dto/rule.dto.js';
import { RulesService } from './rules.service.js';

@ApiTags('rules')
@ApiNotFoundResponse({ description: 'Dispositivo no encontrado' })
@ApiBadRequestResponse({
  description:
    'UUID, contexto o reglas inválidos (o se intentó modificar un sonido de peligro)',
})
@ApiParam({ name: 'context', enum: ACTIVE_CONTEXTS })
@Controller('devices/:deviceId/contexts/:context/rules')
export class RulesController {
  constructor(private readonly service: RulesService) {}

  @Get()
  @ApiOperation({
    summary:
      'Reglas efectivas de un contexto (qué sonidos alertan y con qué prioridad)',
  })
  @ApiOkResponse({ type: ContextRulesDto })
  get(
    @Param('deviceId', ParseUUIDPipe) deviceId: string,
    @Param('context', new ParseActiveContextPipe()) context: UserContext,
  ): Promise<ContextRulesDto> {
    return this.service.getRules(deviceId, context);
  }

  @Put()
  @ApiOperation({ summary: 'Personalizar reglas de un contexto' })
  @ApiOkResponse({ type: ContextRulesDto })
  @ApiBadRequestResponse({
    description: 'Se intentó modificar un sonido de peligro',
  })
  update(
    @Param('deviceId', ParseUUIDPipe) deviceId: string,
    @Param('context', new ParseActiveContextPipe()) context: UserContext,
    @Body() dto: UpdateRulesDto,
  ): Promise<ContextRulesDto> {
    return this.service.updateRules(deviceId, context, dto.rules);
  }

  @Delete()
  @ApiOperation({ summary: 'Restablecer las reglas por defecto del contexto' })
  @ApiOkResponse({ type: ContextRulesDto })
  reset(
    @Param('deviceId', ParseUUIDPipe) deviceId: string,
    @Param('context', new ParseActiveContextPipe()) context: UserContext,
  ): Promise<ContextRulesDto> {
    return this.service.resetRules(deviceId, context);
  }
}
