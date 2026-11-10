import { Usuario } from './usuario.model';

export interface LoginRequest {
  email: string;
  password: string;
}

/**
 * Respuesta del login, de la renovacion y del cambio de clave (D29): cuanto vive el acceso, quien entro y, desde F13,
 * cuanto aguanta la sesion sin renovarse y cuanto le queda. Los tokens van en cookies HttpOnly que este codigo no
 * puede leer.
 */
export interface LoginResponse {
  expiresIn: number;
  usuario: Usuario;
  segundoFactorPendiente?: boolean;
  /** Null cuando la respuesta no renovo la sesion, como tras una verificacion reciente. */
  inactividadSegundos?: number | null;
  restanteSegundos?: number | null;
}

/** F13: GET /api/v1/auth/sesion, lo que la web pide al arrancar en vez de recordarlo en el navegador. */
export interface SesionActual {
  usuario: Usuario;
  segundoFactorPendiente: boolean;
  inactividadSegundos: number | null;
  restanteSegundos: number | null;
}
