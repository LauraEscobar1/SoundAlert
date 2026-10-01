import { ExecutionContext, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Reflector } from '@nestjs/core';
import { AuthGuard } from './auth.guard.js';

const ctx = (headers: Record<string, string>): ExecutionContext =>
  ({
    getHandler: () => undefined,
    getClass: () => undefined,
    switchToHttp: () => ({
      getRequest: () => ({ header: (n: string) => headers[n] }),
    }),
  }) as unknown as ExecutionContext;

const guard = (apiKey?: string, isPublic = false) =>
  new AuthGuard(
    { getAllAndOverride: () => isPublic } as unknown as Reflector,
    new ConfigService({ auth: { apiKey } }),
  );

describe('AuthGuard', () => {
  it('sin API_KEY deja pasar (desarrollo)', () => {
    expect(guard().canActivate(ctx({}))).toBe(true);
  });

  it('con API_KEY exige la cabecera correcta', () => {
    expect(guard('secreto').canActivate(ctx({ 'x-api-key': 'secreto' }))).toBe(
      true,
    );
    expect(() =>
      guard('secreto').canActivate(ctx({ 'x-api-key': 'otro' })),
    ).toThrow(UnauthorizedException);
    expect(() => guard('secreto').canActivate(ctx({}))).toThrow(
      UnauthorizedException,
    );
  });

  it('las rutas @Public no la exigen', () => {
    expect(guard('secreto', true).canActivate(ctx({}))).toBe(true);
  });
});
