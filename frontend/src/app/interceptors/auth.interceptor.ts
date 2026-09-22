import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthService } from '../service/auth.service';
import { TokenService } from '../service/token.service';

/**
 * Agrega el token JWT a cada peticion. La peticion es inmutable: se clona con el header Authorization.
 * Si la API responde 401, la sesion vencio o dejo de valer: se borra y se vuelve al login.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token: string | null = inject(TokenService).obtenerToken();
  const authService: AuthService = inject(AuthService);
  const router: Router = inject(Router);

  const peticion: HttpRequest<unknown> =
    token && !esPublica(req) ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(peticion).pipe(
    catchError((error: HttpErrorResponse) => {
      // En el login, un 401 es una clave incorrecta y lo muestra el formulario
      if (error.status === 401 && !req.url.includes('/api/v1/auth/')) {
        authService.cerrarSesionLocal();
        router.navigate(['/login'], { queryParams: { sesion: 'vencida' } });
      }
      return throwError(() => error);
    }),
  );
};

/** El login y el registro de tienda no llevan token: uno viejo o vencido no debe estorbarlos. */
function esPublica(req: HttpRequest<unknown>): boolean {
  return req.url.endsWith('/api/v1/auth/login') || (req.method === 'POST' && req.url.endsWith('/api/v1/empresas'));
}
