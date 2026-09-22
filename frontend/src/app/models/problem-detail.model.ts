/** Cuerpo de los errores de la API (RFC 9457). Un 400 de validacion trae ademas el mapa errors por campo. */
export interface ProblemDetail {
  title: string;
  status: number;
  detail: string;
  instance?: string;
  errors?: Record<string, string>;
}
