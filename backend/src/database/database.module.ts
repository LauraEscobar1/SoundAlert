import { Global, Logger, Module, Provider } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createClient, SupabaseClient } from '@supabase/supabase-js';
import { AppConfig } from '../config/configuration.js';
import {
  MemoryAlertsRepository,
  MemoryDatabaseHealth,
  MemoryDetectionsRepository,
  MemoryDevicesRepository,
  MemoryRuleOverridesRepository,
  MemoryUsersRepository,
} from './memory/memory.repositories.js';
import {
  AlertsRepository,
  DatabaseHealth,
  DetectionsRepository,
  DevicesRepository,
  RuleOverridesRepository,
  UsersRepository,
} from './repositories.js';
import {
  SupabaseAlertsRepository,
  SupabaseDatabaseHealth,
  SupabaseDetectionsRepository,
  SupabaseDevicesRepository,
  SupabaseRuleOverridesRepository,
  SupabaseUsersRepository,
} from './supabase/supabase.repositories.js';

export const SUPABASE_CLIENT = Symbol('SUPABASE_CLIENT');

const supabaseClientProvider: Provider = {
  provide: SUPABASE_CLIENT,
  inject: [ConfigService],
  useFactory: (config: ConfigService): SupabaseClient | null => {
    const db = config.getOrThrow<AppConfig['database']>('database');
    const logger = new Logger('DatabaseModule');
    if (db.provider !== 'supabase') {
      logger.warn(
        'DB_PROVIDER=memory: base de datos EN MEMORIA (solo para tests)',
      );
      return null;
    }
    logger.log(`Supabase: ${new URL(db.supabaseUrl!).host}`);
    return createClient(db.supabaseUrl!, db.supabaseKey!, {
      auth: { persistSession: false, autoRefreshToken: false },
    });
  },
};

function repository<T>(
  token: abstract new (...args: any[]) => T,
  memory: () => T,
  supabase: (client: SupabaseClient) => T,
): Provider {
  return {
    provide: token,
    inject: [SUPABASE_CLIENT],
    useFactory: (client: SupabaseClient | null) =>
      client ? supabase(client) : memory(),
  };
}

@Global()
@Module({
  providers: [
    supabaseClientProvider,
    repository(
      DatabaseHealth,
      () => new MemoryDatabaseHealth(),
      (c) => new SupabaseDatabaseHealth(c),
    ),
    repository(
      UsersRepository,
      () => new MemoryUsersRepository(),
      (c) => new SupabaseUsersRepository(c),
    ),
    repository(
      DevicesRepository,
      () => new MemoryDevicesRepository(),
      (c) => new SupabaseDevicesRepository(c),
    ),
    repository(
      RuleOverridesRepository,
      () => new MemoryRuleOverridesRepository(),
      (c) => new SupabaseRuleOverridesRepository(c),
    ),
    repository(
      DetectionsRepository,
      () => new MemoryDetectionsRepository(),
      (c) => new SupabaseDetectionsRepository(c),
    ),
    repository(
      AlertsRepository,
      () => new MemoryAlertsRepository(),
      (c) => new SupabaseAlertsRepository(c),
    ),
  ],
  exports: [
    DatabaseHealth,
    UsersRepository,
    DevicesRepository,
    RuleOverridesRepository,
    DetectionsRepository,
    AlertsRepository,
  ],
})
export class DatabaseModule {}
