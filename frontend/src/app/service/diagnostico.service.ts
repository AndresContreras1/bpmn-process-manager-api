import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { Diagnostico, TipoElemento } from '../models/diagnostico.model';

/**
 * El diagnostico del diagrama: los errores impiden publicar y las advertencias no. El mismo diagrama siempre
 * responde lo mismo, asi que la pantalla puede pedirlo cada vez que la API confirma un cambio.
 */
@Injectable({ providedIn: 'root' })
export class DiagnosticoService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly api: string = `${environment.apiUrl}/api/v1`;

  obtener(procesoId: number): Observable<Diagnostico> {
    return this.http.get<Diagnostico>(`${this.api}/procesos/${procesoId}/diagnostico`);
  }

  /** El diagrama que quedaria si se borrase ese elemento, para ensenarlo antes de confirmar el borrado. */
  sinElemento(procesoId: number, tipo: TipoElemento, id: number): Observable<Diagnostico> {
    return this.http.get<Diagnostico>(`${this.api}/procesos/${procesoId}/diagnostico`, {
      params: { sinElemento: `${tipo}:${id}` },
    });
  }
}
