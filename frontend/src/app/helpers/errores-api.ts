import { HttpErrorResponse } from '@angular/common/http';
import { FormGroup } from '@angular/forms';

import { ProblemDetail } from '../models/problem-detail.model';

/** Mensaje general de un error de la API, para mostrarlo encima del formulario. */
export function mensajeDeError(error: HttpErrorResponse): string {
  if (error.status === 0) {
    return 'The server cannot be reached. Check that the backend is running.';
  }
  if (error.status === 403) {
    return 'Your role does not allow this action.';
  }
  const problema: ProblemDetail | null = error.error as ProblemDetail | null;
  return problema?.detail ?? 'Something went wrong. Please try again.';
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
