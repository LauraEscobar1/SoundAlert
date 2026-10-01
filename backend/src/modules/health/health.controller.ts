import { Controller, Get, Inject } from '@nestjs/common';
import { Public } from '../../auth/public.decorator.js';
import { DatabaseHealth } from '../../database/repositories.js';
import { ConfigService } from '@nestjs/config';
import {
  ApiOkResponse,
  ApiOperation,
  ApiProperty,
  ApiTags,
} from '@nestjs/swagger';
import { AppConfig } from '../../config/configuration.js';
import { SOUND_CLASSIFIER } from '../classification/sound-classifier.interface.js';
import type { SoundClassifier } from '../classification/sound-classifier.interface.js';

export class HealthDto {
  @ApiProperty({ example: 'ok', enum: ['ok', 'degraded'] })
  status: string;

  @ApiProperty({ example: { provider: 'supabase', ok: true } })
  database: { provider: string; ok: boolean; error?: string };

  @ApiProperty({ example: { name: 'mock', version: '0.0.0', ready: true } })
  classifier: { name: string; version: string; ready: boolean };
}

@ApiTags('health')
@Public()
@Controller('health')
export class HealthController {
  constructor(
    @Inject(SOUND_CLASSIFIER) private readonly classifier: SoundClassifier,
    private readonly config: ConfigService,
    private readonly db: DatabaseHealth,
  ) {}

  @Get()
  @ApiOperation({
    summary: 'Estado del servicio y de la conexión con la base de datos',
  })
  @ApiOkResponse({ type: HealthDto })
  async check(): Promise<HealthDto> {
    const db = await this.db.check();
    return {
      status: db.ok ? 'ok' : 'degraded',
      database: {
        provider:
          this.config.getOrThrow<AppConfig['database']>('database').provider,
        ...db,
      },
      classifier: { ...this.classifier.info, ready: this.classifier.isReady() },
    };
  }
}
