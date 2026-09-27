import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { EditarPoolRequest, OrdenRequest, Pool, PoolRequest } from '../models/diagrama.model';

/** Los participantes de un proceso. El de la tienda lo crea la API con el proceso y no se borra. */
@Injectable({ providedIn: 'root' })
export class PoolService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly api: string = `${environment.apiUrl}/api/v1`;

  crear(procesoId: number, solicitud: PoolRequest): Observable<Pool> {
    return this.http.post<Pool>(`${this.api}/procesos/${procesoId}/pools`, solicitud);
  }

  editar(id: number, solicitud: EditarPoolRequest): Observable<Pool> {
    return this.http.put<Pool>(`${this.api}/pools/${id}`, solicitud);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/pools/${id}`);
  }

  /** Los ids en el orden que tienen que quedar, de arriba abajo. */
  ordenar(procesoId: number, orden: OrdenRequest): Observable<Pool[]> {
    return this.http.put<Pool[]>(`${this.api}/procesos/${procesoId}/pools/orden`, orden);
  }
}
