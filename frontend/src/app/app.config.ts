import { provideHttpClient, withFetch, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import {
  ApplicationConfig,
  DEFAULT_CURRENCY_CODE,
  inject,
  provideAppInitializer,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './interceptors/auth.interceptor';
import { idempotenciaInterceptor } from './interceptors/idempotencia.interceptor';
import { verificacionInterceptor } from './interceptors/verificacion.interceptor';
import { AuthService } from './service/auth.service';
import { IdiomaService, MONEDA } from './service/idioma.service';
import { PreferenciasService } from './service/preferencias.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    // El orden importa: la clave de idempotencia se pone antes de pasar por la sesion, para que el reintento que hace
    // authInterceptor tras renovarla vaya con la misma clave y no cree un recurso de mas. F14: la verificacion reciente
    // va entre las dos, y su reintento pasa otra vez por la sesion.
    // D29: lo que cambia algo lleva el valor de la cookie XSRF-TOKEN en la cabecera X-XSRF-TOKEN; HttpClient lo
    // copia solo en las peticiones a la propia API, y la API lo compara con la cookie.
    provideHttpClient(
      withFetch(),
      withInterceptors([idempotenciaInterceptor, verificacionInterceptor, authInterceptor]),
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
    ),
    // El login ya pide el token CSRF, asi que la cookie se pide antes de pintar nada; despues, F13, quien entro, que
    // ya no se recuerda en el navegador: las guardas leen la sesion que trae la API
    provideAppInitializer(() => inject(AuthService).iniciar()),
    // F12: con cada sesion, el idioma y la zona horaria de la persona; si el idioma es otro, la web se abre en el suyo
    provideAppInitializer(() => inject(PreferenciasService).seguirLaSesion()),
    // F12: cada idioma con su moneda, la del pipe currency cuando no se le dice otra
    { provide: DEFAULT_CURRENCY_CODE, useFactory: () => MONEDA[inject(IdiomaService).actual] },
  ],
};
