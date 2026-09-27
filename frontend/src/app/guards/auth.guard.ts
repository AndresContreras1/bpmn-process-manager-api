import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../service/auth.service';
import { TokenService } from '../service/token.service';

/**
 * Deja entrar solo con una sesion abierta; si no, manda al login y recuerda a que pagina iba el usuario.
 *
 * Y si entro con una clave temporal, lo manda a cambiarla: hasta que lo haga la API le responde 403 a todo lo que
 * no sea cambiarla, salir o renovar, asi que dejarle navegar seria ensenarle pantallas que no van a cargar.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const router: Router = inject(Router);
  if (!inject(AuthService).estaAutenticado()) {
    return router.createUrlTree(['/login'], { queryParams: { volver: state.url } });
  }
  const debeCambiarClave: boolean = inject(TokenService).obtenerUsuario()?.debeCambiarClave === true;
  return debeCambiarClave && !state.url.startsWith('/cuenta')
    ? router.createUrlTree(['/cuenta'], { queryParams: { clave: 'temporal' } })
    : true;
};
