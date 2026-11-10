import { LOCALE_ID, Pipe, PipeTransform, inject } from '@angular/core';

import { PreferenciasService } from '../service/preferencias.service';

/** Como se escribe una fecha: solo el dia, el dia con el mes en letras, o el dia con la hora. */
export type EstiloDeFecha = 'fecha' | 'fechaLarga' | 'fechaHora';

const OPCIONES: Readonly<Record<EstiloDeFecha, Intl.DateTimeFormatOptions>> = {
  fecha: { dateStyle: 'medium' },
  fechaLarga: { dateStyle: 'long' },
  fechaHora: { dateStyle: 'medium', timeStyle: 'short' },
};

/** Un formateador por idioma, estilo y zona: crearlos cuesta, y una lista pinta la misma combinacion muchas veces. */
const FORMATEADORES = new Map<string, Intl.DateTimeFormat>();

/** Solo el dia, sin hora: 2026-09-21. */
const SOLO_DIA = /^\d{4}-\d{2}-\d{2}$/;
/** Termina en Z o en un desplazamiento como +05:00. */
const CON_ZONA = /(Z|[+-]\d{2}:?\d{2})$/i;

/**
 * F12: una fecha de la API en el idioma de la web y en la zona horaria de quien la mira.
 *
 * La API responde en UTC y sin zona (2026-09-21T15:30:00), asi que se lee como UTC; el DatePipe la tomaria por hora
 * del navegador. Un dia sin hora, como el del registro, es ese dia en cualquier zona y no se mueve. No es puro: la
 * zona llega despues de pintar la pantalla, cuando la API devuelve las preferencias, y la fecha tiene que cambiar.
 */
@Pipe({ name: 'fecha', pure: false })
export class FechaPipe implements PipeTransform {
  private readonly locale: string = inject(LOCALE_ID);
  private readonly preferencias: PreferenciasService = inject(PreferenciasService);

  transform(valor: string | Date | null | undefined, estilo: EstiloDeFecha = 'fechaHora'): string {
    if (valor === null || valor === undefined || valor === '') {
      return '';
    }
    const soloDia: boolean = typeof valor === 'string' && SOLO_DIA.test(valor);
    const fecha: Date = aFecha(valor, soloDia);
    if (Number.isNaN(fecha.getTime())) {
      return String(valor);
    }
    const zona: string = soloDia ? 'UTC' : this.preferencias.zonaHoraria();
    return formateador(this.locale, soloDia && estilo === 'fechaHora' ? 'fecha' : estilo, zona).format(fecha);
  }
}

function aFecha(valor: string | Date, soloDia: boolean): Date {
  if (valor instanceof Date) {
    return valor;
  }
  if (soloDia) {
    return new Date(`${valor}T00:00:00Z`);
  }
  return new Date(CON_ZONA.test(valor) ? valor : `${valor}Z`);
}

function formateador(locale: string, estilo: EstiloDeFecha, zona: string): Intl.DateTimeFormat {
  const clave: string = `${locale}|${estilo}|${zona}`;
  let guardado: Intl.DateTimeFormat | undefined = FORMATEADORES.get(clave);
  if (!guardado) {
    try {
      guardado = new Intl.DateTimeFormat(locale, { ...OPCIONES[estilo], timeZone: zona });
    } catch {
      // Una zona que este navegador no conoce: mejor la hora en UTC que una pantalla rota
      guardado = new Intl.DateTimeFormat(locale, { ...OPCIONES[estilo], timeZone: 'UTC' });
    }
    FORMATEADORES.set(clave, guardado);
  }
  return guardado;
}
