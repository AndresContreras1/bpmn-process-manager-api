import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { Diagrama } from '../models/diagrama.model';

/** El diagrama BPMN de un proceso, con todos sus elementos en una sola peticion. */
@Injectable({ providedIn: 'root' })
export class DiagramaService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly url: string = `${environment.apiUrl}/api/v1/procesos`;

  obtener(procesoId: number): Observable<Diagrama> {
    return this.http.get<Diagrama>(`${this.url}/${procesoId}/diagrama`);
  }
}
