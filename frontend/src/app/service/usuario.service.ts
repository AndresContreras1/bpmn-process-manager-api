import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

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
