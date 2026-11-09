import { provideHttpClient, withFetch, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './interceptors/auth.interceptor';
import { idempotenciaInterceptor } from './interceptors/idempotencia.interceptor';
import { AuthService } from './service/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    // El orden importa: la clave de idempotencia se pone antes de pasar por la sesion, para que el reintento que hace
    // authInterceptor tras renovarla vaya con la misma clave y no cree un recurso de mas.
    // D29: lo que cambia algo lleva el valor de la cookie XSRF-TOKEN en la cabecera X-XSRF-TOKEN; HttpClient lo
    // copia solo en las peticiones a la propia API, y la API lo compara con la cookie.
    provideHttpClient(
      withFetch(),
      withInterceptors([idempotenciaInterceptor, authInterceptor]),
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
    ),
    // El login ya pide el token CSRF, asi que la cookie se pide antes de pintar nada
    provideAppInitializer(() => inject(AuthService).prepararCsrf()),
  ],
};
