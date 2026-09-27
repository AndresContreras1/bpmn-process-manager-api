import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { PageResponse } from '../models/page-response.model';
import {
  EditarProcesoRequest,
  FiltrosProceso,
  Proceso,
  ProcesoDetalle,
  ProcesoRequest,
} from '../models/proceso.model';

/** Procesos de la tienda del usuario: la API solo devuelve los de su empresa. */
@Injectable({ providedIn: 'root' })
export class ProcesoService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly url: string = `${environment.apiUrl}/api/v1/procesos`;

  listar(filtros: FiltrosProceso): Observable<PageResponse<Proceso>> {
    // Con el objeto params Angular codifica los valores; los filtros vacios no se envian
    const params: Record<string, string | number> = {
      pagina: filtros.pagina,
      orden: `${filtros.orden},${filtros.direccion}`,
    };
    if (filtros.nombre.trim()) {
      params['nombre'] = filtros.nombre.trim();
    }
    if (filtros.estado) {
      params['estado'] = filtros.estado;
    }
    if (filtros.categoria) {
      params['categoria'] = filtros.categoria;
    }
    return this.http.get<PageResponse<Proceso>>(this.url, { params });
  }

  obtener(id: number): Observable<ProcesoDetalle> {
    return this.http.get<ProcesoDetalle>(`${this.url}/${id}`);
  }

  crear(solicitud: ProcesoRequest): Observable<Proceso> {
    return this.http.post<Proceso>(this.url, solicitud);
  }

  /** La version va en el cuerpo: es la del ultimo GET, y si alguien guardo desde entonces la API responde 409. */
  editar(id: number, solicitud: EditarProcesoRequest): Observable<Proceso> {
    return this.http.put<Proceso>(`${this.url}/${id}`, solicitud);
  }

  /** Publicar es un cambio de estado, y como todo cambio se hace sobre una version conocida. */
  publicar(id: number, version: number): Observable<Proceso> {
    return this.http.patch<Proceso>(`${this.url}/${id}`, { estado: 'PUBLICADO', version });
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
