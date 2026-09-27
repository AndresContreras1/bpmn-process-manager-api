import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { EditarMensajeRequest, Mensaje, MensajeRequest } from '../models/diagrama.model';

/** Los flujos de mensaje entre dos participantes. */
@Injectable({ providedIn: 'root' })
export class MensajeService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly api: string = `${environment.apiUrl}/api/v1`;

  crear(procesoId: number, solicitud: MensajeRequest): Observable<Mensaje> {
    return this.http.post<Mensaje>(`${this.api}/procesos/${procesoId}/mensajes`, solicitud);
  }

  editar(id: number, solicitud: EditarMensajeRequest): Observable<Mensaje> {
    return this.http.put<Mensaje>(`${this.api}/mensajes/${id}`, solicitud);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/mensajes/${id}`);
  }
}
