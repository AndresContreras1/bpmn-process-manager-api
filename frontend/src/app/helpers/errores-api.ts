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

/** Marca cada control con el mensaje que la API devolvio para ese campo en el mapa errors de un 400. */
export function marcarErroresDelServidor(formulario: FormGroup, error: HttpErrorResponse): void {
  const errores: Record<string, string> = (error.error as ProblemDetail | null)?.errors ?? {};
  for (const [campo, mensaje] of Object.entries(errores)) {
    formulario.get(campo)?.setErrors({ servidor: mensaje });
  }
}
