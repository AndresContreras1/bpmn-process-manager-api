import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { Correlacion, CorrelacionRequest } from '../models/diagrama.model';

/**
 * La clave con la que un mensaje que llega se relaciona con su caso. Es una por mensaje y se pone entera con un
 * PUT: no hay POST, porque no se crea aparte del mensaje al que pertenece.
 */
@Injectable({ providedIn: 'root' })
export class CorrelacionService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly api: string = `${environment.apiUrl}/api/v1`;

  definir(mensajeId: number, solicitud: CorrelacionRequest): Observable<Correlacion> {
    return this.http.put<Correlacion>(`${this.api}/mensajes/${mensajeId}/correlacion`, solicitud);
  }

  obtener(mensajeId: number): Observable<Correlacion> {
    return this.http.get<Correlacion>(`${this.api}/mensajes/${mensajeId}/correlacion`);
  }
}
