import { Injectable } from '@angular/core';

import { Usuario } from '../models/usuario.model';

const CLAVE_TOKEN = 'token';
const CLAVE_USUARIO = 'usuario';
const CLAVE_VENCE = 'token-vence';

/** Guarda la sesion en localStorage: el token JWT, cuando vence y el usuario que devolvio el login. */
@Injectable({ providedIn: 'root' })
export class TokenService {
  guardar(token: string, usuario: Usuario, segundosDeVida: number): void {
    localStorage.setItem(CLAVE_TOKEN, token);
    localStorage.setItem(CLAVE_USUARIO, JSON.stringify(usuario));
    localStorage.setItem(CLAVE_VENCE, String(Date.now() + segundosDeVida * 1000));
  }

  obtenerToken(): string | null {
    return localStorage.getItem(CLAVE_TOKEN);
  }

  obtenerUsuario(): Usuario | null {
    const guardado: string | null = localStorage.getItem(CLAVE_USUARIO);
    return guardado ? (JSON.parse(guardado) as Usuario) : null;
  }

  /** El token existe y no ha vencido segun la hora que informo el login. */
  tokenVigente(): boolean {
    const vence: number = Number(localStorage.getItem(CLAVE_VENCE) ?? 0);
    return this.obtenerToken() !== null && Date.now() < vence;
  }

  borrar(): void {
    localStorage.removeItem(CLAVE_TOKEN);
    localStorage.removeItem(CLAVE_USUARIO);
    localStorage.removeItem(CLAVE_VENCE);
  }
}
