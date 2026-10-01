import { SoundCategory } from '../../domain/index.js';

/**
 * CONTRATO DE IA
 * ---------------
 * Cualquier modelo (YAMNet, PANNs, un modelo propio, un servicio en Python,
 * TensorFlow.js, ONNX, etc.) se integra implementando `SoundClassifier`
 * y registrándolo en `classification.module.ts`. El resto del backend
 * no sabe ni le importa qué modelo se usa.
 */

export enum AudioFormat {
  /** PCM lineal de 16 bits little-endian, sin cabecera. */
  PCM_16LE = 'PCM_16LE',
  /** PCM float 32 bits little-endian, sin cabecera. */
  PCM_F32LE = 'PCM_F32LE',
  WAV = 'WAV',
}

export interface AudioInput {
  data: Buffer;
  format: AudioFormat;
  sampleRate: number;
  channels: number;
  durationMs?: number;
  /** Datos auxiliares (p. ej. `simulatedLabel` para el clasificador mock). */
  metadata?: Record<string, string>;
}

/** Predicción tal como la devuelve el modelo, con su etiqueta nativa. */
export interface RawPrediction {
  label: string;
  score: number;
}

/** Predicción ya traducida al dominio de SoundAlert. */
export interface ClassificationResult {
  category: SoundCategory;
  /** 0 a 1. */
  confidence: number;
  /** Etiqueta original del modelo (útil para depurar y re-entrenar). */
  rawLabel: string;
}

export interface ClassifierInfo {
  name: string;
  version: string;
}

export interface SoundClassifier {
  readonly info: ClassifierInfo;
  /** Indica si el modelo está cargado y listo para inferir. */
  isReady(): boolean;
  /** Devuelve predicciones ordenadas de mayor a menor confianza. */
  classify(input: AudioInput): Promise<ClassificationResult[]>;
}

export const SOUND_CLASSIFIER = Symbol('SOUND_CLASSIFIER');
