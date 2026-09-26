import { Usuario } from './usuario.model';

export interface LoginRequest {
  email: string;
  password: string;
}

/** Respuesta del login y de la renovacion: los dos tokens, la vida del de acceso y el usuario que entro. */
export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  /** Token opaco que renueva la sesion una sola vez; la API lo cambia por otro en cada renovacion. */
  refreshToken: string;
  usuario: Usuario;
}
