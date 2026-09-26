import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { Tablero } from '../models/tablero.model';

/** Como va la operacion. Los mismos numeros para un proceso o para la tienda entera. */
@Injectable({ providedIn: 'root' })
export class TableroService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly api: string = `${environment.apiUrl}/api/v1`;

  /** El tablero de un proceso, o el de la tienda entera cuando no se dice cual. */
  obtener(procesoId: number | null): Observable<Tablero> {
    return procesoId === null
      ? this.http.get<Tablero>(`${this.api}/empresas/actual/tablero`)
      : this.http.get<Tablero>(`${this.api}/procesos/${procesoId}/tablero`);
  }
}
