import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import {
  EstadoMensajeSaliente,
  MensajeEntrante,
  MensajeEntranteRequest,
  MensajesDelCaso,
  MensajeSaliente,
  ResultadoCorrelacion,
} from '../models/mensajeria.model';
import { PageResponse } from '../models/page-response.model';

/** Los mensajes que van y vienen entre un proceso y los otros participantes. */
@Injectable({ providedIn: 'root' })
export class MensajeriaService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly api: string = `${environment.apiUrl}/api/v1`;

  /** Lo que un caso mando y lo que le llego: es lo que explica un caso que esta esperando. */
  delCaso(casoId: number): Observable<MensajesDelCaso> {
    return this.http.get<MensajesDelCaso>(`${this.api}/casos/${casoId}/mensajes`);
  }

  /** La bandeja de salida de un proceso. Un mensaje espera aqui hasta que el reloj llega a su tick de entrega. */
  salida(procesoId: number, estado: EstadoMensajeSaliente | '', pagina: number): Observable<PageResponse<MensajeSaliente>> {
    const params: Record<string, string | number> = { pagina };
    if (estado !== '') {
      params['estado'] = estado;
    }
    return this.http.get<PageResponse<MensajeSaliente>>(`${this.api}/procesos/${procesoId}/bandeja-salida`, {
      params,
    });
  }

  /** La bandeja de entrada: lo que ha llegado y lo que se hizo con cada uno. */
  entrada(procesoId: number, resultado: ResultadoCorrelacion | '', pagina: number): Observable<PageResponse<MensajeEntrante>> {
    const params: Record<string, string | number> = { pagina };
    if (resultado !== '') {
      params['resultado'] = resultado;
    }
    return this.http.get<PageResponse<MensajeEntrante>>(`${this.api}/procesos/${procesoId}/bandeja-entrada`, {
      params,
    });
  }

  /**
   * Manda al proceso el mensaje que estaba esperando, o el que abre un caso. Mandarlo dos veces con la misma
   * claveExterna responde la primera vez otra vez, marcado con Idempotent-Replayed.
   */
  recibir(procesoId: number, solicitud: MensajeEntranteRequest): Observable<MensajeEntrante> {
    return this.http.post<MensajeEntrante>(`${this.api}/procesos/${procesoId}/mensajes-entrantes`, solicitud);
  }
}
