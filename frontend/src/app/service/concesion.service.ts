import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../environments/environment';
import { Accion, Concesion, ConcesionRequest } from '../models/concesion.model';

/** De que es una concesion: una carpeta, que la heredan las suyas, o un proceso. */
export type TipoDeRecurso = 'carpetas' | 'procesos';

/**
 * PR 60: quien puede hacer que sobre una carpeta o un proceso. Listar, conceder y retirar piden ADMINISTRAR sobre el
 * recurso; lo que la persona puede hacer con un proceso lo pregunta cualquiera.
 */
@Injectable({ providedIn: 'root' })
export class ConcesionService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly url: string = `${environment.apiUrl}/api/v1`;

  /** Lo que quien pregunta puede hacer con el proceso, para ensenar solo los botones que van a funcionar. */
  permisos(procesoId: number): Observable<Accion[]> {
    return this.http
      .get<{ acciones: Accion[] }>(`${this.url}/procesos/${procesoId}/permisos`)
      .pipe(map((respuesta) => respuesta.acciones));
  }

  /** Las concesiones puestas en ese recurso, no las que hereda de sus carpetas. */
  listar(tipo: TipoDeRecurso, id: number): Observable<Concesion[]> {
    return this.http.get<Concesion[]>(`${this.url}/${tipo}/${id}/concesiones`);
  }

  conceder(tipo: TipoDeRecurso, id: number, solicitud: ConcesionRequest): Observable<Concesion> {
    return this.http.post<Concesion>(`${this.url}/${tipo}/${id}/concesiones`, solicitud);
  }

  retirar(concesionId: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/concesiones/${concesionId}`);
  }
}
