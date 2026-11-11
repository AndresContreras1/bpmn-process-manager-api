import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../environments/environment';
import { Comparacion, ModeloLeido, ModeloGuardado, Revision } from '../models/modelo.model';

const BPMN = 'application/bpmn+xml';

/** El modelo entero como archivo BPMN: leerlo, guardarlo, compararlo y volver a una revision. */
@Injectable({ providedIn: 'root' })
export class ModeloService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly api: string = `${environment.apiUrl}/api/v1/procesos`;

  /** El XML y su huella, que es el {@code If-Match} del primer guardado. */
  leer(procesoId: number): Observable<ModeloLeido> {
    return (
      this.http
        // para=dibujar: la web lo lee para mostrarlo, y eso solo pide poder verlo; descargarlo es exportar
        .get(`${this.api}/${procesoId}/modelo`, {
          observe: 'response',
          responseType: 'text',
          params: { para: 'dibujar' },
        })
        .pipe(map((respuesta) => ({ xml: respuesta.body ?? '', huella: etiqueta(respuesta.headers.get('ETag')) })))
    );
  }

  guardar(procesoId: number, xml: string, huella: string | null, clave: string): Observable<ModeloGuardado> {
    let cabeceras = new HttpHeaders({ 'Content-Type': BPMN, 'Idempotency-Key': clave });
    if (huella) {
      cabeceras = cabeceras.set('If-Match', `"${huella}"`);
    }
    return this.http.put<ModeloGuardado>(`${this.api}/${procesoId}/modelo`, xml, { headers: cabeceras });
  }

  /** Lo que este archivo le haria al borrador, sin guardarlo. */
  compararArchivo(procesoId: number, xml: string): Observable<Comparacion> {
    return this.http.post<Comparacion>(`${this.api}/${procesoId}/comparar`, xml, {
      headers: new HttpHeaders({ 'Content-Type': BPMN }),
    });
  }

  comparar(procesoId: number, de: string, a: string): Observable<Comparacion> {
    return this.http.get<Comparacion>(`${this.api}/${procesoId}/comparar`, { params: { de, a } });
  }

  revisiones(procesoId: number): Observable<Revision[]> {
    return this.http.get<Revision[]>(`${this.api}/${procesoId}/revisiones`);
  }

  puntoDeGuardado(procesoId: number): Observable<Revision> {
    return this.http.post<Revision>(`${this.api}/${procesoId}/revisiones`, null);
  }

  restaurar(procesoId: number, revisionId: number): Observable<ModeloGuardado> {
    return this.http.post<ModeloGuardado>(`${this.api}/${procesoId}/revisiones/${revisionId}/restaurar`, null);
  }

  cambiarModo(procesoId: number, modo: string, version: number): Observable<unknown> {
    return this.http.put(`${this.api}/${procesoId}/modo`, { modo, version });
  }
}

/** El valor de un ETag, sin comillas. */
export function etiqueta(valor: string | null): string | null {
  return valor ? valor.replace(/^W\//, '').replace(/^"|"$/g, '') : null;
}
