import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { APP_GUARD } from '@nestjs/core';
import { AuthGuard } from './auth/auth.guard.js';
import configuration, { validateConfig } from './config/configuration.js';
import { DatabaseModule } from './database/database.module.js';
import { AlertsModule } from './modules/alerts/alerts.module.js';
import { CatalogModule } from './modules/catalog/catalog.module.js';
import { ClassificationModule } from './modules/classification/classification.module.js';
import { DetectionsModule } from './modules/detections/detections.module.js';
import { DevicesModule } from './modules/devices/devices.module.js';
import { HealthModule } from './modules/health/health.module.js';
import { RulesModule } from './modules/rules/rules.module.js';
import { UsersModule } from './modules/users/users.module.js';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      load: [configuration],
      validate: validateConfig,
    }),
    DatabaseModule,
    ClassificationModule,
    CatalogModule,
    UsersModule,
    DevicesModule,
    RulesModule,
    AlertsModule,
    DetectionsModule,
    HealthModule,
  ],
  providers: [{ provide: APP_GUARD, useClass: AuthGuard }],
})
export class AppModule {}
