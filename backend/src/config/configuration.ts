export type DatabaseProvider = 'memory' | 'supabase';
export type ClassifierProvider = 'mock' | 'remote';

export interface AppConfig {
  port: number;
  database: {
    provider: DatabaseProvider;
    supabaseUrl?: string;
    supabaseKey?: string;
  };
  classifier: {
    provider: ClassifierProvider;
    remoteUrl?: string;
    remoteApiKey?: string;
    timeoutMs: number;
  };
  alerts: {
    /** Confianza mínima global (0-1) para generar una alerta. */
    minConfidence: number;
    /** Segundos durante los cuales no se repite la misma alerta en un dispositivo. */
    cooldownSeconds: number;
  };
  audio: {
    maxBytes: number;
  };
  auth: {
    /** Si se define, todas las rutas (salvo @Public) exigen la cabecera x-api-key. */
    apiKey?: string;
  };
}

function num(value: string | undefined, fallback: number): number {
  const n = Number(value);
  return value !== undefined && value !== '' && Number.isFinite(n)
    ? n
    : fallback;
}

export default (): AppConfig => {
  const env = process.env;
  const provider = (env.DB_PROVIDER || 'supabase') as DatabaseProvider;

  return {
    port: num(env.PORT, 3000),
    database: {
      provider,
      supabaseUrl: env.SUPABASE_URL,
      supabaseKey: env.SUPABASE_SERVICE_ROLE_KEY,
    },
    classifier: {
      provider: (env.CLASSIFIER_PROVIDER ?? 'mock') as ClassifierProvider,
      remoteUrl: env.CLASSIFIER_REMOTE_URL,
      remoteApiKey: env.CLASSIFIER_REMOTE_API_KEY,
      timeoutMs: num(env.CLASSIFIER_TIMEOUT_MS, 5000),
    },
    alerts: {
      minConfidence: num(env.MIN_CONFIDENCE, 0.6),
      cooldownSeconds: num(env.ALERT_COOLDOWN_SECONDS, 10),
    },
    audio: {
      maxBytes: num(env.MAX_AUDIO_BYTES, 2 * 1024 * 1024),
    },
    auth: {
      apiKey: env.API_KEY || undefined,
    },
  };
};

export function validateConfig(
  config: Record<string, unknown>,
): Record<string, unknown> {
  // Supabase es la persistencia por defecto; "memory" solo existe para tests.
  const db = config.DB_PROVIDER || 'supabase';
  if (db !== 'memory' && db !== 'supabase') {
    throw new Error(
      `DB_PROVIDER inválido: ${String(db)} (usa "supabase" o "memory" solo en tests)`,
    );
  }
  if (db === 'supabase') {
    if (!config.SUPABASE_URL || !config.SUPABASE_SERVICE_ROLE_KEY) {
      throw new Error(
        'Faltan SUPABASE_URL y/o SUPABASE_SERVICE_ROLE_KEY en backend/.env (ver .env.example)',
      );
    }
    if (!/^https?:\/\//.test(String(config.SUPABASE_URL))) {
      throw new Error(
        'SUPABASE_URL debe ser la URL del proyecto, p. ej. https://xxxx.supabase.co',
      );
    }
  }
  const classifier = config.CLASSIFIER_PROVIDER ?? 'mock';
  if (classifier !== 'mock' && classifier !== 'remote') {
    throw new Error(
      `CLASSIFIER_PROVIDER inválido: ${String(classifier)} (usa "mock" o "remote")`,
    );
  }
  if (classifier === 'remote' && !config.CLASSIFIER_REMOTE_URL) {
    throw new Error(
      'CLASSIFIER_PROVIDER=remote requiere CLASSIFIER_REMOTE_URL',
    );
  }
  return config;
}
