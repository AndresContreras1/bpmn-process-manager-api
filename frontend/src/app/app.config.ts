import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './interceptors/auth.interceptor';
import { idempotenciaInterceptor } from './interceptors/idempotencia.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    // El orden importa: la clave de idempotencia se pone antes de firmar, para que el reintento que hace
    // authInterceptor tras renovar el token vaya con la misma clave y no cree un recurso de mas
    provideHttpClient(withFetch(), withInterceptors([idempotenciaInterceptor, authInterceptor])),
  ],
};
