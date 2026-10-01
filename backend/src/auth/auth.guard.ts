import {
  CanActivate,
  ExecutionContext,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Reflector } from '@nestjs/core';
import { timingSafeEqual } from 'node:crypto';
import type { Request } from 'express';
import { AppConfig } from '../config/configuration.js';
import { IS_PUBLIC } from './public.decorator.js';

export const API_KEY_HEADER = 'x-api-key';

/**
 * Punto único de autenticación (guard global).
 *
 * Hoy: si API_KEY está definida, exige la cabecera `x-api-key`; si no, deja pasar
 * (desarrollo). Para añadir autenticación real (p. ej. JWT de Supabase Auth) se
 * sustituye la lógica de `canActivate` y se comprueba que el dispositivo
 * pertenece al usuario; controllers y servicios no cambian.
 */
@Injectable()
export class AuthGuard implements CanActivate {
  private readonly apiKey?: Buffer;

  constructor(
    private readonly reflector: Reflector,
    config: ConfigService,
  ) {
    const key = config.get<AppConfig['auth']>('auth')?.apiKey;
    this.apiKey = key ? Buffer.from(key) : undefined;
  }

  canActivate(context: ExecutionContext): boolean {
    if (!this.apiKey) return true;
    const isPublic = this.reflector.getAllAndOverride<boolean>(IS_PUBLIC, [
      context.getHandler(),
      context.getClass(),
    ]);
    if (isPublic) return true;

    const provided = context
      .switchToHttp()
      .getRequest<Request>()
      .header(API_KEY_HEADER);
    const given = Buffer.from(provided ?? '');
    if (
      given.length !== this.apiKey.length ||
      !timingSafeEqual(given, this.apiKey)
    ) {
      throw new UnauthorizedException('API key inválida o ausente');
    }
    return true;
  }
}
