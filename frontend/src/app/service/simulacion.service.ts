import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { PanelDeSimulacion, PedidosSimulados, PedidosSimuladosRequest } from '../models/simulacion.model';

/**
 * El reloj de la tienda y los socios simulados. Solo un administrador entra aqui: la API responde 403 a los demas,
 * porque mover el reloj de una tienda mueve todos sus casos a la vez.
 */
@Injectable({ providedIn: 'root' })
export class SimulacionService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly url: string = `${environment.apiUrl}/api/v1/simulacion`;

  /** Donde va la simulacion: el reloj, quien lo mueve y lo que sigue en las bandejas. */
  panel(): Observable<PanelDeSimulacion> {
    return this.http.get<PanelDeSimulacion>(this.url);
  }

  /**
   * Mueve el reloj y entrega lo que ya toca. Cada mensaje se entrega en su propia transaccion, asi que veinte
   * pedidos avanzan uno detras de otro y la respuesta es el panel de despues.
   */
  tick(ticks: number): Observable<PanelDeSimulacion> {
    return this.http.post<PanelDeSimulacion>(`${this.url}/tick`, { ticks });
  }

  /**
   * Le pide al cliente simulado un lote de pedidos. Entran por la misma puerta que cualquier otro mensaje, asi que
   * un pedido simulado y uno de verdad recorren exactamente el mismo camino.
   */
  pedidos(solicitud: PedidosSimuladosRequest): Observable<PedidosSimulados> {
    return this.http.post<PedidosSimulados>(`${this.url}/pedidos`, solicitud);
  }
}
