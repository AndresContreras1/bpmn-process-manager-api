import { DOCUMENT } from '@angular/common';
import { Injectable, LOCALE_ID, inject } from '@angular/core';
import { Navigation, Router, UrlTree } from '@angular/router';

import { Idioma } from '../models/preferencias.model';

/** Un idioma de la web y como se llama en si mismo: quien no lee el idioma actual tiene que reconocer el suyo. */
export interface OpcionDeIdioma {
  codigo: Idioma;
  nombre: string;
}

export const IDIOMAS: readonly OpcionDeIdioma[] = [
  { codigo: 'es-CO', nombre: 'Español (Colombia)' },
  { codigo: 'en-US', nombre: 'English (United States)' },
  { codigo: 'en-AU', nombre: 'English (Australia)' },
  { codigo: 'fr-FR', nombre: 'Français (France)' },
];

/** La moneda de cada idioma, la que usa el pipe currency cuando no se le dice otra. */
export const MONEDA: Readonly<Record<Idioma, string>> = {
  'es-CO': 'COP',
  'en-US': 'USD',
  'en-AU': 'AUD',
  'fr-FR': 'EUR',
};

/** La cookie que NGINX lee para servir la compilacion de cada idioma, antes que el Accept-Language del navegador. */
export const COOKIE_DE_IDIOMA = 'idioma';

/** Un año: lo que dura la preferencia en el navegador si nadie la cambia. */
const UN_ANO = 60 * 60 * 24 * 365;

/**
 * El idioma de la web. Cada idioma es otra compilacion, en otra carpeta: cambiarlo es guardar la cookie que NGINX lee
 * y abrir la misma pantalla en la carpeta del idioma nuevo. Con `ng serve` no hay carpetas, y solo se guarda la
 * cookie.
 */
@Injectable({ providedIn: 'root' })
export class IdiomaService {
  private readonly documento: Document = inject(DOCUMENT);
  private readonly router: Router = inject(Router);
  /** El idioma con que se compilo esta copia de la web. */
  readonly actual: Idioma = normalizar(inject(LOCALE_ID));

  esDeLaWeb(codigo: string | null | undefined): codigo is Idioma {
    return IDIOMAS.some((opcion: OpcionDeIdioma) => opcion.codigo === codigo);
  }

  /** Guarda la preferencia en la cookie y, si es otro idioma, abre la misma pantalla en el. */
  ir(idioma: Idioma): void {
    this.recordar(idioma);
    if (idioma === this.actual) {
      return;
    }
    const carpeta: string = `/${this.actual}/`;
    const ubicacion: Location = this.documento.location;
    if (!ubicacion.pathname.startsWith(carpeta)) {
      return;
    }
    ubicacion.assign(`/${idioma}${this.rutaActual()}`);
  }

  /**
   * La pantalla a la que va la web, sin la carpeta del idioma: si el router esta navegando, la de destino. Al entrar,
   * las preferencias llegan mientras el login todavia lleva a la lista de procesos, y la direccion del navegador
   * sigue diciendo /login.
   */
  private rutaActual(): string {
    const navegacion: Navigation | null = this.router.currentNavigation();
    const destino: UrlTree | undefined = navegacion?.finalUrl ?? navegacion?.extractedUrl;
    return destino ? this.router.serializeUrl(destino) : this.router.url;
  }

  /** Solo la cookie, para que la proxima pagina que pida el navegador ya venga en ese idioma. */
  recordar(idioma: Idioma): void {
    const segura: string = this.documento.location.protocol === 'https:' ? '; Secure' : '';
    this.documento.cookie = `${COOKIE_DE_IDIOMA}=${idioma}; Path=/; Max-Age=${UN_ANO}; SameSite=Lax${segura}`;
  }
}

/** El LOCALE_ID de una compilacion es el codigo de su idioma; el del servidor de desarrollo, el de origen. */
function normalizar(locale: string): Idioma {
  const codigo: string = locale === 'en' ? 'en-US' : locale;
  return (IDIOMAS.find((opcion: OpcionDeIdioma) => opcion.codigo === codigo)?.codigo ?? 'en-US') as Idioma;
}
