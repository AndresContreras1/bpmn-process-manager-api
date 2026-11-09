import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from '../service/auth.service';

/**
 * D29: la sesion viaja sola, en cookies HttpOnly que el navegador manda a la API; aqui no se firma ninguna peticion.
 *
 * Si la API responde 401, el acceso vencio: se renueva con la cookie de refresco y se reintenta la peticion, asi que
 * una sesion larga no vuelve al login cada quince minutos. Solo se cierra la sesion cuando no habia nadie dentro o
 * cuando la propia renovacion es rechazada.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService: AuthService = inject(AuthService);
  const router: Router = inject(Router);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // En las rutas de sesion un 401 es la respuesta, no un acceso vencido: clave incorrecta o refresco invalido
      if (error.status !== 401 || esDeSesion(req)) {
        return throwError(() => error);
      }
      if (!authService.estaAutenticado()) {
        return cerrar(authService, router, error);
      }
      return authService.renovar().pipe(
        switchMap(() => next(req)),
        // Falla la renovacion, o el reintento vuelve a dar 401: la sesion nueva tampoco vale
        catchError((fallo: HttpErrorResponse) =>
          fallo.status === 401 ? cerrar(authService, router, fallo) : throwError(() => fallo),
        ),
      );
    }),
  );
};

/** Entrar, renovar, salir y cambiar la clave: su 401 lo muestra la pantalla, no lo arregla una renovacion. */
function esDeSesion(req: HttpRequest<unknown>): boolean {
  return req.url.includes('/api/v1/auth/');
}

/** La sesion dejo de valer: se olvida y se vuelve al login diciendo por que. */
function cerrar(authService: AuthService, router: Router, error: HttpErrorResponse): Observable<never> {
  authService.cerrarSesionLocal();
  router.navigate(['/login'], { queryParams: { sesion: 'vencida' } });
  return throwError(() => error);
}
