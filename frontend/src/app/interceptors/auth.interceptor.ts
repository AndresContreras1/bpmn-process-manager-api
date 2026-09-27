import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from '../service/auth.service';
import { TokenService } from '../service/token.service';

/**
 * Agrega el token JWT a cada peticion. La peticion es inmutable: se clona con el header Authorization.
 *
 * Si la API responde 401, el token de acceso vencio: se renueva con el de refresco y se reintenta la peticion, asi
 * que una sesion larga no vuelve al login cada quince minutos. Solo se cierra la sesion cuando ya no queda nada que
 * renovar o cuando la propia renovacion es rechazada.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenService: TokenService = inject(TokenService);
  const authService: AuthService = inject(AuthService);
  const router: Router = inject(Router);

  return next(conToken(req, tokenService.obtenerToken())).pipe(
    catchError((error: HttpErrorResponse) => {
      // En las rutas de sesion un 401 es la respuesta, no un token vencido: clave incorrecta o refresco invalido
      if (error.status !== 401 || esDeSesion(req)) {
        return throwError(() => error);
      }
      if (tokenService.obtenerRefresco() === null) {
        return cerrar(authService, router, error);
      }
      return authService.renovar().pipe(
        switchMap((token: string) => next(conToken(req, token))),
        // Falla la renovacion, o el reintento vuelve a dar 401: el token nuevo tampoco vale
        catchError((fallo: HttpErrorResponse) =>
          fallo.status === 401 ? cerrar(authService, router, fallo) : throwError(() => fallo),
        ),
      );
    }),
  );
};

/** La peticion firmada, salvo las publicas: en ellas un token viejo o vencido no debe estorbar. */
function conToken(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token && !esPublica(req) ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
}

/** El login y el registro de tienda no llevan token. */
function esPublica(req: HttpRequest<unknown>): boolean {
  return req.url.endsWith('/api/v1/auth/login') || (req.method === 'POST' && req.url.endsWith('/api/v1/empresas'));
}

/** Entrar, renovar, salir y cambiar la clave: su 401 lo muestra la pantalla, no lo arregla una renovacion. */
function esDeSesion(req: HttpRequest<unknown>): boolean {
  return req.url.includes('/api/v1/auth/');
}

/** La sesion dejo de valer: se borra y se vuelve al login diciendo por que. */
function cerrar(authService: AuthService, router: Router, error: HttpErrorResponse): Observable<never> {
  authService.cerrarSesionLocal();
  router.navigate(['/login'], { queryParams: { sesion: 'vencida' } });
  return throwError(() => error);
}
