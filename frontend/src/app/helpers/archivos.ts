import { HttpErrorResponse } from '@angular/common/http';

import { environment } from '../../environments/environment';
import { ProblemDetail } from '../models/problem-detail.model';
import { mensajeDeError } from './errores-api';

/**
 * PR F20: abre un enlace firmado de la API. La API lo sirve siempre como adjunto, asi que el navegador baja el archivo
 * y la pagina se queda donde estaba; el enlace vale cinco minutos y no necesita sesion, por eso se pide justo antes.
 */
export function abrirDescarga(url: string): void {
  const enlace: HTMLAnchorElement = document.createElement('a');
  enlace.href = `${environment.apiUrl}${url}`;
  // Vacio: el nombre lo dice la cabecera Content-Disposition de la API, ya limpio
  enlace.download = '';
  enlace.rel = 'noopener';
  document.body.appendChild(enlace);
  enlace.click();
  enlace.remove();
}

/** El tamano de un archivo como lo lee una persona: 812 B, 48 KB, 3.2 MB. */
export function tamanoLegible(bytes: number, idioma: string): string {
  const formato = (valor: number, decimales: number): string =>
    new Intl.NumberFormat(idioma, { maximumFractionDigits: decimales }).format(valor);
  if (bytes < 1024) {
    return `${formato(bytes, 0)} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${formato(bytes / 1024, 0)} KB`;
  }
  return `${formato(bytes / (1024 * 1024), 1)} MB`;
}

/**
 * Lo que se dice cuando una subida no entra. La API explica el 413 (el tope de 25 MB o el espacio de la
 * organizacion) y el 415 (un tipo que no acepta); NGINX corta antes un cuerpo grande, con una pagina sin ese detalle.
 * Vive aqui y no con los demas errores para no sumarle peso a la carga inicial: solo lo usan las pantallas que suben.
 */
export function mensajeDeSubida(error: HttpErrorResponse): string {
  const detalle: string | undefined = (error.error as ProblemDetail | null)?.detail;
  if (error.status === 413) {
    return detalle ?? $localize`The file is larger than 25 MB, or the organization has no space left for documents.`;
  }
  if (error.status === 415) {
    return detalle ?? $localize`That type of file is not accepted.`;
  }
  return mensajeDeError(error);
}
