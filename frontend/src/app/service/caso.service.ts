import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import {
  AbrirCasoRequest,
  Caso,
  CasoDetalle,
  EventoCaso,
  FiltrosCaso,
  VariablesRequest,
} from '../models/caso.model';
import { PageResponse } from '../models/page-response.model';

/** Los casos de la tienda: cada uno es una ejecucion de una version publicada, es decir un pedido. */
@Injectable({ providedIn: 'root' })
export class CasoService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly api: string = `${environment.apiUrl}/api/v1`;

  /** Los mas nuevos primero. Los filtros vacios no se envian. */
  listar(filtros: FiltrosCaso): Observable<PageResponse<Caso>> {
    const params: Record<string, string | number> = {
      pagina: filtros.pagina,
      orden: `${filtros.orden},${filtros.direccion}`,
    };
    if (filtros.procesoId !== '') {
      params['procesoId'] = filtros.procesoId;
    }
    if (filtros.estado !== '') {
      params['estado'] = filtros.estado;
    }
    if (filtros.referencia.trim()) {
      params['referencia'] = filtros.referencia.trim();
    }
    return this.http.get<PageResponse<Caso>>(`${this.api}/casos`, { params });
  }

  /** El caso con sus pasos y sus variables. */
  obtener(id: number): Observable<CasoDetalle> {
    return this.http.get<CasoDetalle>(`${this.api}/casos/${id}`);
  }

  /** La linea de tiempo: cada decision, cada tarea y cada error, en el orden en que pasaron. */
  eventos(id: number): Observable<EventoCaso[]> {
    return this.http.get<EventoCaso[]>(`${this.api}/casos/${id}/eventos`);
  }

  /**
   * Abre un caso sobre la version vigente y lo corre hasta que se detiene. El proceso tiene que empezar con un
   * evento de inicio normal: uno que empieza con un mensaje se abre mandando ese mensaje.
   */
  abrir(procesoId: number, solicitud: AbrirCasoRequest): Observable<Caso> {
    return this.http.post<Caso>(`${this.api}/procesos/${procesoId}/casos`, solicitud);
  }

  /** Lo cierra antes de tiempo: sus tokens vivos se apagan y deja de moverse. No se borra nada. */
  cancelar(id: number): Observable<Caso> {
    return this.http.post<Caso>(`${this.api}/casos/${id}/cancelar`, null);
  }

  /** Reemplaza las variables enteras, sobre la version que se leyo. */
  variables(id: number, solicitud: VariablesRequest): Observable<Caso> {
    return this.http.patch<Caso>(`${this.api}/casos/${id}/variables`, solicitud);
  }

  /** Vuelve a evaluar lo que lo dejo sin camino, con las variables que tiene ahora. */
  reintentar(id: number): Observable<Caso> {
    return this.http.post<Caso>(`${this.api}/casos/${id}/reintentar`, null);
  }
}
