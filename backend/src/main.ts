import { Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { AppModule } from './app.module.js';
import { API_KEY_HEADER } from './auth/auth.guard.js';
import { setupApp } from './setup-app.js';

async function bootstrap() {
  const app = await NestFactory.create(AppModule, { bodyParser: false });
  setupApp(app);

  const document = SwaggerModule.createDocument(
    app,
    new DocumentBuilder()
      .setTitle('SoundAlert API')
      .setDescription(
        'Detecta → clasifica → alerta. Convierte sonidos importantes del entorno en alertas visuales y vibraciones.',
      )
      .setVersion('0.1.0')
      // Solo se usa si API_KEY está definida en el .env del backend.
      .addApiKey(
        { type: 'apiKey', in: 'header', name: API_KEY_HEADER },
        API_KEY_HEADER,
      )
      .addSecurityRequirements(API_KEY_HEADER)
      .build(),
  );
  SwaggerModule.setup('docs', app, document, {
    swaggerOptions: { persistAuthorization: true },
  });

  const port = app.get(ConfigService).getOrThrow<number>('port');
  await app.listen(port);
  Logger.log(
    `SoundAlert API en http://localhost:${port} (docs: /docs)`,
    'Bootstrap',
  );
}
await bootstrap();
