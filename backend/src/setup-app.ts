import { INestApplication, ValidationPipe } from '@nestjs/common';
import { json } from 'express';

/** Configuración compartida entre main.ts y los tests e2e. */
export function setupApp(app: INestApplication): void {
  app.setGlobalPrefix('api/v1', { exclude: ['health'] });
  app.use(json({ limit: '5mb' }));
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
    }),
  );
  app.enableCors();
}
