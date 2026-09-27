import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { Actividad, ActividadRequest, EditarActividadRequest } from '../models/diagrama.model';

/** Las tareas de un carril. Editarlas puede moverlas de carril, y con eso cambia el rol que las atiende. */
@Injectable({ providedIn: 'root' })
export class ActividadService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly api: string = `${environment.apiUrl}/api/v1`;

  crear(laneId: number, solicitud: ActividadRequest): Observable<Actividad> {
    return this.http.post<Actividad>(`${this.api}/lanes/${laneId}/actividades`, solicitud);
  }

  editar(id: number, solicitud: EditarActividadRequest): Observable<Actividad> {
    return this.http.put<Actividad>(`${this.api}/actividades/${id}`, solicitud);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/actividades/${id}`);
  }
}
