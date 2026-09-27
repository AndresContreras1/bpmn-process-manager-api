import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { PageResponse } from '../models/page-response.model';
import { AsignarTareaRequest, CompletarTareaRequest, FiltrosTarea, Tarea } from '../models/tarea.model';

/** La bandeja: lo que los casos abiertos estan esperando que haga alguien. */
@Injectable({ providedIn: 'root' })
export class TareaService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly url: string = `${environment.apiUrl}/api/v1/tareas`;

  /** Las mas viejas primero, que es el orden en el que hay que atenderlas. */
  bandeja(filtros: FiltrosTarea): Observable<PageResponse<Tarea>> {
    const params: Record<string, string | number | boolean> = { mias: filtros.mias, pagina: filtros.pagina };
    if (filtros.rolProcesoId !== '') {
      params['rolProcesoId'] = filtros.rolProcesoId;
    }
    if (filtros.procesoId !== '') {
      params['procesoId'] = filtros.procesoId;
    }
    if (filtros.estado !== '') {
      params['estado'] = filtros.estado;
    }
    return this.http.get<PageResponse<Tarea>>(this.url, { params });
  }

  obtener(id: number): Observable<Tarea> {
    return this.http.get<Tarea>(`${this.url}/${id}`);
  }

  /**
   * La completa y deja que el caso siga. Lo que se entregue aterriza en las variables del caso bajo
   * tarea.<nombreDeLaTareaEnCamel>, asi que un gateway de mas adelante puede preguntar por ello.
   */
  completar(id: number, solicitud: CompletarTareaRequest): Observable<Tarea> {
    return this.http.post<Tarea>(`${this.url}/${id}/completar`, solicitud);
  }

  /** Tomarla es una nota para el equipo: el rol entero la sigue viendo y completarla no exige haberla tomado. */
  asignar(id: number, solicitud: AsignarTareaRequest): Observable<Tarea> {
    return this.http.post<Tarea>(`${this.url}/${id}/asignar`, solicitud);
  }
}
