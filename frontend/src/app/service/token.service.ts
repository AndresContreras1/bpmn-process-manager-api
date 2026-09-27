import { Injectable } from '@angular/core';

import { LoginResponse } from '../models/login.model';
import { Usuario } from '../models/usuario.model';

const CLAVE_TOKEN = 'token';
const CLAVE_REFRESCO = 'token-refresco';
const CLAVE_USUARIO = 'usuario';
const CLAVE_VENCE = 'token-vence';

/**
 * Guarda la sesion en localStorage: el token JWT, el token de refresco que lo renueva, cuando vence el de acceso
 * y el usuario que devolvio la API.
 */
@Injectable({ providedIn: 'root' })
export class TokenService {
  /** El login, la renovacion y el cambio de clave devuelven lo mismo, asi que los tres guardan igual. */
  guardar(sesion: LoginResponse): void {
    localStorage.setItem(CLAVE_TOKEN, sesion.accessToken);
    localStorage.setItem(CLAVE_REFRESCO, sesion.refreshToken);
    localStorage.setItem(CLAVE_USUARIO, JSON.stringify(sesion.usuario));
    localStorage.setItem(CLAVE_VENCE, String(Date.now() + sesion.expiresIn * 1000));
  }

  obtenerToken(): string | null {
    return localStorage.getItem(CLAVE_TOKEN);
  }

  obtenerRefresco(): string | null {
    return localStorage.getItem(CLAVE_REFRESCO);
  }

  obtenerUsuario(): Usuario | null {
    const guardado: string | null = localStorage.getItem(CLAVE_USUARIO);
    return guardado ? (JSON.parse(guardado) as Usuario) : null;
  }

  /** El token existe y no ha vencido segun la hora que informo la API. */
  tokenVigente(): boolean {
    const vence: number = Number(localStorage.getItem(CLAVE_VENCE) ?? 0);
    return this.obtenerToken() !== null && Date.now() < vence;
  }

  /**
   * Hay sesion mientras quede algo que la sostenga. El token de acceso vive quince minutos, asi que sin esto una
   * pestana abierta un rato volveria al login aunque la sesion siga viva: el refresco es el que manda.
   */
  sesionAbierta(): boolean {
    return this.tokenVigente() || this.obtenerRefresco() !== null;
  }

  borrar(): void {
    localStorage.removeItem(CLAVE_TOKEN);
    localStorage.removeItem(CLAVE_REFRESCO);
    localStorage.removeItem(CLAVE_USUARIO);
    localStorage.removeItem(CLAVE_VENCE);
  }
}
