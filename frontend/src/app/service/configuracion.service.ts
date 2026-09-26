import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { ConfiguracionTienda, ConfiguracionTiendaRequest } from '../models/configuracion.model';
import { Empresa } from '../models/empresa.model';
import { HistorialCambio } from '../models/proceso.model';
import { PageResponse } from '../models/page-response.model';

/** La tienda de quien entro: sus datos, su configuracion y todo lo que ha pasado en ella. */
@Injectable({ providedIn: 'root' })
export class ConfiguracionService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly url: string = `${environment.apiUrl}/api/v1/empresas/actual`;

  empresa(): Observable<Empresa> {
    return this.http.get<Empresa>(this.url);
  }

  obtener(): Observable<ConfiguracionTienda> {
    return this.http.get<ConfiguracionTienda>(`${this.url}/configuracion`);
  }

  guardar(solicitud: ConfiguracionTiendaRequest): Observable<ConfiguracionTienda> {
    return this.http.put<ConfiguracionTienda>(`${this.url}/configuracion`, solicitud);
  }

  /** El historial de la tienda entera, no el de un proceso: de lo mas nuevo a lo mas viejo. */
  historial(pagina: number): Observable<PageResponse<HistorialCambio>> {
    return this.http.get<PageResponse<HistorialCambio>>(`${this.url}/historial`, { params: { pagina } });
  }
}
