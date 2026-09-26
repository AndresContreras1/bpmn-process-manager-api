import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { Arco, ArcoRequest, EditarArcoRequest } from '../models/diagrama.model';

/** Los flujos de secuencia. Un arco nunca cruza de un pool a otro: para eso estan los mensajes. */
@Injectable({ providedIn: 'root' })
export class ArcoService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly api: string = `${environment.apiUrl}/api/v1`;

  crear(solicitud: ArcoRequest): Observable<Arco> {
    return this.http.post<Arco>(`${this.api}/arcos`, solicitud);
  }

  editar(id: number, solicitud: EditarArcoRequest): Observable<Arco> {
    return this.http.put<Arco>(`${this.api}/arcos/${id}`, solicitud);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/arcos/${id}`);
  }
}
