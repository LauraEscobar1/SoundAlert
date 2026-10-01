import { SetMetadata } from '@nestjs/common';

export const IS_PUBLIC = 'isPublic';

/** Marca un endpoint como accesible sin autenticación (p. ej. /health). */
export const Public = () => SetMetadata(IS_PUBLIC, true);
