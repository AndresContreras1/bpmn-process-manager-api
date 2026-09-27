import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { EditarEventoRequest, Evento, EventoRequest } from '../models/diagrama.model';

/** Los eventos de un carril: donde empieza el proceso, donde termina y donde espera o manda un mensaje. */
@Injectable({ providedIn: 'root' })
export class EventoService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly api: string = `${environment.apiUrl}/api/v1`;

  crear(laneId: number, solicitud: EventoRequest): Observable<Evento> {
    return this.http.post<Evento>(`${this.api}/lanes/${laneId}/eventos`, solicitud);
  }

  editar(id: number, solicitud: EditarEventoRequest): Observable<Evento> {
    return this.http.put<Evento>(`${this.api}/eventos/${id}`, solicitud);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/eventos/${id}`);
  }
}
