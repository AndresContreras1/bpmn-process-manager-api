import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { EmpresaInvitada, ProcesoRecibido } from '../models/compartir.model';
import { PageResponse } from '../models/page-response.model';

/**
 * Compartir un proceso en solo lectura con otra tienda. La invitada lo ve por una sola puerta —el diagrama de la
 * version vigente— y no puede tocar nada.
 */
@Injectable({ providedIn: 'root' })
export class CompartirService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly url: string = `${environment.apiUrl}/api/v1/procesos`;

  /** Por NIT, que es como una tienda se identifica de cara a las demas. */
  compartir(procesoId: number, nit: string): Observable<EmpresaInvitada> {
    return this.http.post<EmpresaInvitada>(`${this.url}/${procesoId}/compartidos`, { nit });
  }

  invitadas(procesoId: number): Observable<EmpresaInvitada[]> {
    return this.http.get<EmpresaInvitada[]>(`${this.url}/${procesoId}/compartidos`);
  }

  dejarDeCompartir(procesoId: number, empresaInvitadaId: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${procesoId}/compartidos/${empresaInvitadaId}`);
  }

  /** Lo que otras tiendas comparten con la tuya. */
  recibidos(pagina: number): Observable<PageResponse<ProcesoRecibido>> {
    return this.http.get<PageResponse<ProcesoRecibido>>(`${this.url}/compartidos-conmigo`, { params: { pagina } });
  }
}
