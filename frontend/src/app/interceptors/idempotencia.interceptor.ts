import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { TokenService } from '../service/token.service';

/**
 * Marca cada POST con sesion con una Idempotency-Key. Si el mismo envio llega dos veces —un doble clic, o el
 * reintento que hace el interceptor de sesion despues de renovar el token— la API devuelve la respuesta de la
 * primera vez en lugar de crear otro recurso.
 *
 * Va antes del interceptor de sesion a proposito: asi la clave se pone una sola vez y el reintento manda la misma.
 * Si se pusiera despues, cada reintento llevaria una clave nueva y no protegeria de nada.
 *
 * Los POST publicos no la usan, porque la API solo la aplica a los autenticados: sin sesion no hay a quien
 * atribuir la clave.
 */
export const idempotenciaInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.method !== 'POST' || inject(TokenService).obtenerToken() === null) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { 'Idempotency-Key': clave() } }));
};

/** Un identificador irrepetible por envio. La API acepta hasta cien caracteres. */
function clave(): string {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  // randomUUID solo existe en un contexto seguro; servida por http en una IP queda getRandomValues, que si esta
  const bytes: Uint8Array = crypto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes, (byte: number) => byte.toString(16).padStart(2, '0')).join('');
}
