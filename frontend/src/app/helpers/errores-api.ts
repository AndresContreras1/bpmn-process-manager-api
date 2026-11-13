import { HttpErrorResponse } from '@angular/common/http';
import { FormGroup } from '@angular/forms';

import { ProblemDetail } from '../models/problem-detail.model';

/** Mensaje general de un error de la API, para mostrarlo encima del formulario. */
export function mensajeDeError(error: HttpErrorResponse): string {
  if (error.status === 0) {
    return $localize`The server cannot be reached. Check that the backend is running.`;
  }
  if (error.status === 403) {
    return $localize`Your role does not allow this action.`;
  }
  const problema: ProblemDetail | null = error.error as ProblemDetail | null;
  return problema?.detail ?? $localize`Something went wrong. Please try again.`;
}

/**
 * El mensaje general y, detras, el de cada campo que la API rechazo en el mapa errors de un 400: sirve donde la
 * pantalla no tiene un control por campo, como los datos libres de una tarea que la API revisa contra su formulario.
 */
export function mensajeConLosCampos(error: HttpErrorResponse): string {
  const errores: Record<string, string> = (error.error as ProblemDetail | null)?.errors ?? {};
  const porCampo: string[] = Object.entries(errores).map(([campo, mensaje]) => `${campo}: ${mensaje}`);
  return [mensajeDeError(error), ...porCampo].join(' ');
}

/**
 * Un 409 tiene varios motivos —un nombre repetido, una regla de negocio, un dato unico— y el titulo del
 * ProblemDetail es lo que los separa. Este es el de editar sobre una version que ya cambio.
 */
export function esConflictoDeVersion(error: HttpErrorResponse): boolean {
  return error.status === 409 && (error.error as ProblemDetail | null)?.title === 'Conflicto de versión';
}

/** Marca cada control con el mensaje que la API devolvio para ese campo en el mapa errors de un 400. */
export function marcarErroresDelServidor(formulario: FormGroup, error: HttpErrorResponse): void {
  const errores: Record<string, string> = (error.error as ProblemDetail | null)?.errors ?? {};
  for (const [campo, mensaje] of Object.entries(errores)) {
    formulario.get(campo)?.setErrors({ servidor: mensaje });
  }
}
