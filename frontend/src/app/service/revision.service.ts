import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { Revision } from '../models/diagnostico.model';

/**
 * La segunda opinion sobre el diagrama. Cada revision cuesta una llamada al modelo, asi que solo la piden
 * administradores y editores, la API la limita por tienda (429) y devuelve la que ya tenia si el diagrama no
 * cambio.
 */
@Injectable({ providedIn: 'root' })
export class RevisionService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly api: string = `${environment.apiUrl}/api/v1`;

  revisar(procesoId: number): Observable<Revision> {
    return this.http.post<Revision>(`${this.api}/procesos/${procesoId}/revision`, null);
  }
}
