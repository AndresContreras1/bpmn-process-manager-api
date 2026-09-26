import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { EditarLaneRequest, Lane, LaneRequest, OrdenRequest } from '../models/diagrama.model';

/** Los carriles de un pool. Cada uno va ligado a un rol de proceso, que es quien atiende lo que hay dentro. */
@Injectable({ providedIn: 'root' })
export class LaneService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly api: string = `${environment.apiUrl}/api/v1`;

  crear(poolId: number, solicitud: LaneRequest): Observable<Lane> {
    return this.http.post<Lane>(`${this.api}/pools/${poolId}/lanes`, solicitud);
  }

  editar(id: number, solicitud: EditarLaneRequest): Observable<Lane> {
    return this.http.put<Lane>(`${this.api}/lanes/${id}`, solicitud);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/lanes/${id}`);
  }

  ordenar(poolId: number, orden: OrdenRequest): Observable<Lane[]> {
    return this.http.put<Lane[]>(`${this.api}/pools/${poolId}/lanes/orden`, orden);
  }
}
