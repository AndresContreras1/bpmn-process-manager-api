import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { MensajesDelCaso } from '../models/mensajeria.model';

/** Los mensajes que van y vienen entre un proceso y los otros participantes. */
@Injectable({ providedIn: 'root' })
export class MensajeriaService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly api: string = `${environment.apiUrl}/api/v1`;

  /** Lo que un caso mando y lo que le llego: es lo que explica un caso que esta esperando. */
  delCaso(casoId: number): Observable<MensajesDelCaso> {
    return this.http.get<MensajesDelCaso>(`${this.api}/casos/${casoId}/mensajes`);
  }
}
