import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../environments/environment';
import {
  Caracterizacion,
  CaracterizacionRequest,
  Documentacion,
  DocumentacionRequest,
} from '../models/documentacion.model';

/**
 * PR F20: la documentacion en Markdown de un proceso y de sus elementos, y su ficha de caracterizacion. La web nunca
 * convierte el Markdown: muestra el HTML que la API convirtio y limpio con su lista blanca.
 */
@Injectable({ providedIn: 'root' })
export class DocumentacionService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly url: string = `${environment.apiUrl}/api/v1`;

  /** El texto del proceso (bpmnId vacio) y el de cada elemento que tenga uno. */
  listar(procesoId: number): Observable<Documentacion[]> {
    return this.http.get<Documentacion[]>(`${this.url}/procesos/${procesoId}/documentacion`);
  }

  /** Guarda el texto con la version leida, o sin version si es nuevo. Un texto vacio lo borra. */
  guardar(procesoId: number, documentacion: DocumentacionRequest): Observable<Documentacion> {
    return this.http.put<Documentacion>(`${this.url}/procesos/${procesoId}/documentacion`, documentacion);
  }

  /** El HTML que se guardaria, sin guardarlo. */
  vistaPrevia(markdown: string): Observable<string> {
    return this.http
      .post<{ html: string }>(`${this.url}/documentacion/vista-previa`, { markdown })
      .pipe(map((respuesta: { html: string }) => respuesta.html));
  }

  /** La ficha del proceso; mientras nadie la escribio, cada campo y la version vienen vacios. */
  caracterizacion(procesoId: number): Observable<Caracterizacion> {
    return this.http.get<Caracterizacion>(`${this.url}/procesos/${procesoId}/caracterizacion`);
  }

  /** La ficha entera de una vez, con la version leida; con una vieja la API responde 409. */
  guardarCaracterizacion(procesoId: number, ficha: CaracterizacionRequest): Observable<Caracterizacion> {
    return this.http.put<Caracterizacion>(`${this.url}/procesos/${procesoId}/caracterizacion`, ficha);
  }
}
