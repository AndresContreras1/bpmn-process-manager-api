import { Injectable } from '@angular/core';

import { Usuario } from '../models/usuario.model';

const CLAVE_USUARIO = 'usuario';

/** Lo que guardaban las versiones anteriores, tokens incluidos: se borra la primera vez que esta arranca. */
const CLAVES_ANTIGUAS: readonly string[] = ['token', 'token-refresco', 'token-vence'];

/**
 * Lo que el navegador recuerda de la sesion (D29): quien entro, para pintar su nombre y lo que su rol permite. Los
 * tokens no: viajan en cookies HttpOnly que el JavaScript de la pagina no puede leer, asi que un XSS no se los lleva.
 */
@Injectable({ providedIn: 'root' })
export class SesionLocalService {
  constructor() {
    CLAVES_ANTIGUAS.forEach((clave: string) => localStorage.removeItem(clave));
  }

  /** El login, la renovacion y el cambio de clave devuelven el usuario, y los tres lo guardan igual. */
  guardar(usuario: Usuario): void {
    localStorage.setItem(CLAVE_USUARIO, JSON.stringify(usuario));
  }

  obtenerUsuario(): Usuario | null {
    const guardado: string | null = localStorage.getItem(CLAVE_USUARIO);
    return guardado ? (JSON.parse(guardado) as Usuario) : null;
  }

  /**
   * Hay sesion mientras el navegador recuerde quien entro. Si sus cookies ya no sirven, la API lo dice con un 401 y
   * el interceptor intenta renovarla antes de mandar al login.
   */
  hayUsuario(): boolean {
    return this.obtenerUsuario() !== null;
  }

  borrar(): void {
    localStorage.removeItem(CLAVE_USUARIO);
  }
}
