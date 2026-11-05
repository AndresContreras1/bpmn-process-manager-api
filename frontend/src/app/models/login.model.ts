import { Usuario } from './usuario.model';

export interface LoginRequest {
  email: string;
  password: string;
}

/**
 * Respuesta del login, de la renovacion y del cambio de clave (D29): cuanto vive el acceso y quien entro. Los tokens
 * van en cookies HttpOnly que este codigo no puede leer.
 */
export interface LoginResponse {
  expiresIn: number;
  usuario: Usuario;
}
