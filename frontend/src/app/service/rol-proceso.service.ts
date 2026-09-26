import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../environments/environment';
import { PageResponse } from '../models/page-response.model';
import { EditarRolProcesoRequest, RolProceso, RolProcesoRequest } from '../models/rol-proceso.model';

/** Los roles de proceso de la tienda. Una lane va ligada a uno, y es el que atiende lo que hay dentro. */
@Injectable({ providedIn: 'root' })
export class RolProcesoService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly url: string = `${environment.apiUrl}/api/v1/roles`;

  /**
   * Todos los roles en una sola pagina. Son pocos por tienda —uno por cada trabajo que hace— y el editor los
   * necesita enteros para poder elegir cualquiera en el formulario de una lane.
   */
  listar(): Observable<RolProceso[]> {
    return this.http
      .get<PageResponse<RolProceso>>(this.url, { params: { pagina: 0, tamano: 200, orden: 'nombre,asc' } })
      .pipe(map((pagina: PageResponse<RolProceso>) => pagina.content));
  }

  /** La pagina tal como la devuelve la API, para la pantalla que los administra. */
  pagina(pagina: number, nombre: string): Observable<PageResponse<RolProceso>> {
    const params: Record<string, string | number> = { pagina, orden: 'nombre,asc' };
    if (nombre.trim()) {
      params['nombre'] = nombre.trim();
    }
    return this.http.get<PageResponse<RolProceso>>(this.url, { params });
  }

  crear(solicitud: RolProcesoRequest): Observable<RolProceso> {
    return this.http.post<RolProceso>(this.url, solicitud);
  }

  editar(id: number, solicitud: EditarRolProcesoRequest): Observable<RolProceso> {
    return this.http.put<RolProceso>(`${this.url}/${id}`, solicitud);
  }

  /** Un rol en uso por alguna lane no se puede eliminar: la API responde 409. */
  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
