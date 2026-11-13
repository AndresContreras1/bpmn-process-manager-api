import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { EMPTY, Observable, expand, reduce } from 'rxjs';

import { environment } from '../../environments/environment';
import { PageResponse } from '../models/page-response.model';
import { ActualizarUsuarioRequest, CrearUsuarioRequest, RolDeUsuario, Usuario } from '../models/usuario.model';

/**
 * Como se pide una pagina de usuarios: una parte del nombre, y si se quiere ver tambien a quien esta desactivado.
 * Los dos filtros son los que la API acepta desde que se le anadieron; la pantalla no ofrece nada mas.
 */
export interface FiltrosUsuario {
  pagina: number;
  orden: string;
  direccion: 'asc' | 'desc';
  nombre: string;
  incluirInactivos: boolean;
}

/** El maximo de usuarios por pagina que acepta la API. */
const USUARIOS_POR_PAGINA = 50;

/** Los usuarios de la tienda. Solo un administrador los administra; la API responde 403 a los demas. */
@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly url: string = `${environment.apiUrl}/api/v1/usuarios`;

  /**
   * Por defecto la API devuelve solo los activos; con incluirInactivos salen tambien los que ya no pueden entrar,
   * que es de donde se los reactiva. El nombre vacio no se manda: un filtro en blanco no es un filtro.
   */
  listar(filtros: FiltrosUsuario): Observable<PageResponse<Usuario>> {
    const params: Record<string, string | number | boolean> = {
      pagina: filtros.pagina,
      orden: `${filtros.orden},${filtros.direccion}`,
    };
    if (filtros.nombre.trim() !== '') {
      params['nombre'] = filtros.nombre.trim();
    }
    if (filtros.incluirInactivos) {
      params['incluirInactivos'] = true;
    }
    return this.http.get<PageResponse<Usuario>>(this.url, { params });
  }

  /**
   * Los activos, por nombre, para los desplegables que eligen a alguien: el dueno de un proceso y a quien se le concede
   * algo. Se leen todas las paginas, de cincuenta en cincuenta, que es el maximo que la API acepta: con una sola, la
   * persona numero cincuenta y uno no se podria elegir y sus concesiones saldrian sin nombre.
   */
  paraElegir(): Observable<Usuario[]> {
    const leer = (pagina: number): Observable<PageResponse<Usuario>> =>
      this.http.get<PageResponse<Usuario>>(this.url, {
        params: { pagina, tamano: USUARIOS_POR_PAGINA, orden: 'nombre,asc' },
      });
    return leer(0).pipe(
      expand((leida: PageResponse<Usuario>) => (leida.page + 1 < leida.totalPages ? leer(leida.page + 1) : EMPTY)),
      reduce((todos: Usuario[], leida: PageResponse<Usuario>) => [...todos, ...leida.content], []),
    );
  }

  /** La respuesta trae la clave temporal cuando la API la invento: es la unica vez que se puede leer. */
  crear(solicitud: CrearUsuarioRequest): Observable<Usuario> {
    return this.http.post<Usuario>(this.url, solicitud);
  }

  actualizar(id: number, solicitud: ActualizarUsuarioRequest): Observable<Usuario> {
    return this.http.patch<Usuario>(`${this.url}/${id}`, solicitud);
  }

  /** Devuelve otra clave temporal, tambien una sola vez. */
  restablecerClave(id: number): Observable<Usuario> {
    return this.http.post<Usuario>(`${this.url}/${id}/restablecer-clave`, null);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  /** Los roles de proceso que la persona ya atiende: es lo que el panel tiene que ensenar marcado. */
  rolesDeProcesoDe(id: number): Observable<RolDeUsuario[]> {
    return this.http.get<RolDeUsuario[]>(`${this.url}/${id}/roles-proceso`);
  }

  /** Reemplaza la lista entera: lo que se manda es lo que la persona queda teniendo. */
  rolesDeProceso(id: number, ids: number[]): Observable<RolDeUsuario[]> {
    return this.http.put<RolDeUsuario[]>(`${this.url}/${id}/roles-proceso`, { rolesProcesoIds: ids });
  }
}
