import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { PageResponse } from '../models/page-response.model';
import { ActualizarUsuarioRequest, CrearUsuarioRequest, RolDeUsuario, Usuario } from '../models/usuario.model';

/** Filtros de la lista de usuarios; los vacios no se envian. */
export interface FiltrosUsuario {
  nombre: string;
  pagina: number;
  orden: string;
  direccion: 'asc' | 'desc';
}

/** Los usuarios de la tienda. Solo un administrador los administra; la API responde 403 a los demas. */
@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly url: string = `${environment.apiUrl}/api/v1/usuarios`;

  listar(filtros: FiltrosUsuario): Observable<PageResponse<Usuario>> {
    const params: Record<string, string | number> = {
      pagina: filtros.pagina,
      orden: `${filtros.orden},${filtros.direccion}`,
    };
    if (filtros.nombre.trim()) {
      params['nombre'] = filtros.nombre.trim();
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

  rolesDeProceso(id: number, ids: number[]): Observable<RolDeUsuario[]> {
    return this.http.put<RolDeUsuario[]>(`${this.url}/${id}/roles-proceso`, { rolesProcesoIds: ids });
  }
}
