import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { EnlaceDeDescarga } from '../models/documento.model';
import { FormatoManual, Manual } from '../models/documentacion.model';

/** PR F20: el manual de una version publicada, que la cola arma como HTML y PDF en el idioma de quien lo pide. */
@Injectable({ providedIn: 'root' })
export class ManualService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly url: string = `${environment.apiUrl}/api/v1`;

  /** El de la version vigente, o el de la que se diga. Llega PENDIENTE: la cola lo arma despues. */
  pedir(procesoId: number, version?: number): Observable<Manual> {
    const params: Record<string, number> = version === undefined ? {} : { version };
    return this.http.post<Manual>(`${this.url}/procesos/${procesoId}/manuales`, null, { params });
  }

  /** Los manuales del proceso, el mas nuevo primero. */
  listar(procesoId: number): Observable<Manual[]> {
    return this.http.get<Manual[]>(`${this.url}/procesos/${procesoId}/manuales`);
  }

  obtener(id: number): Observable<Manual> {
    return this.http.get<Manual>(`${this.url}/manuales/${id}`);
  }

  /** Un enlace de cinco minutos al PDF o al HTML; 409 mientras se arma o si fallo. */
  enlace(id: number, formato: FormatoManual): Observable<EnlaceDeDescarga> {
    return this.http.get<EnlaceDeDescarga>(`${this.url}/manuales/${id}/enlace`, { params: { formato } });
  }
}
