import { Global, Logger, Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AppConfig } from '../../config/configuration.js';
import { MockSoundClassifier } from './mock-sound-classifier.js';
import { RemoteSoundClassifier } from './remote-sound-classifier.js';
import {
  SOUND_CLASSIFIER,
  SoundClassifier,
} from './sound-classifier.interface.js';

/**
 * Punto único donde se elige la implementación de IA.
 * Para añadir un modelo nuevo: crear una clase que implemente
 * `SoundClassifier` y añadir un `case` aquí (y en la validación de config).
 */
@Global()
@Module({
  providers: [
    {
      provide: SOUND_CLASSIFIER,
      inject: [ConfigService],
      useFactory: (config: ConfigService): SoundClassifier => {
        const opts = config.getOrThrow<AppConfig['classifier']>('classifier');
        const logger = new Logger('ClassificationModule');
        switch (opts.provider) {
          case 'remote':
            logger.log(`Clasificador remoto: ${opts.remoteUrl}`);
            return new RemoteSoundClassifier({
              url: opts.remoteUrl!,
              apiKey: opts.remoteApiKey,
              timeoutMs: opts.timeoutMs,
            });
          case 'mock':
          default:
            logger.warn(
              'Usando clasificador MOCK: la IA aún no está integrada',
            );
            return new MockSoundClassifier();
        }
      },
    },
  ],
  exports: [SOUND_CLASSIFIER],
})
export class ClassificationModule {}
