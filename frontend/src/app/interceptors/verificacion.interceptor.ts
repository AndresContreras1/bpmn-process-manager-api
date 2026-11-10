import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { VerificacionService } from '../service/verificacion.service';

/** El titulo con que la API dice que falta una verificacion reciente (JwtAccessDeniedHandler). */
export const VERIFICACION_REQUERIDA = 'Verificación requerida';

/**
 * F14: si la API pide haber confirmado la identidad hace poco, se le pide a la persona y se repite la peticion. Si lo
 * cancela, la peticion falla con el 403 de siempre, que la pantalla muestra.
 *
 * Va antes del interceptor de sesion: el reintento vuelve a pasar por el, por si el acceso vencio mientras tanto.
 */
export const verificacionInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.url.includes('/api/v1/auth/reautenticacion')) {
    return next(req);
  }
  const verificacion: VerificacionService = inject(VerificacionService);
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 403 || error.error?.title !== VERIFICACION_REQUERIDA) {
        return throwError(() => error);
      }
      return verificacion
        .pedir()
        .pipe(switchMap((confirmada: boolean) => (confirmada ? next(req) : throwError(() => error))));
    }),
  );
};
