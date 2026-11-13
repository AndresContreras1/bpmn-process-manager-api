import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { Documento, DocumentoDetalle, EnlaceDeDescarga } from '../models/documento.model';

/**
 * PR F20: los documentos de un proceso o de uno de sus elementos. Se suben como el archivo tal cual, sin formulario:
 * el cuerpo es el archivo y su nombre va en el parametro nombre. El tipo lo decide la API leyendo el contenido, asi que
 * se manda como octet-stream y no con el tipo que adivina el navegador.
 */
@Injectable({ providedIn: 'root' })
export class DocumentoService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly url: string = `${environment.apiUrl}/api/v1`;

  /** Los del proceso entero y los de sus elementos; con elemento, solo los de ese elemento. */
  listar(procesoId: number, elemento?: string | null): Observable<Documento[]> {
    const params: Record<string, string> = elemento ? { elemento } : {};
    return this.http.get<Documento[]>(`${this.url}/procesos/${procesoId}/documentos`, { params });
  }

  /** Lo crea PENDIENTE: el antivirus lo revisa en la cola antes de que alguien lo pueda bajar. */
  subir(procesoId: number, archivo: File, elemento?: string | null): Observable<Documento> {
    const params: Record<string, string> = { nombre: archivo.name };
    if (elemento) {
      params['elemento'] = elemento;
    }
    return this.http.post<Documento>(`${this.url}/procesos/${procesoId}/documentos`, archivo, {
      params,
      headers: { 'Content-Type': 'application/octet-stream' },
    });
  }

  /** El documento con todas sus versiones, la mas nueva primero. */
  obtener(id: number): Observable<DocumentoDetalle> {
    return this.http.get<DocumentoDetalle>(`${this.url}/documentos/${id}`);
  }

  /** Una version nueva del mismo documento; las anteriores siguen en su lista y se pueden bajar. */
  subirVersion(id: number, archivo: File): Observable<Documento> {
    return this.http.post<Documento>(`${this.url}/documentos/${id}/versiones`, archivo, {
      params: { nombre: archivo.name },
      headers: { 'Content-Type': 'application/octet-stream' },
    });
  }

  /** Un enlace de cinco minutos para la ultima version, o para la que se diga. 409 mientras se revisa o si se bloqueo. */
  enlace(id: number, version?: number): Observable<EnlaceDeDescarga> {
    const params: Record<string, number> = version === undefined ? {} : { version };
    return this.http.get<EnlaceDeDescarga>(`${this.url}/documentos/${id}/enlace`, { params });
  }

  /** El documento sale del proceso con todas sus versiones. */
  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/documentos/${id}`);
  }
}
