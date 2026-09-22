import { Usuario } from './usuario.model';

export interface LoginRequest {
  email: string;
  password: string;
}

/** Respuesta del login: el token JWT, sus segundos de vida y el usuario que entro. */
export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  usuario: Usuario;
}
