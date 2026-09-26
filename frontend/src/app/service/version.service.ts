import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { Diagrama } from '../models/diagrama.model';
import { Version } from '../models/version.model';

/** Las versiones publicadas de un proceso y el diagrama que quedo congelado en cada una. */
@Injectable({ providedIn: 'root' })
export class VersionService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly url: string = `${environment.apiUrl}/api/v1/procesos`;

  /** De la mas nueva a la mas vieja, como las devuelve la API. */
  listar(procesoId: number): Observable<Version[]> {
    return this.http.get<Version[]>(`${this.url}/${procesoId}/versiones`);
  }

  /** El diagrama tal como se publico, con la misma forma que el del borrador. */
  diagrama(procesoId: number, numero: number): Observable<Diagrama> {
    return this.http.get<Diagrama>(`${this.url}/${procesoId}/versiones/${numero}/diagrama`);
  }

  /**
   * Retira una version: deja de ser la vigente y el proceso vuelve a la anterior que siga en pie. No borra nada
   * -los casos que se abrieron con ella siguen corriendo con ella- y es lo unico que la API deja cambiarle a una
   * version publicada, asi que el cuerpo solo puede decir RETIRADA.
   */
  retirar(procesoId: number, numero: number): Observable<Version> {
    return this.http.patch<Version>(`${this.url}/${procesoId}/versiones/${numero}`, { estado: 'RETIRADA' });
  }
}
