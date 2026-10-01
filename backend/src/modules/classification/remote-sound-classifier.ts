import { Logger, ServiceUnavailableException } from '@nestjs/common';
import { LabelMapper } from './label-mapper.js';
import {
  AudioInput,
  ClassificationResult,
  RawPrediction,
  SoundClassifier,
} from './sound-classifier.interface.js';

export interface RemoteClassifierOptions {
  url: string;
  apiKey?: string;
  timeoutMs: number;
}

/**
 * Delega la inferencia a un servicio HTTP externo (p. ej. un microservicio
 * en Python con el modelo que se elija). Contrato esperado:
 *
 *   POST {url}
 *   { "audioBase64": string, "format": string, "sampleRate": number, "channels": number }
 *   → 200 { "model"?: { "name": string, "version": string },
 *           "predictions": [{ "label": string, "score": number }] }
 */
export class RemoteSoundClassifier implements SoundClassifier {
  readonly info = { name: 'remote', version: 'unknown' };
  private readonly logger = new Logger(RemoteSoundClassifier.name);

  constructor(
    private readonly options: RemoteClassifierOptions,
    private readonly mapper = new LabelMapper(),
  ) {}

  isReady(): boolean {
    return true;
  }

  async classify(input: AudioInput): Promise<ClassificationResult[]> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };
    if (this.options.apiKey)
      headers.Authorization = `Bearer ${this.options.apiKey}`;

    let response: Response;
    try {
      response = await fetch(this.options.url, {
        method: 'POST',
        headers,
        body: JSON.stringify({
          audioBase64: input.data.toString('base64'),
          format: input.format,
          sampleRate: input.sampleRate,
          channels: input.channels,
        }),
        signal: AbortSignal.timeout(this.options.timeoutMs),
      });
    } catch (error) {
      this.logger.error(`Servicio de IA no disponible: ${String(error)}`);
      throw new ServiceUnavailableException(
        'El servicio de clasificación no está disponible',
      );
    }

    if (!response.ok) {
      this.logger.error(`Servicio de IA respondió ${response.status}`);
      throw new ServiceUnavailableException(
        'El servicio de clasificación falló',
      );
    }

    const body = (await response.json()) as {
      model?: { name?: string; version?: string };
      predictions?: RawPrediction[];
    };
    if (body.model?.name) this.info.name = body.model.name;
    if (body.model?.version) this.info.version = body.model.version;
    return this.mapper.map(body.predictions ?? []);
  }
}
