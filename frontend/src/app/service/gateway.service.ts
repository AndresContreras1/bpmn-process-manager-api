import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { EditarGatewayRequest, Gateway, GatewayRequest } from '../models/diagrama.model';

/** Los puntos de decision de un carril. */
@Injectable({ providedIn: 'root' })
export class GatewayService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly api: string = `${environment.apiUrl}/api/v1`;

  crear(laneId: number, solicitud: GatewayRequest): Observable<Gateway> {
    return this.http.post<Gateway>(`${this.api}/lanes/${laneId}/gateways`, solicitud);
  }

  editar(id: number, solicitud: EditarGatewayRequest): Observable<Gateway> {
    return this.http.put<Gateway>(`${this.api}/gateways/${id}`, solicitud);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/gateways/${id}`);
  }
}
