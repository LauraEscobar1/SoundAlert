import { Controller, Get } from '@nestjs/common';
import { ApiOkResponse, ApiOperation, ApiTags } from '@nestjs/swagger';
import {
  ACTIVE_CONTEXTS,
  PRIORITY_POLICIES,
  PriorityLevel,
  SOUND_CATALOG,
  SoundCategory,
} from '../../domain/index.js';
import {
  toContextDto,
  toPriorityDto,
  toSoundTypeDto,
} from './catalog.mapper.js';
import { ContextDto, PriorityDto, SoundTypeDto } from './dto/catalog.dto.js';

@ApiTags('catalog')
@Controller('catalog')
export class CatalogController {
  @Get('sounds')
  @ApiOperation({ summary: 'Tipos de sonido que SoundAlert reconoce' })
  @ApiOkResponse({ type: [SoundTypeDto] })
  sounds(): SoundTypeDto[] {
    return [...SOUND_CATALOG.values()]
      .filter((d) => d.category !== SoundCategory.UNKNOWN)
      .sort(
        (a, b) =>
          PRIORITY_POLICIES[b.defaultPriority].rank -
          PRIORITY_POLICIES[a.defaultPriority].rank,
      )
      .map(toSoundTypeDto);
  }

  @Get('priorities')
  @ApiOperation({ summary: 'Niveles de prioridad con su vibración y color' })
  @ApiOkResponse({ type: [PriorityDto] })
  priorities(): PriorityDto[] {
    return [
      PriorityLevel.DANGER,
      PriorityLevel.ATTENTION,
      PriorityLevel.INFORMATION,
    ].map(toPriorityDto);
  }

  @Get('contexts')
  @ApiOperation({
    summary: 'Contextos disponibles (casa, calle, universidad, trabajo)',
  })
  @ApiOkResponse({ type: [ContextDto] })
  contexts(): ContextDto[] {
    return ACTIVE_CONTEXTS.map(toContextDto);
  }
}
