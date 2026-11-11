import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { Carpeta } from '../models/carpeta.model';

/** El arbol de carpetas del repositorio de procesos. */
@Injectable({ providedIn: 'root' })
export class CarpetaService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly url: string = `${environment.apiUrl}/api/v1/carpetas`;

  listar(): Observable<Carpeta[]> {
    return this.http.get<Carpeta[]>(this.url);
  }

  crear(nombre: string, padreId: number | null): Observable<Carpeta> {
    return this.http.post<Carpeta>(this.url, { nombre, padreId });
  }

  editar(carpeta: Carpeta, nombre: string, padreId: number | null): Observable<Carpeta> {
    return this.http.put<Carpeta>(`${this.url}/${carpeta.id}`, { nombre, padreId, version: carpeta.version });
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
