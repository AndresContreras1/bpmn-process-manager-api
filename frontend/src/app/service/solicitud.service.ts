import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { PageResponse } from '../models/page-response.model';
import {
  EstadoSolicitud,
  PoliticaDePublicacion,
  PoliticaDePublicacionRequest,
  Solicitud,
  TipoDeDecision,
} from '../models/solicitud.model';

/** La ruta de cada decision. */
const RUTA_DE_DECISION: Record<TipoDeDecision, string> = {
  APROBADA: 'aprobar',
  CAMBIOS_PEDIDOS: 'pedir-cambios',
  RECHAZADA: 'rechazar',
};

/**
 * PR F21: la revision antes de publicar (PR 64). Pedirla pide PUBLICAR sobre el proceso; decidir, APROBAR y ser uno
 * de sus aprobadores. La politica la lee cualquiera y la cambia el administrador.
 */
@Injectable({ providedIn: 'root' })
export class SolicitudService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly url: string = `${environment.apiUrl}/api/v1`;

  /** Congela el borrador y lo pone a revision; el texto, que explica el cambio, es opcional. */
  pedir(procesoId: number, markdown: string): Observable<Solicitud> {
    const texto = markdown.trim();
    return this.http.post<Solicitud>(`${this.url}/procesos/${procesoId}/solicitudes`, texto ? { markdown: texto } : {});
  }

  /** Las de un proceso, la mas nueva primero; sin la diferencia. */
  delProceso(procesoId: number, estado: EstadoSolicitud | null = null): Observable<PageResponse<Solicitud>> {
    const params: Record<string, string | number> = { pagina: 0, tamano: 20 };
    if (estado) {
      params['estado'] = estado;
    }
    return this.http.get<PageResponse<Solicitud>>(`${this.url}/procesos/${procesoId}/solicitudes`, { params });
  }

  /** Las de la tienda en procesos que la persona ve; con pendientesDeMi, las abiertas que esperan su decision. */
  listar(
    pendientesDeMi: boolean,
    estado: EstadoSolicitud | null,
    pagina: number,
    tamano: number = 20,
  ): Observable<PageResponse<Solicitud>> {
    const params: Record<string, string | number | boolean> = { pagina, tamano };
    if (pendientesDeMi) {
      params['pendientesDeMi'] = true;
    }
    if (estado) {
      params['estado'] = estado;
    }
    return this.http.get<PageResponse<Solicitud>>(`${this.url}/solicitudes`, { params });
  }

  /** Una, con sus decisiones y la diferencia contra la vigente de cuando se pidio. */
  obtener(id: number): Observable<Solicitud> {
    return this.http.get<Solicitud>(`${this.url}/solicitudes/${id}`);
  }

  /** Aprobar, pedir cambios o rechazar. Aprobar puede ir sin texto; los otros dos lo piden (la API responde 400). */
  decidir(id: number, decision: TipoDeDecision, markdown: string): Observable<Solicitud> {
    const texto = markdown.trim();
    return this.http.post<Solicitud>(
      `${this.url}/solicitudes/${id}/${RUTA_DE_DECISION[decision]}`,
      texto ? { markdown: texto } : {},
    );
  }

  retirar(id: number): Observable<Solicitud> {
    return this.http.post<Solicitud>(`${this.url}/solicitudes/${id}/retirar`, null);
  }

  politica(): Observable<PoliticaDePublicacion> {
    return this.http.get<PoliticaDePublicacion>(`${this.url}/empresas/actual/politica-de-publicacion`);
  }

  guardarPolitica(politica: PoliticaDePublicacionRequest): Observable<PoliticaDePublicacion> {
    return this.http.put<PoliticaDePublicacion>(`${this.url}/empresas/actual/politica-de-publicacion`, politica);
  }
}
