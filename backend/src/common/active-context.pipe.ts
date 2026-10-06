import { BadRequestException, PipeTransform } from '@nestjs/common';
import {
  ACTIVE_CONTEXTS,
  isActiveContext,
  UserContext,
} from '../domain/index.js';

/** Acepta solo contextos activos (HOME, STREET, OTHER) en parámetros de ruta. */
export class ParseActiveContextPipe implements PipeTransform<
  string,
  UserContext
> {
  transform(value: string): UserContext {
    if (!isActiveContext(value)) {
      throw new BadRequestException(
        `Contexto inválido: ${value}. Contextos activos: ${ACTIVE_CONTEXTS.join(', ')}`,
      );
    }
    return value;
  }
}
